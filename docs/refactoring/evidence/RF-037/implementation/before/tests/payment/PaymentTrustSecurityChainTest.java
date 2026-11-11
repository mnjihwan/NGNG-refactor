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
class PaymentTrustSecurityChainTest {
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
    @AfterEach void cleared() {
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(context.getBean(TransactionRequestService.class), context.getBean(TransactionStatusRepository.class));
        verify(details, never()).save(any()); verify(details, never()).delete(any());
        verify(details, never()).updateTransactionStatus(any(), any());
        verify(products, never()).save(any()); verify(products, never()).delete(any());
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
    private MockHttpServletRequestBuilder operation(String operation) {
        return switch (operation) {
            case "latest" -> get("/points");
            case "all" -> get("/points/all");
            case "charge" -> post("/points").contentType("application/json").content("{\"addCost\":200,\"type\":\"충전\"}");
            case "payment" -> post("/points/payment").contentType("application/json").content("{\"paymentCost\":1000,\"payProductId\":42}");
            default -> throw new IllegalArgumentException(operation);
        };
    }
    private MockHttpServletRequestBuilder pay(String role, String amount, String product) {
        return post("/points/payment").contentType("application/json")
                .content("{\"paymentCost\":" + amount + ",\"payProductId\":" + product + "}")
                .header("Authorization", "Bearer " + token(role));
    }
    private void noBalance() {
        verify(users, never()).lockUserIdForPointUpdate(any());
        verifyNoInteractions(histories);
    }
    private void refused(int code, String amount, String product) throws Exception {
        mvc.perform(pay("USER", amount, product)).andExpect(status().is(code))
                .andExpect(content().string("")).andExpect(header().doesNotExist("Authorization"));
        verify(histories, never()).save(any());
    }
    @ParameterizedTest @CsvSource({"USER,7001", "USER,999", "USER,888", "ADMIN,7001", "ADMIN,999", "ADMIN,888",
            "UNCONFIRMED_USER,7001", "UNCONFIRMED_USER,999", "UNCONFIRMED_USER,888"})
    void onlyRecordedBuyerMayPayRegardlessOfRoleAndCurrentProductOwner(String role, long caller) throws Exception {
        user.setUserId(caller);
        var result = mvc.perform(pay(role, "800", "42"));
        if (caller == 7001) {
            result.andExpect(status().isOk()).andExpect(jsonPath("$.cost").value(200))
                    .andExpect(jsonPath("$.addCost").value(800)).andExpect(jsonPath("$.user").doesNotExist());
            verify(histories).save(any());
        } else {
            result.andExpect(status().isForbidden()).andExpect(content().string("")); noBalance();
        }
        verify(users).findUserByEmail(EMAIL);
        verify(products).lockTransactionProduct(42L); verify(details).lockPaymentTransactions(42L);
        verifyNoMoreInteractions(products, details);
    }
    @ParameterizedTest @ValueSource(strings = {"null", "0", "-1", "-9223372036854775808", "\"bad\"", "9223372036854775808"})
    void invalidProductIs400BeforeServiceDbReads(String id) throws Exception {
        refused(400, "800", id); verifyNoInteractions(users, products, details, histories);
    }
    @ParameterizedTest @ValueSource(strings = {"null", "0", "-1", "-9223372036854775808", "\"bad\"", "9223372036854775808"})
    void invalidAmountIs400BeforeServiceDbReads(String amount) throws Exception {
        refused(400, amount, "42"); verifyNoInteractions(users, products, details, histories);
    }
    @Test void missingProductFieldIs400() throws Exception {
        mvc.perform(post("/points/payment").contentType("application/json").content("{\"paymentCost\":800}")
                .header("Authorization", "Bearer " + token("USER"))).andExpect(status().isBadRequest());
        verifyNoInteractions(users, products, details, histories);
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "malformed", "tampered", "expired"})
    void invalidCredentialIs401BeforeAllBusinessRepositories(String kind) throws Exception {
        var request = post("/points/payment").contentType("application/json").content("{\"paymentCost\":800,\"payProductId\":42}");
        String access = token("USER");
        if (kind.equals("malformed")) access = "synthetic-broken";
        if (kind.equals("expired")) access = token("access", "USER", -3600000);
        if (kind.equals("tampered")) {
            int signature = access.lastIndexOf('.') + 1;
            access = access.substring(0, signature) + (access.charAt(signature) == 'a' ? 'b' : 'a') + access.substring(signature + 1);
        }
        if (!kind.equals("missing")) request.header("Authorization", "Bearer " + access);
        mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Authorization"));
        verifyNoInteractions(users, products, details, histories);
    }
    @ParameterizedTest @ValueSource(strings = {"absent", "nullId"})
    void unresolvedDatabaseUserIs401BeforeProductLock(String missing) throws Exception {
        doReturn(missing.equals("absent") ? Optional.empty() : Optional.of(new User())).when(users).findUserByEmail(EMAIL);
        refused(401, "800", "42"); verifyNoInteractions(products, details, histories); noBalance();
    }
    @ParameterizedTest @ValueSource(strings = {"product", "transaction"})
    void missingProductOrTransactionIs404BeforeBalance(String missing) throws Exception {
        if (missing.equals("product")) when(products.lockTransactionProduct(42L)).thenReturn(Optional.empty());
        else when(details.lockPaymentTransactions(42L)).thenReturn(List.of());
        refused(404, "800", "42"); noBalance();
        if (missing.equals("product")) verifyNoInteractions(details);
    }
    @ParameterizedTest @ValueSource(strings = {"multiple", "transactionId", "consumerId", "priceNull", "priceZero", "priceNegative", "body"})
    void ambiguousIncompleteOrWrongAmountIs409BeforeBalance(String condition) throws Exception {
        List<TransactionDetailsRepository.PaymentTransaction> rows = switch (condition) {
            case "multiple" -> List.of(new PaymentFixture.TransactionRow(88L, 7001L, 800L), new PaymentFixture.TransactionRow(89L, 7001L, 800L));
            case "transactionId" -> List.of(new PaymentFixture.TransactionRow(null, 7001L, 800L));
            case "consumerId" -> List.of(new PaymentFixture.TransactionRow(88L, null, 800L));
            case "priceNull" -> List.of(new PaymentFixture.TransactionRow(88L, 7001L, null));
            case "priceZero" -> List.of(new PaymentFixture.TransactionRow(88L, 7001L, 0L));
            case "priceNegative" -> List.of(new PaymentFixture.TransactionRow(88L, 7001L, -1L));
            default -> List.of(new PaymentFixture.TransactionRow(88L, 7001L, 800L));
        };
        when(details.lockPaymentTransactions(42L)).thenReturn(rows);
        refused(409, condition.equals("body") ? "1" : "800", "42"); noBalance();
    }
    @Test void exactEightFieldSuccessResponseAndEqualBalanceRemain200() throws Exception {
        PaymentFixture.allowPayment(products, details, 7001L, 1000L);
        var result = mvc.perform(pay("USER", "1000", "42")).andExpect(status().isOk()).andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsByteArray());
        Set<String> keys = new HashSet<>(); body.fieldNames().forEachRemaining(keys::add);
        assertEquals(POINT_KEYS, keys);
        assertEquals(103, body.get("id").longValue()); assertEquals(1000, body.get("beforeCost").longValue());
        assertEquals(1000, body.get("addCost").longValue()); assertEquals(0, body.get("cost").longValue());
        assertEquals("결제", body.get("type").textValue());
        assertEquals("Fixture 유저가 1000 금액 만큼 42상품을 구매 하셨습니다.", body.get("typeDetail").textValue());
        assertNotNull(body.get("createdAt")); assertNotNull(body.get("updatedAt"));
    }
    @Test void insufficientBalanceKeeps400AndNoAppend() throws Exception {
        when(histories.findLatestCostForUpdate(7001L)).thenReturn(Optional.of(799L));
        refused(400, "800", "42"); verify(histories).findLatestCostForUpdate(7001L);
    }
    @ParameterizedTest @ValueSource(longs = {7001, 888})
    void normalRefreshFallbackKeepsBuyerPolicyAndAccessHeader(long buyer) throws Exception {
        user.setUserId(buyer);
        mvc.perform(post("/points/payment").contentType("application/json")
                .content("{\"paymentCost\":800,\"payProductId\":42}")
                .cookie(new Cookie("refreshToken", token("refresh", "USER", 3600000))))
                .andExpect(status().is(buyer == 7001 ? 200 : 403))
                .andExpect(header().string("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")));
        if (buyer != 7001) noBalance();
    }
    @Test void dbErrorIsNotSwallowedByTokenFilter() {
        var error = new org.springframework.dao.DataAccessResourceFailureException("synthetic-unavailable");
        doThrow(error).when(users).findUserByEmail(EMAIL);
        Exception thrown = assertThrows(Exception.class, () -> mvc.perform(pay("USER", "800", "42")));
        assertSame(error, thrown.getCause()); verifyNoInteractions(products, details, histories);
    }
    @ParameterizedTest @CsvSource({"1,1", "800,2", "800,3", "800,4", "800,5", "800,0"})
    void directPostStoredPriceRemainsPayableWithoutAcceptanceAndExposesDeferredLowPriceRisk(long storedPrice, long status) throws Exception {
        Product product = Product.builder().productId(42L).price(10000L).forSale(true).visible(false)
                .user(User.builder().userId(999L).name("seller").build())
                .category(Category.builder().categoryId(1L).categoryName("fixture").build())
                .status(Status.builder().statusId(1L).statusName("used").build())
                .thumbnail(Thumbnail.builder().thumbnailId(1L).thumbnailUrl("synthetic-thumb").build())
                .productImages(List.of()).tags(List.of()).build();
        when(products.findById(42L)).thenReturn(Optional.of(product));
        java.util.concurrent.atomic.AtomicReference<TransactionDetails> stored = new java.util.concurrent.atomic.AtomicReference<>();
        when(details.save(any())).thenAnswer(call -> {
            TransactionDetails value = call.getArgument(0); value.setId(88L); value = spy(value); stored.set(value); return value;
        });
        mvc.perform(post("/transaction").contentType("application/json")
                .content("{\"productId\":42,\"buyerId\":7001,\"price\":" + storedPrice + "}")
                .header("Authorization", "Bearer " + token("USER")))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/transaction/88"));
        // Existing direct-create authorization is deliberately deferred; this is a known-risk characterization.
        TransactionDetails transaction = stored.get();
        transaction.setStatus(status == 0 ? null : new TransactionStatus(status)); transaction.setSeller(null); product.setPrice(20000L);
        when(details.lockPaymentTransactions(42L)).thenAnswer(call -> List.of(new PaymentFixture.TransactionRow(
                transaction.getId(), transaction.getConsumer().getUserId(), transaction.getPrice())));
        clearInvocations(products, details, histories, users, transaction);
        mvc.perform(pay("USER", Long.toString(storedPrice), "42")).andExpect(status().isOk())
                .andExpect(jsonPath("$.cost").value(1000 - storedPrice));
        verify(products).lockTransactionProduct(42L); verifyNoMoreInteractions(products);
        verify(details).lockPaymentTransactions(42L); verifyNoMoreInteractions(details);
        verify(histories).save(any()); if (status == 0) assertNull(transaction.getStatus()); else assertEquals(status, transaction.getStatus().getId());
        verify(transaction, never()).setStatus(any());
        assertNull(transaction.getSeller()); assertTrue(product.getForSale()); assertFalse(product.getVisible());
        verifyNoInteractions(context.getBean(TransactionRequestService.class), context.getBean(TransactionStatusRepository.class));
    }
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, PointController.class, PointHistoryService.class, AuthService.class, JacksonAutoConfiguration.class, TransactionController.class, TransactionDetailsService.class})
    static class IsolatedConfiguration implements WebMvcConfigurer {
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
