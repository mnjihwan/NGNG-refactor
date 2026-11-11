package com.ngng.api.isolation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.global.security.filter.TokenFilter;
import com.ngng.api.global.security.jwt.util.JwtTokenProvider;
import com.ngng.api.user.point.entity.PointHistory;
import com.ngng.api.user.point.repository.PointHistoryRepository;
import com.ngng.api.user.point.repository.PaymentSettlementRepository;
import com.ngng.api.user.point.payment.PaymentVerificationGate;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.user.user.entity.Role;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.dto.response.PhoneNumberAuthResponse;
import com.ngng.api.user.user.service.PhoneNumberAuthService;
import com.ngng.api.product.product.service.ProductService;
import com.ngng.api.transaction.service.TransactionDetailsService;
import jakarta.mail.Message;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Restricted Boot bootstrap + real MVC/security/JWT/user/point/email logic, repository doubles.
 * No physical persistence, transaction advice, real HTTP server, login provider or SDK send. */
@SpringBootTest(classes = Rf016IsolationConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"spring.config.location=file:rf016-must-never-load.properties",
                "spring.config.import=file:rf016-import-must-never-load.properties",
                "spring.config.additional-location=file:rf016-additional-must-never-load.properties",
                "spring.profiles.active=rf016-poison", "rf016.poison=must-be-removed"})
