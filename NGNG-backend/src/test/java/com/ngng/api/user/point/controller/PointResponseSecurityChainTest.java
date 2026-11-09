package com.ngng.api.user.point.controller;

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
class PointResponseSecurityChainTest {
    private static final String EMAIL = "rf008-fixture@example.invalid";
    private static final String SECRET = Encoders.BASE64URL.encode(
            "rf008-synthetic-key-only-64-bytes-abcdefghijklmnopqrstuvwxyz-123456".getBytes(StandardCharsets.UTF_8));
    private static final Set<String> POINT_KEYS = Set.of("id", "beforeCost", "addCost", "cost", "type", "typeDetail", "createdAt", "updatedAt");
    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mvc;
    private static ObjectMapper mapper;
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
        reset(users, histories);
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
    @AfterEach void cleared() { assertNull(SecurityContextHolder.getContext().getAuthentication()); }
    private PointHistory history(long id, long cost) {
        return PointHistory.builder().id(id).beforeCost(cost - 100).addCost(100L).cost(cost).type("충전")
                .typeDetail("Fixture 표시 문구").user(user)
                .createdAt(Timestamp.valueOf("2026-01-02 03:04:05.123456789"))
                .updatedAt(Timestamp.valueOf("2026-02-03 04:05:06.987654321")).build();
    }
    private static String token(String role) {
        return Jwts.builder().setSubject(EMAIL).claim("role", role).claim("type", "access")
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
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
    private JsonNode response(String operation, String role) throws Exception {
        var result = mvc.perform(operation(operation).header("Authorization", "Bearer " + token(role)))
                .andExpect(status().is(operation.equals("charge") ? 201 : 200)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }
    // Never print response/user/token values in assertions or console, even on a failure.
    private void assertResponse(JsonNode actual, PointHistory expected) throws Exception {
        ObjectNode entityJson = (ObjectNode) mapper.readTree(mapper.writeValueAsBytes(expected));
        assertNotNull(entityJson.get("user"));
        entityJson.remove("user");
        Set<String> actualKeys = new HashSet<>(); actual.fieldNames().forEachRemaining(actualKeys::add);
        assertEquals(POINT_KEYS, actualKeys, "exact response field set");
        for (String key : List.of("user", "password", "email", "address", "phoneNumber", "accountBank", "accountNumber", "role")) {
            assertTrue(actual.findValues(key).isEmpty(), "forbidden response key: " + key);
        }
        assertTrue(entityJson.equals(actual), "response differs from entity JSON after permitted user removal");
        for (String key : POINT_KEYS) assertTrue(actual.has(key), "missing point key: " + key);
    }
    static Stream<Arguments> roleOperations() {
        return Stream.of("USER", "ADMIN", "UNCONFIRMED_USER").flatMap(role ->
                Stream.of("latest", "all", "charge", "payment").map(operation -> Arguments.of(role, operation)));
    }
    @ParameterizedTest @MethodSource("roleOperations")
    void roleResponseMatrixPreservesValuesAndDatabaseIdentity(String role, String operation) throws Exception {
        JsonNode result = response(operation, role);
        verify(users).findUserByEmail(EMAIL);
        if (operation.equals("latest")) {
            assertResponse(result, latest); verify(histories).findLastByUserId(7001L);
        } else if (operation.equals("all")) {
            assertEquals(2, result.size()); assertResponse(result.get(0), latest); assertResponse(result.get(1), history(99L, 900L));
            verify(histories).findAllByUserId(7001L);
        } else {
            var saved = org.mockito.ArgumentCaptor.forClass(PointHistory.class);
            var order = inOrder(users, histories);
            order.verify(users).findUserByEmail(EMAIL);
            order.verify(users).lockUserIdForPointUpdate(7001L);
            order.verify(histories).findLatestCostForUpdate(7001L);
            order.verify(histories).save(saved.capture());
            PointHistory value = saved.getValue();
            assertSame(user, value.getUser()); assertEquals(1000L, value.getBeforeCost());
            assertEquals(operation.equals("charge") ? 200L : 1000L, value.getAddCost());
            assertEquals(operation.equals("charge") ? 1200L : 0L, value.getCost());
            assertEquals(operation.equals("charge") ? "충전" : "결제", value.getType());
            assertEquals(operation.equals("charge") ? "Fixture 유저가 200 금액 만큼 충전 하셨습니다." :
                    "Fixture 유저가 1000 금액 만큼 42상품을 구매 하셨습니다.", value.getTypeDetail());
            assertResponse(result, value);
        }
        if (operation.equals("latest") || operation.equals("all")) {
            verify(histories, never()).save(any()); verify(users, never()).lockUserIdForPointUpdate(any());
            verify(histories, never()).findLatestCostForUpdate(any());
        }
        verifyNoMoreInteractions(users, histories);
    }
    static Stream<Arguments> deniedOperations() {
        return Stream.of("missing", "malformed").flatMap(kind -> Stream.of("latest", "all", "charge", "payment")
                .map(operation -> Arguments.of(kind, operation)));
    }
    @ParameterizedTest @MethodSource("deniedOperations")
    void authenticationRefusalNeverReachesBusinessRepositories(String kind, String operation) throws Exception {
        var request = operation(operation);
        if (kind.equals("malformed")) request.header("Authorization", "Bearer synthetic-invalid");
        mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        verifyNoInteractions(users, histories);
    }
    @Test void actualBootMapperIsTheOnlyMvcJacksonMapper() {
        var converters = context.getBean(RequestMappingHandlerAdapter.class).getMessageConverters().stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance).map(MappingJackson2HttpMessageConverter.class::cast).toList();
        assertEquals(1, converters.size()); assertSame(mapper, converters.get(0).getObjectMapper());
    }
    @Test void emptyReadsKeep200EmptyBodyAndEmptyArray() throws Exception {
        when(histories.findLastByUserId(7001L)).thenReturn(Optional.empty());
        when(histories.findAllByUserId(7001L)).thenReturn(List.of());
        mvc.perform(operation("latest").header("Authorization", "Bearer " + token("USER")))
                .andExpect(status().isOk()).andExpect(content().string(""));
        assertTrue(response("all", "USER").isEmpty());
        verify(histories, never()).save(any()); verify(users, never()).lockUserIdForPointUpdate(any());
    }
    @Test void nullFieldsRetainKeysAndJsonNull() throws Exception {
        latest = PointHistory.builder().user(user).build();
        when(histories.findLastByUserId(7001L)).thenReturn(Optional.of(latest));
        JsonNode result = response("latest", "USER"); assertResponse(result, latest);
        for (String key : POINT_KEYS) assertTrue(result.has(key) && result.get(key).isNull(), "null field changed: " + key);
    }
    @Test void dtoMappingAndBootSerializationNeverAccessUser() throws Exception {
        PointHistory guarded = spy(history(102L, 1000L));
        doThrow(new AssertionError("user association accessed")).when(guarded).getUser();
        var dto = PointHistoryResponseDTO.from(guarded);
        assertSame(guarded.getCreatedAt(), dto.getCreatedAt());
        assertSame(guarded.getUpdatedAt(), dto.getUpdatedAt());
        JsonNode result = mapper.readTree(mapper.writeValueAsBytes(dto));
        assertResponse(result, latest);
        verify(guarded, never()).getUser();
        assertNull(PointHistoryResponseDTO.from(null));
    }
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, PointController.class, PointHistoryService.class, AuthService.class, JacksonAutoConfiguration.class})
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
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean PointHistoryRepository histories() { return mock(PointHistoryRepository.class); }
    }
}
