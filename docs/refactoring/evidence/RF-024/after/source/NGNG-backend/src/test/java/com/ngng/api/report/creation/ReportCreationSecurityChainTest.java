package com.ngng.api.report.creation;

import com.ngng.api.global.security.config.SecurityConfig;
import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.global.security.handler.CustomLogoutHandler;
import com.ngng.api.global.security.handler.CustomLogoutSuccessHandler;
import com.ngng.api.global.security.jwt.util.JwtTokenProvider;
import com.ngng.api.global.security.jwt.util.JwtTokenVerifier;
import com.ngng.api.report.report.controller.*;
import com.ngng.api.report.report.entity.*;
import com.ngng.api.report.report.repository.*;
import com.ngng.api.report.report.service.ReportServiceImpl;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Explicit real MVC/security/JWT/AuthService/service; only repositories are doubles. No Boot or external beans. */
class ReportCreationSecurityChainTest {
    static final String EMAIL = "rf024@example.test";
    static final String SECRET = Encoders.BASE64URL.encode(
            "rf024-synthetic-key-only-64-bytes-abcdefghijklmnopqrstuvwxyz-123456".getBytes(StandardCharsets.UTF_8));
    static AnnotationConfigWebApplicationContext context;
    static MockMvc mvc;
    ReportRepository reports; ReportTypeRepository types; ReportImageRepository images; UserRepository users;
    com.ngng.api.penalty.repository.PenaltyRepository penalties;
    com.ngng.api.penalty.repository.PenaltyLevelRepository levels;
    com.ngng.api.product.product.repository.ProductRepository products;
    com.ngng.api.product.product.service.ProductCacheInvalidator cache;
    boolean penaltyRegression;
    @BeforeAll static void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "rf024-only", Map.of("jwt.expiration.access", "3600000", "jwt.expiration.refresh", "7200000")));
        context.setServletContext(new MockServletContext()); context.register(Config.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterAll static void stop() { context.close(); }
    @BeforeEach void setup() {
        reports = context.getBean(ReportRepository.class); types = context.getBean(ReportTypeRepository.class);
        images = context.getBean(ReportImageRepository.class); users = context.getBean(UserRepository.class);
        penalties = context.getBean(com.ngng.api.penalty.repository.PenaltyRepository.class);
        levels = context.getBean(com.ngng.api.penalty.repository.PenaltyLevelRepository.class);
        products = context.getBean(com.ngng.api.product.product.repository.ProductRepository.class);
        cache = context.getBean(com.ngng.api.product.product.service.ProductCacheInvalidator.class);
        reset(reports, types, images, users, penalties, levels, products, cache); SecurityContextHolder.clearContext();
        penaltyRegression = false;
        when(users.findUserByEmail(EMAIL)).thenReturn(Optional.of(User.builder().userId(1000L).build()));
        when(types.findById(1L)).thenReturn(Optional.of(ReportType.builder().reportTypeId(1L).reportType("synthetic-type").build()));
        when(reports.save(any())).thenAnswer(call -> { Report r = call.getArgument(0); r.setReportId(88L); return r; });
    }
    @AfterEach void clear() {
        assertNull(SecurityContextHolder.getContext().getAuthentication()); verifyNoInteractions(images);
        if (!penaltyRegression) verifyNoInteractions(penalties, levels, products, cache);
    }
    static String token(String type, String role, long seconds) {
        return Jwts.builder().setSubject(EMAIL).claim("role", role).claim("type", type)
                .setExpiration(new Date(System.currentTimeMillis() + seconds * 1000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(SECRET))).compact();
    }
    static String body(String reporter, String state) {
        return "{\"reportTypeId\":1,\"reportContents\":\"synthetic-report\",\"userId\":2000,\"productId\":7,\"privateChatId\":9"
                + (reporter == null ? "" : ",\"reporterId\":" + reporter) + (state == null ? "" : ",\"isReport\":" + state) + "}";
    }
    @ParameterizedTest @ValueSource(strings = {"USER", "ADMIN", "UNCONFIRMED_USER"})
    void allowedCallerIdIsResolvedFromEmailAndNullStateAlwaysSentToRepository(String role) throws Exception {
        when(users.findUserByEmail(EMAIL)).thenAnswer(call -> {
            var principal = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            assertNull(principal.getUser().getUserId()); assertEquals(EMAIL, principal.getUser().getEmail());
            return Optional.of(User.builder().userId(1000L).build());
        });
        for (String reporter : Arrays.asList(null, "null", "1000")) {
            for (String state : Arrays.asList(null, "null")) {
                mvc.perform(post("/reports").header("Authorization", "Bearer " + token("access", role, 3600))
                        .contentType("application/json").content(body(reporter, state)))
                        .andExpect(status().isCreated()).andExpect(jsonPath("$.reportId").value(88))
                        .andExpect(jsonPath("$.isReport").isEmpty()).andExpect(jsonPath("$.productId").value(7))
                        .andExpect(jsonPath("$.privateChatId").value(9)).andExpect(header().doesNotExist("Authorization"));
            }
        }
        var saved = org.mockito.ArgumentCaptor.forClass(Report.class); verify(reports, times(6)).save(saved.capture());
        for (Report report : saved.getAllValues()) {
            assertEquals(1000L, report.getReporterId()); assertNull(report.getIsReport());
            assertEquals(2000L, report.getUserId()); assertEquals(7L, report.getProductId());
            assertEquals(9L, report.getPrivateChatId()); assertEquals("synthetic-report", report.getReportContents());
        }
        verify(users, times(6)).findUserByEmail(EMAIL); verify(types, times(6)).findById(1L);
    }
    @ParameterizedTest @ValueSource(strings = {"USER", "ADMIN", "UNCONFIRMED_USER"})
    void impersonationHasPriorityOverStateAndDoesNotLookupTypeOrSave(String role) throws Exception {
        for (String state : Arrays.asList(null, "null", "true", "false")) {
            mvc.perform(post("/reports").header("Authorization", "Bearer " + token("access", role, 3600))
                    .contentType("application/json").content(body("9000", state)))
                    .andExpect(status().isForbidden()).andExpect(content().string(""));
        }
        verifyNoInteractions(reports, types);
    }
    @ParameterizedTest @ValueSource(strings = {"USER", "ADMIN", "UNCONFIRMED_USER"})
    void bothBooleanStatesAre400ForOwnOrOmittedReporterBeforeTargetAccess(String role) throws Exception {
        for (String reporter : Arrays.asList(null, "null", "1000")) {
            for (String state : List.of("true", "false")) {
                mvc.perform(post("/reports").header("Authorization", "Bearer " + token("access", role, 3600))
                        .contentType("application/json").content(body(reporter, state)))
                        .andExpect(status().isBadRequest()).andExpect(content().string(""));
            }
        }
        verifyNoInteractions(reports, types);
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "malformed", "tampered", "expired", "wrongType"})
    void invalidCredentialsWinOverSimultaneousForgeryAndNeverReachRepositories(String credentials) throws Exception {
        var request = post("/reports").contentType("application/json").content(body("9000", "true"));
        String access = token("access", "USER", 3600);
        if (credentials.equals("malformed")) access = "broken";
        if (credentials.equals("expired")) access = token("access", "USER", -3600);
        if (credentials.equals("wrongType")) access = token("refresh", "USER", 3600);
        if (credentials.equals("tampered")) {
            int signature = access.lastIndexOf('.') + 1;
            access = access.substring(0, signature) + (access.charAt(signature) == 'a' ? 'b' : 'a') + access.substring(signature + 1);
        }
        if (!credentials.equals("missing")) request.header("Authorization", "Bearer " + access);
        mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Authorization"));
        verifyNoInteractions(reports, types, users);
    }
    @ParameterizedTest @ValueSource(strings = {"absent", "nullId"})
    void unresolvedDbCallerIs401BeforeBothInputGuards(String condition) throws Exception {
        when(users.findUserByEmail(EMAIL)).thenReturn(condition.equals("absent") ? Optional.empty() : Optional.of(User.builder().build()));
        mvc.perform(post("/reports").header("Authorization", "Bearer " + token("access", "USER", 3600))
                .contentType("application/json").content(body("9000", "true"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(reports, types);
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "expired"})
    void legitimateRefreshPreservesCreationAndForgeryRejection(String access) throws Exception {
        for (String reporter : List.of("1000", "9000")) {
            var request = post("/reports").contentType("application/json").content(body(reporter, null))
                    .cookie(new Cookie("refreshToken", token("refresh", "USER", 3600)));
            if (access.equals("expired")) request.header("Authorization", "Bearer " + token("access", "USER", -3600));
            mvc.perform(request).andExpect(status().is(reporter.equals("1000") ? 201 : 403))
                    .andExpect(header().string("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")));
        }
        verify(reports).save(any()); verify(types).findById(1L);
    }
    @ParameterizedTest @ValueSource(strings = {"USER", "ADMIN", "UNCONFIRMED_USER"})
    void adminPatchRetainsOnlyAdminProcessedTransition(String role) throws Exception {
        Report report = Report.builder().reportId(88L).reporterId(2000L).userId(3000L).build();
        when(reports.findById(88L)).thenReturn(Optional.of(report));
        mvc.perform(patch("/admin/reports/88").header("Authorization", "Bearer " + token("access", role, 3600)))
                .andExpect(status().is(role.equals("ADMIN") ? 200 : 403));
        if (role.equals("ADMIN")) { assertTrue(report.getIsReport()); verify(reports).save(report); }
        else { assertNull(report.getIsReport()); verifyNoInteractions(reports); }
        verifyNoInteractions(users, types);
    }
    @ParameterizedTest @ValueSource(strings = {"USER", "ADMIN", "UNCONFIRMED_USER"})
    void separateAdminPenaltyStillSavesPenaltyProcessesReportAndDisablesProduct(String role) throws Exception {
        penaltyRegression = true;
        Report report = Report.builder().reportId(88L).reporterId(2000L).productId(7L).build();
        var product = com.ngng.api.product.product.entity.Product.builder().productId(7L).forSale(true).build();
        when(reports.findById(88L)).thenReturn(Optional.of(report));
        when(products.findById(7L)).thenReturn(Optional.of(product));
        when(levels.findById(1L)).thenReturn(Optional.of(com.ngng.api.penalty.entity.PenaltyLevel.builder()
                .penaltyLevelId(1L).penaltyLevelName("synthetic-level").penaltyLevelDays(1L).build()));
        when(penalties.save(any())).thenAnswer(call -> call.getArgument(0));
        mvc.perform(post("/admin/penalties").header("Authorization", "Bearer " + token("access", role, 3600))
                .contentType("application/json").content("{\"penaltyLevelId\":1,\"reportId\":88,\"reporterId\":2000,\"userId\":3000,\"reason\":\"synthetic\"}"))
                .andExpect(status().is(role.equals("ADMIN") ? 201 : 403));
        if (role.equals("ADMIN")) {
            assertTrue(report.getIsReport()); assertFalse(product.getForSale()); verify(reports).save(report);
            verify(penalties).save(any()); verify(products).save(product); verify(cache).invalidate(7L);
        } else {
            assertNull(report.getIsReport()); assertTrue(product.getForSale()); verifyNoInteractions(reports, penalties, levels, products, cache);
        }
        verifyNoInteractions(users, types);
    }
    @Test void malformedJsonKeepsBinding400AndDoesNotLookupCaller() throws Exception {
        mvc.perform(post("/reports").header("Authorization", "Bearer " + token("access", "USER", 3600))
                .contentType("application/json").content("{")).andExpect(status().isBadRequest());
        verifyNoInteractions(reports, types, users);
    }
    @Test void missingTypeStillUsesExistingExceptionAfterIdentityChecks() {
        when(types.findById(1L)).thenReturn(Optional.empty());
        Exception thrown = assertThrows(Exception.class, () -> mvc.perform(post("/reports")
                .header("Authorization", "Bearer " + token("access", "USER", 3600))
                .contentType("application/json").content(body("1000", null))));
        assertInstanceOf(NoSuchElementException.class, thrown.getCause()); verify(reports, never()).save(any());
    }
    @Test void databaseFailuresAreNotDisguisedAsAuthenticationErrors() {
        var failure = new org.springframework.dao.DataAccessResourceFailureException("synthetic-unavailable");
        when(users.findUserByEmail(EMAIL)).thenThrow(failure);
        Exception thrown = assertThrows(Exception.class, () -> mvc.perform(post("/reports")
                .header("Authorization", "Bearer " + token("access", "USER", 3600))
                .contentType("application/json").content(body("1000", null))));
        assertSame(failure, thrown.getCause()); verifyNoInteractions(reports, types);
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "string", "anonymous", "unauthenticated", "nullUser", "nullEmail", "blankEmail"})
    void directServiceEntryRejectsInvalidPrincipalWithoutAuthServiceCastFailure(String condition) {
        var valid = new CustomUserDetails(User.builder().email(EMAIL).build());
        org.springframework.security.core.Authentication authentication = switch (condition) {
            case "missing" -> null;
            case "string" -> new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("unexpected", null, List.of());
            case "anonymous" -> new org.springframework.security.authentication.AnonymousAuthenticationToken("synthetic", "anonymousUser", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ANONYMOUS")));
            case "unauthenticated" -> new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(valid, null);
            case "nullUser" -> new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(new CustomUserDetails(null), null, List.of());
            case "nullEmail" -> new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(new CustomUserDetails(User.builder().build()), null, List.of());
            default -> new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(new CustomUserDetails(User.builder().email(" ").build()), null, List.of());
        };
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            var failure = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> context.getBean(ReportServiceImpl.class)
                    .save(com.ngng.api.report.report.dto.CreateReportRequestDTO.builder().reporterId(9000L).isReport(true).build()));
            assertEquals(401, failure.getStatusCode().value()); verifyNoInteractions(reports, types, users);
        } finally { SecurityContextHolder.clearContext(); }
    }
    @Test void serviceNeverTrustsPrincipalIdOverDatabaseIdentity() {
        var principal = new CustomUserDetails(User.builder().userId(9000L).email(EMAIL).build());
        SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null, List.of()));
        try {
            context.getBean(ReportServiceImpl.class).save(com.ngng.api.report.report.dto.CreateReportRequestDTO.builder().reportTypeId(1L).build());
            verify(reports).save(argThat(r -> r.getReporterId().equals(1000L) && r.getIsReport() == null));
        } finally { SecurityContextHolder.clearContext(); }
    }
    @Configuration @EnableWebMvc @EnableWebSecurity @Import(SecurityConfig.class)
    static class Config {
        @Bean @Primary AuthenticationConfiguration authenticationConfiguration() throws Exception {
            var c = mock(AuthenticationConfiguration.class); when(c.getAuthenticationManager()).thenReturn(mock(AuthenticationManager.class)); return c;
        }
        @Bean JwtTokenVerifier verifier() { return new JwtTokenVerifier(SECRET); }
        @Bean JwtTokenProvider provider() { return new JwtTokenProvider(SECRET); }
        @Bean CustomLogoutHandler logoutHandler() { return new CustomLogoutHandler(); }
        @Bean CustomLogoutSuccessHandler logoutSuccessHandler() { return new CustomLogoutSuccessHandler(); }
        @Bean ReportRepository reports() { return mock(ReportRepository.class); }
        @Bean ReportTypeRepository types() { return mock(ReportTypeRepository.class); }
        @Bean ReportImageRepository images() { return mock(ReportImageRepository.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean AuthService auth(UserRepository users) { return new AuthService(users); }
        @Bean ReportServiceImpl service(ReportRepository r, ReportTypeRepository t, ReportImageRepository i, AuthService a) { return new ReportServiceImpl(r,t,i,a); }
        @Bean CreateReportController create(ReportServiceImpl s) { return new CreateReportController(s); }
        @Bean AdminReportController admin(ReportServiceImpl s) { return new AdminReportController(s); }
        @Bean com.ngng.api.penalty.repository.PenaltyRepository penalties() { return mock(com.ngng.api.penalty.repository.PenaltyRepository.class); }
        @Bean com.ngng.api.penalty.repository.PenaltyLevelRepository levels() { return mock(com.ngng.api.penalty.repository.PenaltyLevelRepository.class); }
        @Bean com.ngng.api.product.product.repository.ProductRepository products() { return mock(com.ngng.api.product.product.repository.ProductRepository.class); }
        @Bean com.ngng.api.product.product.service.ProductCacheInvalidator cache() { return mock(com.ngng.api.product.product.service.ProductCacheInvalidator.class); }
        @Bean com.ngng.api.penalty.service.PenaltyTran penalty(ReportRepository r, ReportServiceImpl s,
                com.ngng.api.penalty.repository.PenaltyRepository p, com.ngng.api.penalty.repository.PenaltyLevelRepository l,
                com.ngng.api.product.product.repository.ProductRepository products, com.ngng.api.product.product.service.ProductCacheInvalidator cache) {
            return new com.ngng.api.penalty.service.PenaltyTran(p, l, r, s, products, cache);
        }
        @Bean com.ngng.api.penalty.controller.AdminPenaltyController penaltyController(com.ngng.api.penalty.service.PenaltyTran p) {
            return new com.ngng.api.penalty.controller.AdminPenaltyController(mock(com.ngng.api.penalty.service.PenaltyService.class), p);
        }
    }
}