@ContextConfiguration(loader = Rf016SafeContextLoader.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(listeners = Rf016IsolatedBootSmokeTest.CloseVerificationListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class Rf016IsolatedBootSmokeTest {
    static final String EMAIL = "rf016-owner@example.invalid";
    static ConfigurableApplicationContext lifecycleContext;
    static final AtomicBoolean closed = new AtomicBoolean();
    @Autowired WebApplicationContext context;
    @Autowired ConfigurableEnvironment environment;
    @Autowired UserRepository users;
    @Autowired PointHistoryRepository points;
    @Autowired PhoneNumberAuthService sms;
    @Autowired JavaMailSender sender;
    @Autowired Rf016IsolationConfiguration.MemoryMailbox mailbox;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper mapper;
    MockMvc mvc;
    User user;
    PointHistory latest;

    @BeforeAll static void observeLifecycle(@Autowired ConfigurableApplicationContext context) {
        lifecycleContext = context;
        context.addApplicationListener(event -> { if (event instanceof ContextClosedEvent) closed.set(true); });
    }
    @AfterAll static void clearSecurity() { SecurityContextHolder.clearContext(); }
    public static final class CloseVerificationListener extends AbstractTestExecutionListener {
        // afterTestClass runs in reverse: EventPublishing(10000), DirtiesContext(3000), then this.
        @Override public int getOrder() { return 2000; }
        @Override public void afterTestClass(TestContext testContext) {
            assertFalse(lifecycleContext.isActive());
            assertTrue(closed.get(), "restricted Boot context must emit close event");
            System.out.println("RF016 restricted-context-closed=true");
        }
    }
    @BeforeEach void fixture() {
        SecurityContextHolder.clearContext();
        reset(users, points, sms);
        clearInvocations(sender);
        mailbox.messages.clear();
        user = User.builder().userId(16001L).name("RF016 Fixture").email(EMAIL).visible(true)
                .role(Role.builder().roleType("USER").build()).build();
        latest = PointHistory.builder().id(16002L).beforeCost(0L).addCost(1000L).cost(1000L)
                .type("충전").typeDetail("RF016 fixture").user(user).build();
        when(users.findUserByEmail(EMAIL)).thenAnswer(inv -> {
            CustomUserDetails principal = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            assertNull(principal.getUser().getUserId(), "database identity must come from repository double");
            return Optional.of(user);
        });
        when(points.findLastByUserId(16001L)).thenReturn(Optional.of(latest));
        when(points.findAllByUserId(16001L)).thenReturn(List.of(latest));
        when(users.lockUserIdForPointUpdate(16001L)).thenReturn(Optional.of(16001L));
        when(points.findLatestCostForUpdate(16001L)).thenReturn(Optional.of(1000L));
        when(points.save(any())).thenAnswer(inv -> { PointHistory p = inv.getArgument(0); p.setId(16003L); return p; });
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void noLeak() {
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(context.getBean(ProductService.class), context.getBean(TransactionDetailsService.class));
        verifyNoInteractions(context.getBean(ProductRepository.class), context.getBean(TransactionDetailsRepository.class),
                context.getBean(PaymentVerificationGate.class), context.getBean(PaymentSettlementRepository.class));
        mailbox.messages.clear();
        reset(users, points, sms);
        clearInvocations(sender);
    }
    String access() { return tokens.createAccessToken(new CustomUserDetails(user)); }
    JsonNode json(byte[] bytes) throws Exception { return mapper.readTree(bytes); }

    @Test void configurationWasSanitizedBeforeConfigDataAndOnlyExplicitBeansExist() {
        assertEquals("isolated-only", environment.getProperty("rf016.fixture.config"));
        assertEquals("classpath:/rf016-isolation.properties", environment.getProperty("spring.config.location"));
        assertEquals("", environment.getProperty("spring.config.import"));
        assertEquals("", environment.getProperty("spring.config.additional-location"));
        assertNull(environment.getProperty("rf016.poison"));
        assertEquals(0, environment.getActiveProfiles().length);
        assertFalse(environment.getPropertySources().contains("systemProperties"));
        assertFalse(environment.getPropertySources().contains("systemEnvironment"));
        environment.getPropertySources().forEach(source -> {
            assertFalse(source.getName().contains("application.yml"));
            assertFalse(source.getName().contains("must-never-load"));
            assertFalse(source.getName().contains("rf016-poison"));
        });
        assertFalse(AutoConfigurationPackages.has(context.getAutowireCapableBeanFactory()));
        List<String> inventory = new ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            if (type == null) continue;
            String className = type.getName();
            inventory.add(name + "=" + className);
            assertFalse(className.matches(".*(DataSource|EntityManagerFactory|TransactionManager|RedisConnectionFactory|LettuceConnectionFactory|RedisTemplate|RedisCacheManager|ElasticsearchClient|RestHighLevelClient|AmazonS3Client|DefaultMessageService|JavaMailSenderImpl).*"),
                    "unexpected external resource type: " + className);
            assertFalse(className.startsWith("com.ngng.api.global.config."), "application resource configuration imported");
        }
        inventory.stream().sorted().forEach(value -> System.out.println("RF016_BEAN " + value));
        environment.getPropertySources().forEach(source -> System.out.println("RF016_SOURCE " + source.getName()));
        assertTrue(mockingDetails(users).isMock()); assertTrue(mockingDetails(points).isMock());
        assertTrue(mockingDetails(sender).isMock()); assertTrue(mockingDetails(sms).isMock());
        assertNull(org.springframework.test.util.ReflectionTestUtils.getField(sms, "messageService"), "SMS SDK constructor must not run");
        FilterChainProxy chain = context.getBean(FilterChainProxy.class);
        assertTrue(chain.getFilterChains().stream().flatMap(c -> c.getFilters().stream()).anyMatch(TokenFilter.class::isInstance));
        var converters = context.getBean(RequestMappingHandlerAdapter.class).getMessageConverters().stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance).map(MappingJackson2HttpMessageConverter.class::cast).toList();
        assertEquals(1, converters.size()); assertSame(mapper, converters.get(0).getObjectMapper());
    }
    @Test void authenticatedUserReadUsesDatabaseIdentityAndPointDouble() throws Exception {
        byte[] body = mvc.perform(get("/users").header("Authorization", access())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        JsonNode value = json(body);
        assertTrue(value.path("userId").asLong() == 16001L);
        assertTrue(value.path("point").asLong() == 1000L);
        verify(users).findUserByEmail(EMAIL); verify(points).findLastByUserId(16001L);
        verifyNoMoreInteractions(users, points); verifyNoInteractions(sender, sms);
    }
    @ParameterizedTest @ValueSource(strings = {"/points", "/points/all"})
    void authenticatedPointReadsNeverWrite(String path) throws Exception {
        JsonNode value = json(mvc.perform(get(path).header("Authorization", access())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        if (path.endsWith("all")) { assertEquals(1, value.size()); value = value.get(0); }
        assertTrue(value.path("cost").asLong() == 1000L);
        assertFalse(value.has("user"));
        verify(users).findUserByEmail(EMAIL);
        if (path.endsWith("all")) verify(points).findAllByUserId(16001L); else verify(points).findLastByUserId(16001L);
        verifyNoMoreInteractions(users, points); verifyNoInteractions(sender, sms);
    }
    @Test void authenticatedChargeUsesRealServiceAndOrderedRepositoryDoubles() throws Exception {
        JsonNode value = json(mvc.perform(post("/points").header("Authorization", access()).contentType("application/json")
                .content("{\"addCost\":200,\"type\":\"충전\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsByteArray());
        assertTrue(value.path("beforeCost").asLong() == 1000L);
        assertTrue(value.path("cost").asLong() == 1200L); assertFalse(value.has("user"));
        var order = inOrder(users, points);
        order.verify(users).findUserByEmail(EMAIL); order.verify(users).lockUserIdForPointUpdate(16001L);
        order.verify(points).findLatestCostForUpdate(16001L);
        order.verify(points).save(argThat(p -> p.getUser() == user && p.getCost() == 1200L && p.getAddCost() == 200L));
        verifyNoMoreInteractions(users, points); verifyNoInteractions(sender, sms);
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "malformed", "expired"})
    void invalidAuthenticationIs401BeforeBusinessAccess(String kind) throws Exception {
        var request = get("/users");
        if (kind.equals("malformed")) request.header("Authorization", "Bearer synthetic-invalid");
        if (kind.equals("expired")) request.header("Authorization", "Bearer " + io.jsonwebtoken.Jwts.builder().setSubject(EMAIL)
                .claim("role", "USER").claim("type", "access").setExpiration(new Date(1L))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64URL.decode(Rf016SafeContextLoader.SECRET))).compact());
        mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        verifyNoInteractions(users, points, sender, sms);
    }
    @Test void duplicateEmailHas409AndNoMessageCreationOrSend() throws Exception {
        when(users.existsByEmail("rf016-new@example.invalid")).thenReturn(true);
        mvc.perform(post("/join/auth/email").contentType("application/json")
                .content("{\"name\":\"RF016 Fixture\",\"email\":\"rf016-new@example.invalid\"}"))
                .andExpect(status().isConflict()).andExpect(content().string(""));
        verify(users).existsByEmail("rf016-new@example.invalid"); verifyNoMoreInteractions(users);
        assertTrue(mailbox.messages.isEmpty()); verifyNoInteractions(sender, points, sms);
    }
    @Test void newEmailRunsRealServiceButOnlyMemorySenderReceivesIt() throws Exception {
        JsonNode value = json(mvc.perform(post("/join/auth/email").contentType("application/json")
                .content("{\"name\":\"RF016 Fixture\",\"email\":\"rf016-new@example.invalid\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEquals(1, mailbox.messages.size());
        var message = mailbox.messages.get(0);
        assertTrue(message.getFrom()[0].toString().equals("rf016-sender@example.invalid"));
        assertTrue(message.getRecipients(Message.RecipientType.TO)[0].toString().equals("rf016-new@example.invalid"));
        String code = value.path("authNumber").asText();
        assertTrue(code.matches("[0-9]{6}")); assertTrue(message.getContent().toString().contains(code));
        verify(sender).createMimeMessage(); verify(sender).send(any(jakarta.mail.internet.MimeMessage.class));
        verifyNoMoreInteractions(sender); verifyNoInteractions(points, sms);
    }
    @Test void smsControllerUsesServiceBoundaryDoubleWithoutSdkConstruction() throws Exception {
        when(sms.sendMessage(any())).thenReturn(PhoneNumberAuthResponse.fail());
        mvc.perform(post("/join/auth/phonenumber").contentType("application/json")
                .content("{\"name\":\"RF016 Fixture\",\"phoneNumber\":\"synthetic-number\"}"))
                .andExpect(status().isConflict()).andExpect(content().string(""));
        verify(sms).sendMessage(argThat(r -> "synthetic-number".equals(r.phoneNumber())));
        verifyNoMoreInteractions(sms); verifyNoInteractions(users, points, sender);
    }
}
