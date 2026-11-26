package com.ngng.api.user.point.payment;

import com.ngng.api.user.point.controller.PointController;
import com.ngng.api.product.product.entity.*;
import com.ngng.api.product.thumbnail.entity.Thumbnail;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.controller.TransactionController;
import com.ngng.api.transaction.entity.TransactionDetails;
import com.ngng.api.transaction.entity.TransactionStatus;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.transaction.repository.TransactionStatusRepository;
import com.ngng.api.transaction.service.TransactionDetailsService;
import com.ngng.api.transaction.service.TransactionRequestService;
import jakarta.servlet.http.Cookie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ngng.api.global.security.config.SecurityConfig;
import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.global.security.handler.*;
import com.ngng.api.global.security.jwt.util.*;
import com.ngng.api.user.point.entity.PointHistory;
import com.ngng.api.user.point.dto.PointHistoryResponseDTO;
import com.ngng.api.user.point.repository.PointHistoryRepository;
import com.ngng.api.user.point.service.PointHistoryService;
import com.ngng.api.user.user.entity.Role;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.*;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Boot Jackson + MVC + security/JWT/AuthService/point service; repositories are doubles.
 * Login AuthenticationConfiguration/AuthenticationManager are also doubles, unused by these point requests.
 * No component scanning, datasource, transaction manager, network, or real credentials. */
class PaymentAndStatusChainTest {
    private static final String EMAIL = "rf008-fixture@example.invalid";
    private static final String SECRET = Encoders.BASE64URL.encode(
            "rf008-synthetic-key-only-64-bytes-abcdefghijklmnopqrstuvwxyz-123456".getBytes(StandardCharsets.UTF_8));
    private static final Set<String> POINT_KEYS = Set.of("id", "beforeCost", "addCost", "cost", "type", "typeDetail", "createdAt", "updatedAt");
    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mvc;
    private static ObjectMapper mapper;
    private ProductRepository products;
    private TransactionDetailsRepository details;
    private UserRepository users;
    private PointHistoryRepository histories;
    private User user;
    private PointHistory latest;

