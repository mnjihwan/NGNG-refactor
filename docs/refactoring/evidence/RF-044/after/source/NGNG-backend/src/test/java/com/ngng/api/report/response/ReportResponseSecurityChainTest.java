package com.ngng.api.report.response;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ngng.api.global.security.config.SecurityConfig;
import com.ngng.api.global.security.handler.*;
import com.ngng.api.global.security.jwt.util.*;
import com.ngng.api.report.report.dto.*;
import com.ngng.api.report.report.controller.*;
import com.ngng.api.report.report.entity.*;
import com.ngng.api.report.report.repository.*;
import com.ngng.api.report.report.service.ReportServiceImpl;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.entity.Role;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.http.converter.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual Boot Jackson, MVC, security/JWT/AuthService/service; repository doubles and mocked login manager.
 * No component scan, database, transaction manager, HTTP server or external upload/provider is started. */
class ReportResponseSecurityChainTest {
    static final String EMAIL = "rf044@example.test";
    static final String SECRET = Encoders.BASE64URL.encode(
            "rf044-synthetic-only-signing-key-abcdefghijklmnopqrstuvwxyz-123456".getBytes(StandardCharsets.UTF_8));
    static final Set<String> CREATE = Set.of("reportId", "reportContents", "reportType", "isReport", "createdAt", "updatedAt", "productId", "privateChatId");
    static final Set<String> LIST = Set.of("reportId", "reportContents", "reportType", "reporter", "user", "isReport", "createdAt", "productId", "privateChatId", "visible");
    static final Set<String> DETAIL = Set.of("reportId", "reportContents", "reportType", "reporter", "user", "isReport", "createdAt", "updatedAt", "productId", "privateChatId", "visible", "reportImages");
    static final Set<String> USER = Set.of("userId", "name", "nickname");
    static final Set<String> FORBIDDEN = Set.of("email", "password", "address", "phoneNumber", "accountBank", "accountNumber", "role", "roleId", "roleType");
    static AnnotationConfigWebApplicationContext context;
    static ObjectMapper mapper;
    static MockMvc mvc;
    ReportRepository reports;
    ReportTypeRepository types;
    ReportImageRepository images;
    UserRepository users;
    Report report;

