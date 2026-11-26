package com.ngng.api.user.signup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngng.api.user.user.dto.request.*;
import com.ngng.api.user.user.dto.response.EmailAuthResponse;
import com.ngng.api.user.user.entity.*;
import com.ngng.api.user.user.repository.*;
import com.ngng.api.user.user.service.*;
import com.ngng.api.user.user.service.signup.*;
import com.ngng.api.user.point.service.PointHistoryService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SignupVerificationTest {
    static final String KEY = Base64.getEncoder().encodeToString("rf043-synthetic-only-32-byte-key-1234567890".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    MemorySignupStore store;
    SignupCrypto crypto;
    SignupVerification verification;
    SignupVerification.Attempt attempt;
    UserRepository users;
    UserRoleRepository roles;
    PointHistoryService points;
    PasswordEncoder passwords;
    JoinService joins;
    @BeforeEach void setup() {
        store=new MemorySignupStore();crypto=new SignupCrypto(true,"test","rf043_epoch_00001",KEY);verification=new SignupVerification(crypto,store);
        attempt=verification.create();users=mock(UserRepository.class);roles=mock(UserRoleRepository.class);points=mock(PointHistoryService.class);passwords=mock(PasswordEncoder.class);
        joins=new JoinService(users,roles,passwords,points,verification);
        when(roles.findByRoleType("UNCONFIRMED_USER")).thenReturn(Role.builder().roleType("UNCONFIRMED_USER").build());
        when(passwords.encode("synthetic-password")).thenReturn("encoded");
        when(users.save(any())).thenAnswer(call->{User u=call.getArgument(0);u.setUserId(43L);return u;});
    }
    SignupVerification.Reservation sent(String channel) {
        var r=verification.reserve(attempt.attemptId(),attempt.attemptSecret(),channel,channel.equals("email")?"Local@example.test":"01112345678");
        verification.complete(r,true);return r;
    }
    String proof(String channel) {var r=sent(channel);return verification.verify(attempt.attemptId(),attempt.attemptSecret(),channel,r.challengeId(),r.code()).proof();}
    JoinRequest request(String emailProof,String phoneProof) {return new JoinRequest("Synthetic"," Local@EXAMPLE.test ","synthetic-password","fixture","011-1234-5678",attempt.attemptId(),attempt.attemptSecret(),emailProof,phoneProof);}
    SignupFailure failure(int expected, Runnable action) {var error=assertThrows(SignupFailure.class,action::run);assertEquals(expected,error.status());return error;}

    @Test void asciiCanonicalTargetsRejectAmbiguousRecipientsAndPermitExistingPhonePrefixesAndPunycode() {
        assertEquals("Local@xn--bcher-kva.example",SignupTarget.email(" Local@XN--BCHER-KVA.example "));
        for(String value:List.of("a,b@example.test","Name <a@example.test>","a(comment)@example.test","\"a\"@example.test","a@example.test\r\nBcc:b@example.test","\ta@example.test","a..b@example.test","a@-bad.test","a@example..test","a@example.test.","a@127.0.0.1","a@é.test","a@@example.test")) failure(400,()->SignupTarget.email(value));
        for(String value:List.of("0101234567","011-1234-5678","019-123-4567"," 01212345678 ")) assertTrue(SignupTarget.phone(value).matches("[0-9]{10,11}"));
        for(String value:List.of("+821012345678","０１０１２３４５６７８","010 1234 5678","010-12-345678","010123456","010123456789","010--1234-5678")) failure(400,()->SignupTarget.phone(value));
    }
    @Test void secretsAndHmacAreFullLengthDomainSeparatedAndLengthUnambiguous() {
        assertEquals(43,crypto.token().length());assertTrue(crypto.code().matches("[0-9]{6}"));
        String digest=crypto.hmac("signup-target-quota","email","Local@example.test");assertEquals(64,digest.length());
        assertNotEquals(digest,crypto.hmac("signup-target-binding","email","Local@example.test"));
        assertNotEquals(crypto.hmac("x","a:b","c"),crypto.hmac("x","a","b:c"));
        assertFalse(crypto.key("quota","email",digest).contains("Local"));
        assertNotEquals(crypto.digest("signup-proof-email",attempt.attemptSecret()),crypto.digest("signup-attempt-secret",attempt.attemptSecret()));
    }
    @Test void missingOrInvalidConfigurationAndDisabledFlagBlockEveryEntryWithoutStoreOrPersistence() {
        for(SignupCrypto invalid:List.of(new SignupCrypto(false,"test","rf043_epoch_00001",KEY),new SignupCrypto(true,"evil:{signup}","rf043_epoch_00001",KEY),new SignupCrypto(true,"test","",KEY),new SignupCrypto(true,"test","rf043_epoch_00001","short"))) {
            var state=mock(SignupStore.class);var v=new SignupVerification(invalid,state);var j=new JoinService(users,roles,passwords,points,v);
            failure(503,v::create);failure(503,()->v.reserve(attempt.attemptId(),attempt.attemptSecret(),"email","Local@example.test"));
            failure(503,()->v.verify(attempt.attemptId(),attempt.attemptSecret(),"email",crypto.token(),"000000"));failure(503,()->j.join(request(crypto.token(),crypto.token())));
            verifyNoInteractions(state,users,roles,points,passwords);
        }
    }
    @Test void wrongSecretNeverChangesQuotaGenerationProofOrChecks() {
        var r=sent("email");String before=store.snapshot();String wrong=crypto.token();
        failure(400,()->verification.reserve(attempt.attemptId(),wrong,"email","Local@example.test"));
        failure(400,()->verification.verify(attempt.attemptId(),wrong,"email",r.challengeId(),r.code()));
        failure(400,()->verification.consume(new JoinRequest("s","e","p","n","ph",attempt.attemptId(),wrong,crypto.token(),crypto.token()),"Local@example.test","01112345678"));
        assertEquals(before,store.snapshot());
    }
    @Test void codesExpireAtExactlyFiveMinutesAndPendingNeverVerifies() {
        var pending=verification.reserve(attempt.attemptId(),attempt.attemptSecret(),"email","Local@example.test");
        failure(400,()->verification.verify(attempt.attemptId(),attempt.attemptSecret(),"email",pending.challengeId(),pending.code()));
        assertEquals("0",store.attempt().get("email:checks"));verification.complete(pending,true);store.now+=299_999;
        assertNotNull(verification.verify(attempt.attemptId(),attempt.attemptSecret(),"email",pending.challengeId(),pending.code()).proof());
        var r=sent("phone");store.now+=300_000;
        failure(400,()->verification.verify(attempt.attemptId(),attempt.attemptSecret(),"phone",r.challengeId(),r.code()));
    }
    @Test void fifthComparisonCanSucceedAndCountersSurviveResend() {
        var r=sent("email");String wrong=r.code().equals("000000")?"000001":"000000";
        for(int i=0;i<4;i++)failure(400,()->verification.verify(attempt.attemptId(),attempt.attemptSecret(),"email",r.challengeId(),wrong));
        var newer=sent("email");assertNotNull(verification.verify(attempt.attemptId(),attempt.attemptSecret(),"email",newer.challengeId(),newer.code()));
        failure(429,()->sent("email"));assertNotNull(proof("phone"));
    }
    @Test void fiveFailuresLockChannelWithoutSixthComparisonOrResendReset() {
        var r=sent("email");String wrong=r.code().equals("000000")?"000001":"000000";
        for(int i=0;i<4;i++)failure(400,()->verification.verify(attempt.attemptId(),attempt.attemptSecret(),"email",r.challengeId(),wrong));
        failure(429,()->verification.verify(attempt.attemptId(),attempt.attemptSecret(),"email",r.challengeId(),wrong));String before=store.snapshot();
        failure(429,()->verification.verify(attempt.attemptId(),attempt.attemptSecret(),"email",r.challengeId(),r.code()));failure(429,()->sent("email"));assertEquals(before,store.snapshot());
    }
    @Test void rollingQuotaIncludesFailedReservationAndNewAttemptsAndExactBoundary() {
        var first=sent("email");var second=verification.reserve(attempt.attemptId(),attempt.attemptSecret(),"email","Local@example.test");failure(503,()->verification.complete(second,false));
        attempt=verification.create();sent("email");SignupFailure limit=failure(429,()->sent("email"));assertEquals(600,limit.retryAfter());
        store.now+=599_999;failure(429,()->sent("email"));store.now++;assertNotNull(sent("email"));
    }
    @Test void failedQuotaReservationPreservesPriorProofAndCollisionNeverOverwrites() {
        String p=proof("email");sent("email");String p2=proof("email");String before=store.snapshot();failure(429,()->sent("email"));assertEquals(before,store.snapshot());
        store.collisions=3;failure(503,verification::create);assertEquals(before,store.snapshot());assertNotEquals(p,p2);
    }
    @Test void verificationSuccessCannotBeReplayedToRecoverOrMintAProof() {
        var r=sent("email");var p=verification.verify(attempt.attemptId(),attempt.attemptSecret(),"email",r.challengeId(),r.code());String before=store.snapshot();
        failure(409,()->verification.verify(attempt.attemptId(),attempt.attemptSecret(),"email",r.challengeId(),r.code()));assertEquals(before,store.snapshot());
        assertFalse(store.attempt().containsValue(p.proof()));assertFalse(store.attempt().containsValue(r.code()));
    }
    @Test void joinRequiresBothExactTargetsAndNoPartialConsumptionBeforeDatabaseAccess() {
        String e=proof("email"),p=proof("phone");String before=store.snapshot();failure(400,()->joins.join(request(e,crypto.token())));assertEquals(before,store.snapshot());verifyNoInteractions(users,roles,passwords,points);
        var wrong=request(e,p);wrong=new JoinRequest(wrong.name(),"Other@example.test",wrong.password(),wrong.nickname(),wrong.phoneNumber(),wrong.attemptId(),wrong.attemptSecret(),e,p);JoinRequest finalWrong=wrong;
        failure(400,()->joins.join(finalWrong));assertEquals(before,store.snapshot());assertTrue(joins.join(request(e,p)).isJoinSuccess());
        verify(users).save(argThat(u->u.getEmail().equals("Local@example.test")&&u.getPhoneNumber().equals("01112345678")&&u.getRole().getRoleType().equals("UNCONFIRMED_USER")&&u.getPassword().equals("encoded")));
        verify(points).createInitByUser(any());failure(409,()->joins.join(request(e,p)));verify(users,times(1)).save(any());
    }
    @Test void consumedAttemptIsTerminalForResendVerifyAndLateCompletion() {
        String e=proof("email"),p=proof("phone");joins.join(request(e,p));String before=store.snapshot();
        failure(409,()->sent("email"));failure(409,()->verification.verify(attempt.attemptId(),attempt.attemptSecret(),"phone",crypto.token(),"000000"));
        failure(409,()->verification.complete(new SignupVerification.Reservation(attempt.attemptId(),crypto.digest("signup-attempt-secret",attempt.attemptSecret()),"email",crypto.token(),"000000",store.now+1),true));assertEquals(before,store.snapshot());
    }
    @Test void resendBeforeJoinInvalidatesOnlyCurrentChannelAndLateSenderCannotActivateOldGeneration() {
        String e=proof("email"),p=proof("phone");var old=verification.reserve(attempt.attemptId(),attempt.attemptSecret(),"email","Local@example.test");var newer=sent("email");
        failure(409,()->verification.complete(old,true));failure(400,()->joins.join(request(e,p)));verifyNoInteractions(users,points);
        String next=verification.verify(attempt.attemptId(),attempt.attemptSecret(),"email",newer.challengeId(),newer.code()).proof();assertTrue(joins.join(request(next,p)).isJoinSuccess());
    }
    @Test void proofAndAttemptAbsoluteExpiriesAreEnforcedEvenWhenKeyCleanupIsDelayed() {
        String e=proof("email"),p=proof("phone");store.now+=300_000;failure(400,()->joins.join(request(e,p)));verifyNoInteractions(users,points);
        store.now+=1_500_000;failure(400,()->sent("email"));
    }
    @Test void consumeTimeoutNeverStartsDatabaseAndNeverRestoresConsumedProofs() {
        String e=proof("email"),p=proof("phone");store.consumeTimeout=true;failure(503,()->joins.join(request(e,p)));verifyNoInteractions(users,roles,passwords,points);assertEquals("consumed",store.attempt().get("state"));
    }
    @Test void databaseFailureAndDuplicateRetainConsumedStateAndForbidRetry() {
        String e=proof("email"),p=proof("phone");doThrow(new IllegalStateException("synthetic-db-failure")).when(users).save(any());
        assertThrows(IllegalStateException.class,()->joins.join(request(e,p)));failure(409,()->joins.join(request(e,p)));verifyNoInteractions(points);
        store.clear();attempt=verification.create();String e2=proof("email"),p2=proof("phone");when(users.existsByPhoneNumber("01112345678")).thenReturn(true);
        assertFalse(joins.join(request(e2,p2)).isJoinSuccess());assertEquals("consumed",store.attempt().get("state"));failure(409,()->joins.join(request(e2,p2)));
    }
    @Test void changedEpochRejectsPreviousAttemptAndMissingRedisFailsClosed() {
        var newer=new SignupVerification(new SignupCrypto(true,"test","rf043_epoch_00002",KEY),store);failure(400,()->newer.reserve(attempt.attemptId(),attempt.attemptSecret(),"email","Local@example.test"));
        store.unavailable=true;failure(503,()->joins.join(request(crypto.token(),crypto.token())));verifyNoInteractions(users,roles,passwords,points);
    }
    @Test void concurrentModelVerificationAndJoinHaveOneWinnerWithoutClaimingRedisAtomicity() throws Exception {
        var r=sent("email");ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var tasks=List.<Callable<String>>of(()->verification.verify(attempt.attemptId(),attempt.attemptSecret(),"email",r.challengeId(),r.code()).proof(),()->verification.verify(attempt.attemptId(),attempt.attemptSecret(),"email",r.challengeId(),r.code()).proof());
            var futures=pool.invokeAll(tasks);int successes=0;String e=null;for(var f:futures)try{e=f.get();successes++;}catch(ExecutionException ex){assertEquals(409,((SignupFailure)ex.getCause()).status());}assertEquals(1,successes);
            String p=proof("phone");JoinRequest request=request(e,p);var results=pool.invokeAll(List.<Callable<Boolean>>of(()->joins.join(request).isJoinSuccess(),()->joins.join(request).isJoinSuccess()));successes=0;
            for(var f:results)try{assertTrue(f.get());successes++;}catch(ExecutionException ex){assertEquals(409,((SignupFailure)ex.getCause()).status());}assertEquals(1,successes);verify(users,times(1)).save(any());
        } finally { pool.shutdownNow(); }
    }
    @Test void actualMailServiceUsesOnlyCanonicalMailboxAndNeverReturnsPin() throws Exception {
        JavaMailSender sender=mock(JavaMailSender.class);List<MimeMessage> mailbox=new ArrayList<>();when(sender.createMimeMessage()).thenAnswer(call->new MimeMessage((Session)null));doAnswer(call->{mailbox.add(call.getArgument(0));return null;}).when(sender).send(any(MimeMessage.class));
        EmailAuthService service=new EmailAuthService(sender,users,verification);ReflectionTestUtils.setField(service,"from","rf043@example.test");
        EmailAuthResponse response=service.sendMail(new EmailAuthRequest("Synthetic"," Local@EXAMPLE.test ",attempt.attemptId(),attempt.attemptSecret()));
        assertEquals("Local@example.test",mailbox.get(0).getAllRecipients()[0].toString());String body=mailbox.get(0).getContent().toString();String pin=body.substring(body.length()-6);assertTrue(pin.matches("[0-9]{6}"));
        String json=new ObjectMapper().writeValueAsString(response);assertFalse(json.contains("authNumber"));assertFalse(json.contains(pin));assertFalse(json.contains("Local"));assertNotNull(verification.verify(attempt.attemptId(),attempt.attemptSecret(),"email",response.challengeId(),pin));
    }
    @Test void actualSmsServiceUsesAdapterAndDisabledOrInvalidRequestsNeverReachIt() {
        SignupSmsSender sender=mock(SignupSmsSender.class);var disabled=new PhoneNumberAuthService(users,verification,sender,false);assertFalse(disabled.sendMessage(new PhoneNumberAuthRequest("S","011-1234-5678",attempt.attemptId(),attempt.attemptSecret())).isSuccess());verifyNoInteractions(sender,users);
        var service=new PhoneNumberAuthService(users,verification,sender,true);failure(400,()->service.sendMessage(new PhoneNumberAuthRequest("S","０１１１２３４５６７８",attempt.attemptId(),attempt.attemptSecret())));verifyNoInteractions(sender,users);
        when(sender.send(eq("01112345678"),eq("S"),matches("[0-9]{6}"))).thenReturn(true);assertTrue(service.sendMessage(new PhoneNumberAuthRequest("S","011-1234-5678",attempt.attemptId(),attempt.attemptSecret())).isSuccess());verify(sender).send(eq("01112345678"),eq("S"),matches("[0-9]{6}"));
    }
    @Test void lateProviderAcceptanceAfterExpiryIsNotReportedAsIssuanceSuccess() {var r=verification.reserve(attempt.attemptId(),attempt.attemptSecret(),"email","Local@example.test");store.now+=300_000;failure(400,()->verification.complete(r,true));assertEquals("pending",store.attempt().get("email:state"));}
    @Test void missingConfigurationControllersFailClosedIncludingOldJoinWithoutAnySenderOrDatabaseCall() throws Exception {
        var state=mock(SignupStore.class);var v=new SignupVerification(new SignupCrypto(false,"","",""),state);
        var sender=mock(JavaMailSender.class);var sms=mock(SignupSmsSender.class);
        var auth=new com.ngng.api.user.user.controller.JoinAuthController(new EmailAuthService(sender,users,v),new PhoneNumberAuthService(users,v,sms,true),v);
        var join=new com.ngng.api.user.user.controller.JoinController(new JoinService(users,roles,passwords,points,v));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(auth,join).setControllerAdvice(new com.ngng.api.user.user.controller.SignupExceptionHandler()).build();
        for(String path:List.of("/join/auth/attempt","/join/auth/email","/join/auth/phonenumber","/join/auth/email/verify","/join/auth/phonenumber/verify","/join")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType("application/json").content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("SIGNUP_UNAVAILABLE"));
        }
        verifyNoInteractions(state,sender,sms,users,roles,passwords,points);
    }
    @Test void failedSmsDeliveryKeepsQuotaAndCannotBecomeVerified() {
        var sender=mock(SignupSmsSender.class);when(sender.send(anyString(),anyString(),anyString())).thenReturn(false);
        var service=new PhoneNumberAuthService(users,verification,sender,true);var request=new PhoneNumberAuthRequest("S","01112345678",attempt.attemptId(),attempt.attemptSecret());
        for(int i=0;i<3;i++)failure(503,()->service.sendMessage(request));failure(429,()->service.sendMessage(request));verify(sender,times(3)).send(anyString(),anyString(),anyString());assertEquals("failed",store.attempt().get("phone:state"));
    }
    @Test void pointFailureLeavesConsumedProofsAndDoesNotAutomaticallyReplayUserSave() {
        String e=proof("email"),p=proof("phone");doThrow(new IllegalStateException("synthetic-point-failure")).when(points).createInitByUser(any());
        assertThrows(IllegalStateException.class,()->joins.join(request(e,p)));assertEquals("consumed",store.attempt().get("state"));failure(409,()->joins.join(request(e,p)));verify(users,times(1)).save(any());verify(points,times(1)).createInitByUser(any());
    }
    @Test void proofExpirationNeverExceedsAbsoluteAttemptDeadline() {
        store.now+=1_799_999;var r=sent("email");var p=verification.verify(attempt.attemptId(),attempt.attemptSecret(),"email",r.challengeId(),r.code());assertEquals(2_800_000,p.expiresAt());store.now++;
        failure(400,()->verification.verify(attempt.attemptId(),attempt.attemptSecret(),"email",r.challengeId(),r.code()));
    }
    @Test void challengeCollisionRetriesAreBoundedAndPreserveCurrentProofAndQuota() {
        proof("email");String before=store.snapshot();store.collisions=3;failure(503,()->sent("email"));assertEquals(before,store.snapshot());
    }
    @Test void actualMailUnknownDeliveryKeepsQuotaAndNeverActivatesTheCode() throws Exception {
        var sender=mock(JavaMailSender.class);List<MimeMessage> accepted=new ArrayList<>();when(sender.createMimeMessage()).thenAnswer(c->new MimeMessage((Session)null));
        doAnswer(c->{accepted.add(c.getArgument(0));throw new org.springframework.mail.MailSendException("synthetic-provider-result-unknown");}).when(sender).send(any(MimeMessage.class));
        var service=new EmailAuthService(sender,users,verification);ReflectionTestUtils.setField(service,"from","rf043@example.test");var request=new EmailAuthRequest("S","Local@example.test",attempt.attemptId(),attempt.attemptSecret());
        for(int i=0;i<3;i++)failure(503,()->service.sendMail(request));failure(429,()->service.sendMail(request));assertEquals(3,accepted.size());verify(sender,times(3)).send(any(MimeMessage.class));
        String body=accepted.get(2).getContent().toString(),pin=body.substring(body.length()-6);String generation=store.attempt().get("email:generation");String before=store.snapshot();
        failure(400,()->verification.verify(attempt.attemptId(),attempt.attemptSecret(),"email",generation,pin));assertEquals(before,store.snapshot());assertEquals("failed",store.attempt().get("email:state"));
    }
    @Test void concurrentFourIssuancesAtSameModelTimeReserveOnlyThreeAndCallSenderThreeTimes() throws Exception {
        var sender=mock(SignupSmsSender.class);when(sender.send(anyString(),anyString(),anyString())).thenReturn(true);var service=new PhoneNumberAuthService(users,verification,sender,true);
        List<Callable<Boolean>> tasks=new ArrayList<>();for(int i=0;i<4;i++){var a=verification.create();tasks.add(()->service.sendMessage(new PhoneNumberAuthRequest("S","011-1234-5678",a.attemptId(),a.attemptSecret())).isSuccess());}
        var pool=Executors.newFixedThreadPool(4);try {int successes=0,limited=0;for(var result:pool.invokeAll(tasks))try{assertTrue(result.get());successes++;}catch(ExecutionException e){assertEquals(429,((SignupFailure)e.getCause()).status());limited++;}assertEquals(3,successes);assertEquals(1,limited);verify(sender,times(3)).send(anyString(),anyString(),anyString());assertEquals(3,store.quotas.values().iterator().next().size());}finally{pool.shutdownNow();}
    }
    @Test @SuppressWarnings({"unchecked","rawtypes"}) void productionRedisAdapterMapsNullAndExecutionFailureToUnavailableWithoutFallback() {
        var template=mock(org.springframework.data.redis.core.StringRedisTemplate.class);var redisState=new RedisSignupStore(template);
        failure(503,()->redisState.run("consume",List.of("synthetic-key"),"synthetic-digest"));
        doThrow(new IllegalStateException("synthetic-redis-timeout")).when(template).execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class));
        failure(503,()->redisState.run("consume",List.of("synthetic-key"),"synthetic-digest"));verify(template,times(2)).execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class));
    }
    @Test void malformedOperationArgumentsAndAliasedIssueKeysHaveNoBehavioralStoreWrites() {
        String digest="a".repeat(64),t=crypto.token(),key=store.attempts.keySet().iterator().next();
        Map<String,String[]> args=Map.of("create",new String[]{digest},"issue",new String[]{digest,"email",digest,t,digest,t},"complete",new String[]{digest,"email",t,"sent"},"verify",new String[]{digest,"email",t,digest,digest},"consume",new String[]{digest,digest,digest,digest,digest});
        String before=store.snapshot();
        for(var entry:args.entrySet()) {
            String op=entry.getKey();String[] valid=entry.getValue();List<String> keys=op.equals("issue")?List.of(key,crypto.key("quota","email","b".repeat(64)),crypto.key("challenge","email","c".repeat(64))):List.of(key);
            assertEquals("UNAVAILABLE",store.run(op,keys,Arrays.copyOf(valid,valid.length-1)).get(0));
            assertEquals("UNAVAILABLE",store.run(op,List.of(),valid).get(0));
            var bad=valid.clone();bad[0]="bad-digest";assertEquals("UNAVAILABLE",store.run(op,keys,bad).get(0));
            if(Set.of("issue","complete","verify").contains(op)){bad=valid.clone();bad[1]=null;assertEquals("UNAVAILABLE",store.run(op,keys,bad).get(0));}
        }
        assertEquals("UNAVAILABLE",store.run("issue",List.of(key,"aliased","aliased"),args.get("issue")).get(0));
        assertEquals("UNAVAILABLE",store.run("create",List.of(""),digest).get(0));
        assertEquals("UNAVAILABLE",store.run("issue",List.of(key,crypto.key("quota","email","b".repeat(64)),new SignupCrypto(true,"test","rf043_epoch_00002",KEY).key("challenge","email","c".repeat(64))),args.get("issue")).get(0));assertEquals(before,store.snapshot());
    }
    @Test void corruptedAttemptOrChannelStateAndNonNumericTtlFailClosedWithoutReservingQuota() {
        var original=new HashMap<>(store.attempt());
        for(String state:List.of("unknown","")){store.attempt().put("state",state);String before=store.snapshot();failure(503,()->sent("email"));assertEquals(before,store.snapshot());store.attempt().clear();store.attempt().putAll(original);}
        store.attempt().remove("state");String missing=store.snapshot();failure(503,()->sent("email"));assertEquals(missing,store.snapshot());store.attempt().putAll(original);
        store.attempt().put("expiry","NaN");String badExpiry=store.snapshot();failure(503,()->sent("email"));assertEquals(badExpiry,store.snapshot());store.attempt().putAll(original);
        sent("email");var channel=new HashMap<>(store.attempt());
        for(var pair:List.of(Map.entry("email:codeExpiry","NaN"),Map.entry("email:checks","1.5"),Map.entry("email:state","unexpected"),Map.entry("email:code","bad"))){store.attempt().put(pair.getKey(),pair.getValue());String before=store.snapshot();failure(503,()->sent("email"));assertEquals(before,store.snapshot());store.attempt().clear();store.attempt().putAll(channel);}
    }
    @Test @SuppressWarnings("unchecked") void luaPreflightGuardsStructurallyPrecedeEveryMutationWithoutExecutingRedis() {
        Map<String,String> scripts=(Map<String,String>)ReflectionTestUtils.getField(RedisSignupStore.class,"SCRIPTS");assertNotNull(scripts);
        for(var entry:scripts.entrySet()) {
            String script=entry.getValue();int write=Integer.MAX_VALUE;for(String command:List.of("HSETNX","HSET","HDEL","SET","ZADD","ZREMRANGEBYSCORE","PEXPIRE","PEXPIREAT")){int i=script.indexOf("redis.call('"+command+"'");if(i>=0)write=Math.min(write,i);}
            assertTrue(script.indexOf("#KEYS")>=0&&script.indexOf("#KEYS")<write,entry.getKey());assertTrue(script.indexOf("#ARGV")>=0&&script.indexOf("#ARGV")<write,entry.getKey());assertTrue(script.contains("local function integer"));
            if(!entry.getKey().equals("create")){assertTrue(script.indexOf("attemptState ~= 'open'")>=0&&script.indexOf("attemptState ~= 'open'")<write);assertTrue(script.indexOf("channelState(KEYS[1]")>=0&&script.indexOf("channelState(KEYS[1]")<write);}
        }
    }
}
