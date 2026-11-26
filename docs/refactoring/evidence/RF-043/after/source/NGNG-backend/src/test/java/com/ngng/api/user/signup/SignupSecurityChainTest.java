package com.ngng.api.user.signup;

import com.fasterxml.jackson.databind.*;
import com.ngng.api.global.security.config.SecurityConfig;
import com.ngng.api.global.security.handler.*;
import com.ngng.api.global.security.jwt.util.*;
import com.ngng.api.user.point.entity.PointHistory;
import com.ngng.api.user.point.repository.*;
import com.ngng.api.user.point.service.PointHistoryService;
import com.ngng.api.user.user.controller.*;
import com.ngng.api.user.user.entity.*;
import com.ngng.api.user.user.entity.Role;
import com.ngng.api.user.user.repository.*;
import com.ngng.api.user.user.service.*;
import com.ngng.api.user.user.service.signup.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Explicit real MVC/Security/JWT/controllers/services, memory mail and behavioral Redis/repository doubles.
 * No Boot discovery, resource config, Redis client, SDK, database or real HTTP server is constructed. */
class SignupSecurityChainTest {
    static AnnotationConfigWebApplicationContext context;
    static MockMvc mvc;
    static ObjectMapper mapper = new ObjectMapper();
    UserRepository users; UserRoleRepository roles; PointHistoryRepository points; JavaMailSender mail;
    MemorySignupStore store; SignupSmsSender sms; List<MimeMessage> messages;
    @BeforeAll static void open() {
        context=new AnnotationConfigWebApplicationContext();
        context.getEnvironment().getPropertySources().remove("systemProperties");context.getEnvironment().getPropertySources().remove("systemEnvironment");
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("rf043-only",Map.of("jwt.expiration.access","3600000","jwt.expiration.refresh","7200000","spring.mail.username","rf043@example.test")));
        context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterAll static void close() {context.close();assertFalse(context.isActive());}
    @BeforeEach void setup() {
        users=context.getBean(UserRepository.class);roles=context.getBean(UserRoleRepository.class);points=context.getBean(PointHistoryRepository.class);mail=context.getBean(JavaMailSender.class);sms=context.getBean(SignupSmsSender.class);store=context.getBean(MemorySignupStore.class);
        reset(users,roles,points,mail,sms);store.clear();SecurityContextHolder.clearContext();messages=new ArrayList<>();
        when(mail.createMimeMessage()).thenAnswer(c->new MimeMessage((Session)null));doAnswer(c->{messages.add(c.getArgument(0));return null;}).when(mail).send(any(MimeMessage.class));when(sms.send(anyString(),anyString(),anyString())).thenReturn(true);
        when(roles.findByRoleType("UNCONFIRMED_USER")).thenReturn(Role.builder().roleType("UNCONFIRMED_USER").build());when(users.save(any())).thenAnswer(c->{User u=c.getArgument(0);u.setUserId(43001L);return u;});when(points.save(any())).thenAnswer(c->{PointHistory p=c.getArgument(0);p.setId(43002L);return p;});
    }
    @AfterEach void clearedSecurity() {assertNull(SecurityContextHolder.getContext().getAuthentication());}
    JsonNode postJson(String path,Object body,int status) throws Exception {
        return mapper.readTree(mvc.perform(post(path).contentType("application/json").content(mapper.writeValueAsBytes(body))).andExpect(status().is(status)).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andReturn().getResponse().getContentAsByteArray());
    }
    Map<String,Object> attempt() throws Exception {JsonNode a=postJson("/join/auth/attempt",Map.of(),201);return new LinkedHashMap<>(Map.of("attemptId",a.path("attemptId").asText(),"attemptSecret",a.path("attemptSecret").asText()));}
    Map<String,Object> body(Map<String,Object> a,Object... pairs) {Map<String,Object> b=new LinkedHashMap<>(a);for(int i=0;i<pairs.length;i+=2)b.put(pairs[i].toString(),pairs[i+1]);return b;}
    String verifyMail(Map<String,Object> a) throws Exception {
        JsonNode challenge=postJson("/join/auth/email",body(a,"name","Synthetic","email"," Local@EXAMPLE.test "),200);assertFalse(challenge.has("authNumber"));assertFalse(challenge.has("email"));
        String text=messages.get(messages.size()-1).getContent().toString();String pin=text.substring(text.length()-6);
        JsonNode proof=postJson("/join/auth/email/verify",body(a,"challengeId",challenge.path("challengeId").asText(),"code",pin),200);return proof.path("proof").asText();
    }
    String verifyPhone(Map<String,Object> a) throws Exception {
        JsonNode challenge=postJson("/join/auth/phonenumber",body(a,"name","Synthetic","phoneNumber","011-1234-5678"),200);
        var pin=org.mockito.ArgumentCaptor.forClass(String.class);verify(sms).send(eq("01112345678"),eq("Synthetic"),pin.capture());
        return postJson("/join/auth/phonenumber/verify",body(a,"challengeId",challenge.path("challengeId").asText(),"code",pin.getValue()),200).path("proof").asText();
    }
    Map<String,Object> joinBody(Map<String,Object>a,String e,String p){return body(a,"name","Synthetic","email","Local@EXAMPLE.test","phoneNumber","011-1234-5678","nickname","fixture","password","synthetic-password","emailProof",e,"phoneProof",p);}
    @Test void explicitContextContainsNoExternalConnectionsSdkOrConfigDiscovery() {
        for(String name:context.getBeanDefinitionNames()) {Class<?> type=context.getType(name);if(type!=null)assertFalse(type.getName().matches(".*(DataSource|EntityManagerFactory|TransactionManager|RedisTemplate|RedisConnectionFactory|DefaultMessageService|JavaMailSenderImpl|NurigoSignupSmsSender).*"),type.getName());}
        assertFalse(context.getEnvironment().getPropertySources().contains("systemEnvironment"));assertTrue(mockingDetails(users).isMock());assertTrue(mockingDetails(sms).isMock());
    }
    @Test void publicSignupSkipsJwtButRequiresBothServerProofsAndCreatesEncodedUnconfirmedUserAndPoint() throws Exception {
        var a=attempt();String e=verifyMail(a),p=verifyPhone(a);
        mvc.perform(post("/join").header("Authorization","Bearer deliberately-invalid-on-public-path").contentType("application/json").content(mapper.writeValueAsBytes(joinBody(a,e,p))))
            .andExpect(status().isCreated()).andExpect(header().string("Location","/users/43001")).andExpect(content().string(""));
        verify(users).save(argThat(u->u.getEmail().equals("Local@example.test")&&u.getPhoneNumber().equals("01112345678")&&u.getRole().getRoleType().equals("UNCONFIRMED_USER")&&!u.getPassword().equals("synthetic-password")));
        verify(points).save(argThat(pnt->pnt.getUser().getUserId()==43001L&&pnt.getCost()==0L&&pnt.getAddCost()==0L));
        postJson("/join",joinBody(a,e,p),409);verify(users,times(1)).save(any());verify(points,times(1)).save(any());
    }
    @Test void oldClientAndMissingChannelAreRejectedBeforeAnyUserOrPointWrite() throws Exception {
        postJson("/join",Map.of("name","S","email","Local@example.test","phoneNumber","01112345678","password","p","nickname","n"),400);
        postJson("/join/auth/email",Map.of("name","S","email","Local@example.test"),400);
        var a=attempt();String e=verifyMail(a);postJson("/join",joinBody(a,e,"not-a-proof"),400);verify(users,never()).save(any());verifyNoInteractions(points,roles,sms);
    }
    @Test void duplicateIssuanceRetains409AndFinalDuplicateRetains400AfterConsumption() throws Exception {
        when(users.existsByEmail("Local@example.test")).thenReturn(true);var a=attempt();mvc.perform(post("/join/auth/email").contentType("application/json").content(mapper.writeValueAsBytes(body(a,"name","S","email","Local@example.test")))).andExpect(status().isConflict()).andExpect(content().string(""));verifyNoInteractions(mail);
        when(users.existsByEmail("Local@example.test")).thenReturn(false);String e=verifyMail(a),p=verifyPhone(a);when(users.existsByEmail("Local@example.test")).thenReturn(true);
        mvc.perform(post("/join").contentType("application/json").content(mapper.writeValueAsBytes(joinBody(a,e,p)))).andExpect(status().isBadRequest()).andExpect(content().string(""));assertEquals("consumed",store.attempt().get("state"));verify(users,never()).save(any());verifyNoInteractions(points);
    }
    @Test void wrongSecretAndLimitHaveGenericResponsesAndNoMailOrQuotaWrites() throws Exception {
        var a=attempt();String before=store.snapshot();var wrong=body(a,"attemptSecret",context.getBean(SignupCrypto.class).token(),"email","Local@example.test","name","S");
        assertEquals("SIGNUP_VERIFICATION_REQUIRED",postJson("/join/auth/email",wrong,400).path("code").asText());assertEquals(before,store.snapshot());verifyNoInteractions(mail);
        verifyMail(a);verifyMail(a);verifyMail(a);
        mvc.perform(post("/join/auth/email").contentType("application/json").content(mapper.writeValueAsBytes(body(a,"email","Local@example.test","name","S")))).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","600"));verify(mail,times(3)).send(any(MimeMessage.class));
    }
    @Configuration @EnableWebMvc @EnableWebSecurity @Import({SecurityConfig.class,JoinController.class,JoinAuthController.class,SignupExceptionHandler.class})
    static class Config {
        @Bean @Primary AuthenticationConfiguration authenticationConfiguration() throws Exception {var c=mock(AuthenticationConfiguration.class);when(c.getAuthenticationManager()).thenReturn(mock(AuthenticationManager.class));return c;}
        @Bean JwtTokenVerifier verifier(){return new JwtTokenVerifier(SignupVerificationTest.KEY);}
        @Bean JwtTokenProvider provider(){return new JwtTokenProvider(SignupVerificationTest.KEY);}
        @Bean CustomLogoutHandler logout(){return new CustomLogoutHandler();}
        @Bean CustomLogoutSuccessHandler logoutSuccess(){return new CustomLogoutSuccessHandler();}
        @Bean UserRepository users(){return mock(UserRepository.class);}
        @Bean UserRoleRepository roles(){return mock(UserRoleRepository.class);}
        @Bean PointHistoryRepository points(){return mock(PointHistoryRepository.class);}
        @Bean SignupCrypto crypto(){return new SignupCrypto(true,"test","rf043_epoch_00001",SignupVerificationTest.KEY);}
        @Bean MemorySignupStore store(){return new MemorySignupStore();}
        @Bean SignupVerification verification(SignupCrypto c,MemorySignupStore s){return new SignupVerification(c,s);}
        @Bean JavaMailSender sender(){return mock(JavaMailSender.class);}
        @Bean SignupSmsSender sms(){return mock(SignupSmsSender.class);}
        @Bean EmailAuthService email(JavaMailSender s,UserRepository u,SignupVerification v){return new EmailAuthService(s,u,v);}
        @Bean PhoneNumberAuthService phone(UserRepository u,SignupVerification v,SignupSmsSender s){return new PhoneNumberAuthService(u,v,s,true);}
        @Bean PointHistoryService pointService(PointHistoryRepository p,UserRepository u){return new PointHistoryService(p,new AuthService(u),u,mock(com.ngng.api.product.product.repository.ProductRepository.class),mock(com.ngng.api.transaction.repository.TransactionDetailsRepository.class),mock(com.ngng.api.user.point.payment.PaymentVerificationGate.class),mock(PaymentSettlementRepository.class));}
        @Bean JoinService join(UserRepository u,UserRoleRepository r,PointHistoryService p,SignupVerification v,org.springframework.security.crypto.password.PasswordEncoder e){return new JoinService(u,r,e,p,v);}
    }
}