    @BeforeAll static void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "rf044-only", Map.of("jwt.expiration.access", "3600000", "jwt.expiration.refresh", "7200000")));
        context.setServletContext(new MockServletContext()); context.register(Config.class); context.refresh();
        mapper = context.getBean(ObjectMapper.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterAll static void stop() { context.close(); }
    @BeforeEach void setup() {
        reports = context.getBean(ReportRepository.class); types = context.getBean(ReportTypeRepository.class);
        images = context.getBean(ReportImageRepository.class); users = context.getBean(UserRepository.class);
        reset(reports, types, images, users); SecurityContextHolder.clearContext();
        report = fixture();
        when(users.findUserByEmail(anyString())).thenReturn(Optional.of(User.builder().userId(1000L).build()));
        when(types.findById(1L)).thenReturn(Optional.of(report.getReportType()));
        when(reports.findById(88L)).thenAnswer(call -> Optional.of(report));
        when(reports.save(any())).thenAnswer(call -> report);
        when(reports.findAll(any(Pageable.class))).thenAnswer(call -> new PageImpl<>(List.of(report), call.getArgument(0), 11));
        when(reports.findByIsReport(isNull(), any(Pageable.class))).thenAnswer(call -> new PageImpl<>(List.of(report), call.getArgument(1), 11));
    }
    @AfterEach void clear() { assertNull(SecurityContextHolder.getContext().getAuthentication()); verifyNoInteractions(images); }
    static Timestamp stamp() { return Timestamp.valueOf("2026-01-02 03:04:05.123456789"); }
    static User person(long id) {
        return User.builder().userId(id).name("fixture-name-" + id).nickname("fixture-nickname-" + id)
                .email("synthetic-sensitive-email").password("synthetic-sensitive-password")
                .address("synthetic-sensitive-address").phoneNumber("synthetic-sensitive-phone")
                .accountBank("synthetic-sensitive-bank").accountNumber("synthetic-sensitive-account")
                .visible(true).createdAt(stamp()).updatedAt(stamp()).role(Role.builder().roleId(1L).roleType("USER").build()).build();
    }
    static Report fixture() {
        Report value = Report.builder().reportId(88L).reportContents("fixture-contents").reporterId(1000L).userId(2000L)
                .reporter(person(1000L)).user(person(2000L)).isReport(null).createdAt(stamp()).updatedAt(stamp())
                .productId(7L).privateChatId(9L).visible(false)
                .reportType(ReportType.builder().reportTypeId(1L).reportType("fixture-type").build()).build();
        value.getReportImages().add(ReportImage.builder().reportImageId(4L).imageUrl("https://example.test/fixture.png")
                .contentType("IMAGE").createdAt(stamp()).updatedAt(null).report(value).build());
        return value;
    }
    static String token(String type, String role, String email, long seconds) {
        return Jwts.builder().setSubject(email).claim("role", role).claim("type", type)
                .setExpiration(new Date(System.currentTimeMillis() + seconds * 1000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(SECRET))).compact();
    }
    static MockHttpServletRequestBuilder request(String op) {
        return switch (op) {
            case "create" -> post("/reports").contentType("application/json").content("{\"reportContents\":\"fixture-contents\",\"reportTypeId\":1,\"reporterId\":1000,\"userId\":2000,\"productId\":7,\"privateChatId\":9}");
            case "list" -> get("/admin/reports");
            case "detail" -> get("/admin/reports/88");
            case "patch" -> patch("/admin/reports/88");
            default -> throw new IllegalArgumentException(op);
        };
    }
    JsonNode response(String op, String role) throws Exception {
        var result = mvc.perform(request(op).header("Authorization", "Bearer " + token("access", role, EMAIL, 3600)))
                .andExpect(status().is(op.equals("create") ? 201 : 200)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }
    static Set<String> keys(JsonNode tree) { Set<String> keys = new HashSet<>(); tree.fieldNames().forEachRemaining(keys::add); return keys; }
    // Use boolean assertions for payload comparisons so failure output never dumps sensitive fixture values or JWTs.
    void contract(String op, JsonNode tree) throws Exception {
        JsonNode item = op.equals("list") ? tree.get("content").get(0) : tree;
        Set<String> expected = new HashSet<>(op.equals("create") ? CREATE : op.equals("list") ? LIST : DETAIL);
        assertEquals(expected, keys(item));
        for (String forbidden : FORBIDDEN) assertTrue(tree.findValues(forbidden).isEmpty(), "forbidden key: " + forbidden);
        if (op.equals("create")) { assertFalse(item.has("reporter")); assertFalse(item.has("user")); }
        else for (String association : List.of("reporter", "user")) {
            assertEquals(USER, keys(item.get(association))); assertTrue(item.get(association).get("userId").isIntegralNumber());
        }
        assertEquals(Set.of("reportTypeId", "reportType"), keys(item.get("reportType")));
        assertTrue(item.get("reportId").isIntegralNumber()); assertTrue(item.get("productId").isIntegralNumber());
        assertTrue(mapper.readTree(mapper.writeValueAsBytes(stamp())).equals(item.get("createdAt")), "timestamp JSON changed");
        assertEquals(op.equals("patch"), item.get("isReport").asBoolean());
        if (op.equals("detail") || op.equals("patch")) {
            assertTrue(item.get("visible").isNull());
            assertEquals(Set.of("reportImageId", "imageUrl", "contentType", "createdAt", "updatedAt"), keys(item.get("reportImages").get(0)));
            assertTrue(item.get("reportImages").get(0).get("updatedAt").isNull());
        }
        Path baseline = Path.of("../docs/refactoring/evidence/RF-044/before/" + op + ".json");
        JsonNode original = mapper.readTree(Files.readString(baseline));
        ObjectNode originalItem = (ObjectNode) (op.equals("list") ? original.get("content").get(0) : original);
        if (op.equals("create")) originalItem.remove(List.of("reporter", "user"));
        else for (String association : List.of("reporter", "user")) ((ObjectNode) originalItem.get(association)).retain(USER);
        assertTrue(original.equals(tree), "only approved response projection may differ from retained before JSON");
    }
    @ParameterizedTest @ValueSource(strings = {"USER", "ADMIN", "UNCONFIRMED_USER"})
    void creationUsesDatabaseIdentityAndEightFields(String role) throws Exception {
        JsonNode tree = response("create", role); contract("create", tree);
        verify(users).findUserByEmail(EMAIL); verify(types).findById(1L);
        verify(reports).save(argThat(r -> r.getReporterId().equals(1000L) && r.getUserId().equals(2000L) && r.getIsReport() == null));
    }
    @ParameterizedTest @ValueSource(strings = {"list", "detail", "patch"})
    void adminResponsesRetainOnlyApprovedUserFields(String op) throws Exception {
        JsonNode tree = response(op, "ADMIN"); contract(op, tree);
        verifyNoInteractions(users, types);
        if (op.equals("patch")) { assertTrue(report.getIsReport()); verify(reports, times(2)).findById(88L); verify(reports).save(report); }
        else verify(reports, never()).save(any());
    }
    @Test void ordinaryCreationBuilderCanKeepBothAssociationsNull() throws Exception {
        when(reports.save(any())).thenAnswer(call -> { Report saved = call.getArgument(0); saved.setReportId(88L); return saved; });
        JsonNode tree = response("create", "USER");
        assertEquals(88, tree.get("reportId").asInt()); assertEquals(7, tree.get("productId").asInt());
        assertTrue(tree.get("isReport").isNull()); assertTrue(tree.get("createdAt").isNull());
        assertFalse(tree.has("reporter")); assertFalse(tree.has("user")); assertEquals(CREATE, keys(tree));
        verify(reports).save(argThat(saved -> saved.getReporter() == null && saved.getUser() == null && saved.getReporterId().equals(1000L)));
    }
    static Stream<Arguments> rejectedRoles() {
        return Stream.of("list", "detail", "patch").flatMap(op -> Stream.of("USER", "UNCONFIRMED_USER").flatMap(role ->
                Stream.of("reporter", "target", "third").map(identity -> Arguments.of(op, role, identity))));
    }
    @ParameterizedTest @MethodSource("rejectedRoles")
    void nonAdminIdentitiesCannotReadOrPatch(String op, String role, String identity) throws Exception {
        String email = identity + "@example.test";
        when(users.findUserByEmail(email)).thenReturn(Optional.of(person(identity.equals("reporter") ? 1000 : identity.equals("target") ? 2000 : 3000)));
        mvc.perform(request(op).header("Authorization", "Bearer " + token("access", role, email, 3600)))
                .andExpect(status().isForbidden()).andExpect(content().string(""));
        verifyNoInteractions(reports, types, users);
    }
    static Stream<Arguments> credentialMatrix() {
        return Stream.of("create", "list", "detail", "patch").flatMap(op ->
                Stream.of("missing", "malformed", "refreshMissing", "refreshMalformed", "refreshExpired", "refreshWrongType", "validRefresh", "expiredAccessRefresh")
                        .map(kind -> Arguments.of(op, kind)));
    }
    @ParameterizedTest @MethodSource("credentialMatrix")
    void realTokenAndRefreshBoundary(String op, String kind) throws Exception {
        var request = request(op); String role = op.equals("create") ? "USER" : "ADMIN";
        if (kind.equals("malformed")) request.header("Authorization", "Bearer invalid");
        if (kind.equals("expiredAccessRefresh")) request.header("Authorization", "Bearer " + token("access", role, EMAIL, -3600));
        if (kind.startsWith("refresh") || kind.equals("validRefresh") || kind.equals("expiredAccessRefresh")) {
            if (!kind.equals("refreshMissing")) request.cookie(new Cookie("refreshToken", kind.equals("refreshMalformed") ? "invalid" :
                    token(kind.equals("refreshWrongType") ? "access" : "refresh", role, EMAIL, kind.equals("refreshExpired") ? -3600 : 3600)));
        }
        boolean accepted = kind.equals("validRefresh") || kind.equals("expiredAccessRefresh");
        var result = mvc.perform(request).andExpect(status().is(accepted ? op.equals("create") ? 201 : 200 : 401)).andReturn();
        if (accepted) {
            assertTrue(result.getResponse().getHeader("Authorization").startsWith("Bearer "));
            // Shape checks without writing a second set of baseline files.
            JsonNode tree = mapper.readTree(result.getResponse().getContentAsByteArray());
            for (String forbidden : FORBIDDEN) assertTrue(tree.findValues(forbidden).isEmpty());
        } else { assertEquals("", result.getResponse().getContentAsString()); assertNull(result.getResponse().getHeader("Authorization")); verifyNoInteractions(reports, types, users); }
    }
    @Test void bootMapperIsTheActualUniqueMvcJacksonMapper() {
        var converters = context.getBean(RequestMappingHandlerAdapter.class).getMessageConverters().stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance).map(MappingJackson2HttpMessageConverter.class::cast).toList();
        assertEquals(1, converters.size()); assertSame(mapper, converters.get(0).getObjectMapper());
    }
    @Test void unprocessedNullPageSortMetadataOrderAndEmptyPageArePreserved() throws Exception {
        JsonNode tree = mapper.readTree(mvc.perform(get("/admin/reports").param("page", "1").param("unprocessedOnly", "true")
                .header("Authorization", "Bearer " + token("access", "ADMIN", EMAIL, 3600))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        var page = org.mockito.ArgumentCaptor.forClass(Pageable.class); verify(reports).findByIsReport(isNull(), page.capture());
        assertEquals(1, page.getValue().getPageNumber()); assertEquals(5, page.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, page.getValue().getSort().getOrderFor("reportId").getDirection());
        assertEquals(11, tree.get("totalElements").asInt()); assertEquals(3, tree.get("totalPages").asInt()); assertEquals(1, tree.at("/pageable/pageNumber").asInt());
        Report second = fixture(); second.setReportId(77L);
        when(reports.findAll(any(Pageable.class))).thenAnswer(call -> new PageImpl<>(List.of(report, second), call.getArgument(0), 2));
        JsonNode multiple = response("list", "ADMIN").get("content"); assertEquals(88, multiple.get(0).get("reportId").asInt()); assertEquals(77, multiple.get(1).get("reportId").asInt());
        when(reports.findAll(any(Pageable.class))).thenAnswer(call -> Page.empty(call.getArgument(0)));
        assertTrue(response("list", "ADMIN").get("content").isEmpty()); verify(reports, never()).save(any());
    }
    @ParameterizedTest @ValueSource(strings = {"create", "list", "detail", "patch"})
    void nullAssociationsAndNullableScalarFieldsRemainNull(String op) throws Exception {
        report = Report.builder().reportId(88L).build();
        JsonNode tree = response(op, op.equals("create") ? "USER" : "ADMIN"); JsonNode item = op.equals("list") ? tree.get("content").get(0) : tree;
        Set<String> expected = new HashSet<>(op.equals("create") ? CREATE : op.equals("list") ? LIST : DETAIL);
        assertEquals(expected, keys(item));
        for (String key : expected) if (!Set.of("reportId", "reportImages", "isReport").contains(key)) assertTrue(item.get(key).isNull(), "null field changed: " + key);
        assertEquals(op.equals("patch"), item.get("isReport").asBoolean());
        if (item.has("reportImages")) assertTrue(item.get("reportImages").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"reporter", "user"})
    void oneAssociationAndNullNamesRemainUnmodifiedIncludingStaticListMapper(String nullSide) throws Exception {
        User unnamed = User.builder().userId(3000L).build();
        report.setReporter(nullSide.equals("reporter") ? null : unnamed); report.setUser(nullSide.equals("user") ? null : unnamed);
        for (String op : List.of("list", "detail", "patch")) {
            JsonNode tree = response(op, "ADMIN"); JsonNode item = op.equals("list") ? tree.get("content").get(0) : tree;
            assertTrue(item.get(nullSide).isNull()); JsonNode other = item.get(nullSide.equals("reporter") ? "user" : "reporter");
            assertEquals(3000, other.get("userId").asInt()); assertTrue(other.get("name").isNull()); assertTrue(other.get("nickname").isNull());
            assertEquals(USER, keys(other));
        }
        JsonNode direct = mapper.valueToTree(ReadReportListResponseDTO.from(report));
        assertTrue(direct.get(nullSide).isNull()); assertEquals(USER, keys(direct.get(nullSide.equals("reporter") ? "user" : "reporter")));
    }
    @Test void deleteStillReturnsOnlyIdAndVisibleTrue() throws Exception {
        JsonNode tree = mapper.readTree(mvc.perform(delete("/admin/reports/88").header("Authorization", "Bearer " + token("access", "ADMIN", EMAIL, 3600)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEquals(Set.of("reportId", "visible"), keys(tree)); assertTrue(tree.get("visible").asBoolean()); verify(reports).save(report);
    }
    @ParameterizedTest @ValueSource(strings = {"detail", "patch"})
    void missingReportRetainsExistingDereferenceFailure(String op) {
        when(reports.findById(88L)).thenReturn(Optional.empty());
        Exception failure = assertThrows(Exception.class, () -> response(op, "ADMIN")); assertInstanceOf(NullPointerException.class, failure.getCause());
        verify(reports, never()).save(any());
    }
    @Test void creationMappingDoesNotAccessEitherUserAssociation() throws Exception {
        Report guarded = spy(report); doThrow(new AssertionError("reporter association accessed")).when(guarded).getReporter();
        doThrow(new AssertionError("user association accessed")).when(guarded).getUser(); when(reports.save(any())).thenReturn(guarded);
        JsonNode tree = response("create", "USER"); assertEquals(CREATE, keys(tree)); verify(guarded, never()).getReporter(); verify(guarded, never()).getUser();
    }
    @Test void adminMappingAndSerializationOnlyReadThreeAllowedUserGetters() throws Exception {
        User guarded = mock(User.class, call -> {
            return switch (call.getMethod().getName()) {
                case "getUserId" -> 1000L; case "getName" -> null; case "getNickname" -> "fixture-nickname";
                default -> throw new AssertionError("forbidden User method: " + call.getMethod().getName());
            };
        });
        report.setReporter(guarded); report.setUser(guarded);
        for (String op : List.of("list", "detail", "patch")) {
            JsonNode tree = response(op, "ADMIN"); JsonNode item = op.equals("list") ? tree.get("content").get(0) : tree;
            assertEquals(USER, keys(item.get("reporter"))); assertTrue(item.get("reporter").get("name").isNull());
        }
        JsonNode direct = mapper.valueToTree(ReadReportListResponseDTO.from(report)); assertEquals(USER, keys(direct.get("user")));
    }
    @Test void productReportDtoRetainsSeparateIdContract() {
        var dto = com.ngng.api.product.product.dto.response.ReadReportResponseDTO.builder()
                .reporter(new com.ngng.api.product.product.dto.UserDTO(person(1000L))).build();
        JsonNode tree = mapper.valueToTree(dto); assertEquals(Set.of("id", "name", "nickname"), keys(tree.get("reporter")));
        assertFalse(tree.get("reporter").has("userId"));
    }
    @Test void sharedDtoInPenaltyDetailStillContainsOnlyIdsAndNullUnpopulatedFields() {
        var penalties = mock(com.ngng.api.penalty.repository.PenaltyRepository.class);
        var levels = mock(com.ngng.api.penalty.repository.PenaltyLevelRepository.class);
        when(penalties.findByReportId(88L)).thenReturn(Optional.of(com.ngng.api.penalty.entity.Penalty.builder().penaltyId(5L).build()));
        var service = new com.ngng.api.penalty.service.PenaltyServiceImpl(penalties, levels, reports);
        JsonNode nested = mapper.valueToTree(service.findById(88L)).get("report");
        assertEquals(DETAIL, keys(nested));
        assertEquals(88, nested.get("reportId").asInt()); assertEquals(7, nested.get("productId").asInt());
        for (String key : DETAIL) if (!Set.of("reportId", "productId").contains(key)) assertTrue(nested.get(key).isNull());
        verify(reports, never()).save(any()); verifyNoInteractions(levels);
    }
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, CreateReportController.class, AdminReportController.class, ReportServiceImpl.class, AuthService.class, JacksonAutoConfiguration.class})
    static class Config implements WebMvcConfigurer {
        final ObjectMapper mapper; Config(ObjectMapper mapper) { this.mapper = mapper; }
        @Override public void configureMessageConverters(List<HttpMessageConverter<?>> converters) { converters.add(new MappingJackson2HttpMessageConverter(mapper)); }
        @Bean @Primary AuthenticationConfiguration authenticationConfiguration() throws Exception {
            var value = mock(AuthenticationConfiguration.class); when(value.getAuthenticationManager()).thenReturn(mock(AuthenticationManager.class)); return value;
        }
        @Bean JwtTokenVerifier verifier() { return new JwtTokenVerifier(SECRET); }
        @Bean JwtTokenProvider provider() { return new JwtTokenProvider(SECRET); }
        @Bean CustomLogoutHandler logoutHandler() { return new CustomLogoutHandler(); }
        @Bean CustomLogoutSuccessHandler logoutSuccessHandler() { return new CustomLogoutSuccessHandler(); }
        @Bean ReportRepository reports() { return mock(ReportRepository.class); }
        @Bean ReportTypeRepository types() { return mock(ReportTypeRepository.class); }
        @Bean ReportImageRepository images() { return mock(ReportImageRepository.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }
}