    @BeforeAll static void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "rf008-test-only", Map.of("jwt.expiration.access", "3600000", "jwt.expiration.refresh", "7200000")));
        context.setServletContext(new MockServletContext());
        context.register(IsolatedConfiguration.class);
        context.refresh();
        mapper = context.getBean(ObjectMapper.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterAll static void stop() { context.close(); }
    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        users = context.getBean(UserRepository.class);
        histories = context.getBean(PointHistoryRepository.class);
        products = context.getBean(ProductRepository.class);
        details = context.getBean(TransactionDetailsRepository.class);
        reset(users, histories, products, details);
        PaymentFixture.allowPayment(products, details, 7001L, 800L);
        user = User.builder().userId(7001L).name("Fixture").email(EMAIL).password("synthetic-password")
                .address("synthetic-address").phoneNumber("synthetic-phone").accountBank("synthetic-bank")
                .accountNumber("synthetic-account").role(Role.builder().roleType("USER").build()).build();
        latest = history(102L, 1000L);
        when(users.findUserByEmail(EMAIL)).thenAnswer(inv -> {
            var principal = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            assertNull(principal.getUser().getUserId(), "JWT must not supply a database ID");
            return Optional.of(user);
        });
        when(histories.findLastByUserId(7001L)).thenReturn(Optional.of(latest));
        when(histories.findAllByUserId(7001L)).thenReturn(List.of(latest, history(99L, 900L)));
        when(users.lockUserIdForPointUpdate(7001L)).thenReturn(Optional.of(7001L));
        when(histories.findLatestCostForUpdate(7001L)).thenReturn(Optional.of(1000L));
        when(histories.save(any())).thenAnswer(inv -> {
            PointHistory saved = inv.getArgument(0);
            saved.setId(103L);
            saved.setCreatedAt(Timestamp.valueOf("2026-01-02 03:04:05.123456789"));
            saved.setUpdatedAt(Timestamp.valueOf("2026-02-03 04:05:06.987654321"));
            return saved;
        });
    }
    private PointHistory history(long id, long cost) {
        return PointHistory.builder().id(id).beforeCost(cost - 100).addCost(100L).cost(cost).type("충전")
                .typeDetail("Fixture 표시 문구").user(user)
                .createdAt(Timestamp.valueOf("2026-01-02 03:04:05.123456789"))
                .updatedAt(Timestamp.valueOf("2026-02-03 04:05:06.987654321")).build();
    }
    private static String token(String role) { return token("access", role, 3600000); }
    private static String token(String type, String role, long duration) {
        return Jwts.builder().setSubject(EMAIL).claim("role", role).claim("type", type)
                .setExpiration(new Date(System.currentTimeMillis() + duration))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(SECRET))).compact();
    }
    private MockHttpServletRequestBuilder pay(String role, String amount, String product) {
        return post("/points/payment").contentType("application/json")
                .content("{\"paymentCost\":" + amount + ",\"payProductId\":" + product + "}")
                .header("Authorization", "Bearer " + token(role));
    }

    @AfterEach void clear() { assertNull(SecurityContextHolder.getContext().getAuthentication()); }
    TransactionDetails jointFixture() {
        var statuses=context.getBean(TransactionStatusRepository.class);
        var settlement=context.getBean(com.ngng.api.user.point.repository.PaymentSettlementRepository.class);
        reset(statuses,settlement);
        when(statuses.findById(any())).thenAnswer(call->Optional.of(new TransactionStatus(call.getArgument(0))));
        var seller=User.builder().userId(999L).name("recorded-seller").build();
        var product=Product.builder().productId(42L).price(10000L).forSale(false).visible(true)
                .user(User.builder().userId(888L).build())
                .category(Category.builder().categoryId(1L).categoryName("synthetic").build())
                .status(Status.builder().statusId(1L).statusName("used").build())
                .thumbnail(Thumbnail.builder().thumbnailId(1L).thumbnailUrl("synthetic").build())
                .productImages(List.of()).tags(List.of()).build();
        var transaction=TransactionDetails.builder().id(88L).product(product).seller(seller).consumer(user)
                .address("synthetic").price(800L).status(new TransactionStatus(1L)).build();
        when(details.findStatusProductId(88L)).thenReturn(Optional.of(42L));
        when(details.lockPaymentTransactions(42L)).thenAnswer(call->List.of(new PaymentFixture.TransactionRow(88L,7001L,800L,42L,999L,transaction.getStatus().getId())));
        when(details.lockStatusTransaction(88L)).thenAnswer(call->{
            var factory=new org.springframework.data.projection.SpelAwareProxyProjectionFactory();
            return Optional.of(factory.createProjection(TransactionDetailsRepository.StatusTransaction.class,
                    Map.of("transactionId",88L,"productId",42L,"consumerId",7001L,"sellerId",999L,"price",800L,"address","synthetic","statusId",transaction.getStatus().getId())));
        });
        when(details.findById(88L)).thenReturn(Optional.of(transaction));
        when(settlement.markPaid(88L,42L,7001L,999L,800L)).thenAnswer(call->{transaction.setStatus(new TransactionStatus(2L));return 1;});
        return transaction;
    }
    MockHttpServletRequestBuilder putStatus(long target) {
        return put("/transaction/88").contentType("application/json").content("{\"statusId\":"+target+"}")
                .header("Authorization","Bearer "+token("USER"));
    }
    @ParameterizedTest @CsvSource({"6,false","7,false","6,true","7,true"})
    void paymentAndExceptionCommandsRespectBothSerialOrdersWithoutRefund(long target,boolean paymentFirst) throws Exception {
        var transaction=jointFixture();
        if(paymentFirst) mvc.perform(pay("USER","800","42")).andExpect(status().isOk()).andExpect(jsonPath("$.cost").value(200));
        mvc.perform(putStatus(target)).andExpect(status().isOk()).andExpect(jsonPath("$.status.id").value(target));
        mvc.perform(pay("USER","800","42")).andExpect(status().isConflict());
        assertEquals(target,transaction.getStatus().getId());
        if(paymentFirst) {
            var debit=org.mockito.ArgumentCaptor.forClass(PointHistory.class);verify(histories).save(debit.capture());assertEquals(200L,debit.getValue().getCost());
            verify(histories).flush();verify(context.getBean(com.ngng.api.user.point.repository.PaymentSettlementRepository.class)).markPaid(88L,42L,7001L,999L,800L);
        } else {
            verify(histories,never()).save(any());verify(histories,never()).flush();verify(users,never()).lockUserIdForPointUpdate(any());
            verifyNoInteractions(context.getBean(com.ngng.api.user.point.repository.PaymentSettlementRepository.class));
        }
        verify(details,never()).updateTransactionStatus(any(),any());verify(histories,never()).delete(any());
    }
    @Test void publicInitialPut2CannotDebitOrCreatePaidState() throws Exception {
        var transaction=jointFixture();mvc.perform(putStatus(2)).andExpect(status().isConflict());
        assertEquals(1L,transaction.getStatus().getId());verify(histories,never()).save(any());verifyNoInteractions(context.getBean(com.ngng.api.user.point.repository.PaymentSettlementRepository.class));
    }
    @Test void legacyExtraPut2IsNoopAfterAtomicPaymentAndPaymentRetryRemains409() throws Exception {
        jointFixture();mvc.perform(pay("USER","800","42")).andExpect(status().isOk());
        mvc.perform(putStatus(2)).andExpect(status().isOk()).andExpect(jsonPath("$.status.id").value(2));
        mvc.perform(pay("USER","800","42")).andExpect(status().isConflict());
        verify(histories).save(any());verify(histories).flush();verify(context.getBean(com.ngng.api.user.point.repository.PaymentSettlementRepository.class)).markPaid(88L,42L,7001L,999L,800L);
    }
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, PointController.class, PointHistoryService.class, AuthService.class, JacksonAutoConfiguration.class, TransactionController.class, TransactionDetailsService.class})
    static class IsolatedConfiguration implements WebMvcConfigurer {
        @Bean com.ngng.api.user.point.payment.PaymentVerificationGate gate() { return PaymentFixture.gateBindings(new long[]{88,42,7001,999,800}, new long[]{89,42,7001,999,1000}, new long[]{90,42,7001,999,1}); }
        @Bean com.ngng.api.user.point.repository.PaymentSettlementRepository settlements() { return PaymentFixture.settlements(); }
        private final ObjectMapper mapper;
        IsolatedConfiguration(ObjectMapper mapper) { this.mapper = mapper; }
        @Override public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
            converters.add(new MappingJackson2HttpMessageConverter(mapper));
        }
        @Bean @Primary AuthenticationConfiguration authenticationConfiguration() throws Exception {
            var configuration = mock(AuthenticationConfiguration.class);
            when(configuration.getAuthenticationManager()).thenReturn(mock(AuthenticationManager.class)); return configuration;
        }
        @Bean JwtTokenVerifier verifier() { return new JwtTokenVerifier(SECRET); }
        @Bean JwtTokenProvider provider() { return new JwtTokenProvider(SECRET); }
        @Bean CustomLogoutHandler logoutHandler() { return new CustomLogoutHandler(); }
        @Bean CustomLogoutSuccessHandler logoutSuccessHandler() { return new CustomLogoutSuccessHandler(); }
        @Bean ProductRepository products() { return mock(ProductRepository.class); }
        @Bean TransactionDetailsRepository details() { return mock(TransactionDetailsRepository.class); }
        @Bean TransactionStatusRepository statuses() { return mock(TransactionStatusRepository.class); }
        @Bean TransactionRequestService requests() { return mock(TransactionRequestService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean PointHistoryRepository histories() { return mock(PointHistoryRepository.class); }
    }
}
