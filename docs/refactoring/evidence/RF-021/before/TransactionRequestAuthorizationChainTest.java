package com.ngng.api.transaction.authorization;

import com.ngng.api.global.security.config.SecurityConfig;
import com.ngng.api.global.security.handler.*;
import com.ngng.api.global.security.jwt.util.*;
import com.ngng.api.product.product.entity.*;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.product.product.service.ProductCacheInvalidator;
import com.ngng.api.product.thumbnail.entity.Thumbnail;
import com.ngng.api.transaction.controller.TransactionController;
import com.ngng.api.transaction.entity.*;
import com.ngng.api.transaction.repository.*;
import com.ngng.api.transaction.service.*;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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

/** Actual filter/JWT/controller/services/AuthService; repositories and cache only are doubles.
 * No Boot scan, application properties, datasource, external client or physical transaction. */
class TransactionRequestAuthorizationChainTest {
    static final String SECRET = Encoders.BASE64URL.encode(
            "rf021-synthetic-only-key-64-bytes-abcdefghijklmnopqrstuvwxyz-123456".getBytes(StandardCharsets.UTF_8));
    static final String EMAIL = "rf021@example.test";
    static AnnotationConfigWebApplicationContext context;
    static MockMvc mvc;
    TransactionDetailsRepository details;
    TransactionRequestRepository requests;
    ProductRepository products;
    ProductCacheInvalidator cache;
    UserRepository users;
    Product product;
    TransactionRequest request;
    @SuppressWarnings("removal")
    static User user(long id) { return User.builder().userId(new Long(id)).name("fixture").phoneNumber("000").email(EMAIL).build(); }
    @BeforeAll static void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "rf021-test-only", Map.of("jwt.expiration.access", "3600000", "jwt.expiration.refresh", "7200000")));
        context.setServletContext(new MockServletContext());
        context.register(IsolatedConfiguration.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterAll static void stop() { context.close(); }
    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        details = context.getBean(TransactionDetailsRepository.class);
        requests = context.getBean(TransactionRequestRepository.class);
        products = context.getBean(ProductRepository.class);
        cache = context.getBean(ProductCacheInvalidator.class);
        users = context.getBean(UserRepository.class);
        reset(details, requests, products, cache, users);
        product = Product.builder().productId(7000L).user(user(1000)).forSale(true).title("fixture").price(900L)
                .category(Category.builder().categoryId(1L).build()).status(Status.builder().statusId(1L).build())
                .thumbnail(Thumbnail.builder().thumbnailId(1L).build()).productImages(List.of()).tags(List.of()).build();
        request = TransactionRequest.builder().transactionRequestId(9000L).product(product).buyer(user(2000))
                .seller(user(3000)).price(300L).build(); // historical seller differs from current owner
        var locked = mock(ProductRepository.TransactionProduct.class);
        when(locked.getProductId()).thenReturn(7000L);
        when(locked.getSellerId()).thenReturn(1000L);
        when(products.lockTransactionProduct(7000L)).thenReturn(Optional.of(locked));
        when(products.findById(7000L)).thenReturn(Optional.of(product));
        when(requests.findProductIdForUpdate(9000L)).thenReturn(Optional.of(7000L));
        when(requests.findById(9000L)).thenReturn(Optional.of(request));
        when(requests.findAllByProductProductId(7000L)).thenReturn(List.of(request));
        when(requests.save(any())).thenAnswer(call -> {
            TransactionRequest saved = call.getArgument(0); saved.setTransactionRequestId(9000L); return saved;
        });
        when(details.save(any())).thenAnswer(call -> {
            TransactionDetails saved = call.getArgument(0); saved.setId(88L); return saved;
        });
    }
    @AfterEach void contextCleared() { assertNull(SecurityContextHolder.getContext().getAuthentication()); }
    void caller(long id) {
        when(users.findUserByEmail(EMAIL)).thenAnswer(call -> {
            var principal = (com.ngng.api.global.security.custom.CustomUserDetails)
                    SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            assertNull(principal.getUser().getUserId());
            var caller = user(id);
            assertNotSame(product.getUser().getUserId(), caller.getUserId());
            return Optional.of(caller);
        });
    }
    static String token(String type, String role, long seconds) {
        return Jwts.builder().setSubject(EMAIL).claim("role", role).claim("type", type)
                .setExpiration(new Date(System.currentTimeMillis() + seconds * 1000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(SECRET))).compact();
    }
    MockHttpServletRequestBuilder update(boolean accepted) {
        return put("/transaction/request").contentType("application/json")
                .content("{\"transactionRequestId\":9000,\"isAccepted\":" + accepted + "}");
    }
    MockHttpServletRequestBuilder create(long buyer, long seller) {
        return post("/transaction/request").contentType("application/json")
                .content("{\"productId\":7000,\"buyerId\":"+buyer+",\"sellerId\":"+seller+",\"price\":300}");
    }
    @ParameterizedTest @CsvSource({"USER,2000", "USER,4000", "ADMIN,4000", "UNCONFIRMED_USER,4000"})
    void beforeCreateCanWriteForAnotherBuyer(String role, long id) throws Exception {
        caller(id);
        mvc.perform(create(2000, 1000).header("Authorization", "Bearer " + token("access", role, 3600)))
                .andExpect(status().isOk()).andExpect(content().string("9000"));
        verify(requests).save(any());
    }
    @ParameterizedTest @CsvSource({"USER,true", "USER,false", "ADMIN,true", "UNCONFIRMED_USER,false"})
    void beforeThirdPartyCanAcceptOrReject(String role, boolean accept) throws Exception {
        caller(4000);
        mvc.perform(update(accept).header("Authorization", "Bearer " + token("access", role, 3600)))
                .andExpect(status().isOk()).andExpect(content().string("9000"));
        assertEquals(accept, request.getIsAccepted());
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "malformed"})
    void authenticationFailureNeverReachesRepositories(String credentials) throws Exception {
        var input = create(2000, 1000);
        if (credentials.equals("malformed")) input.header("Authorization", "Bearer broken");
        mvc.perform(input).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        verifyNoInteractions(details, requests, products, users, cache);
    }
    @Configuration @EnableWebMvc @EnableWebSecurity @Import(SecurityConfig.class)
    static class IsolatedConfiguration {
        @Bean @Primary AuthenticationConfiguration authenticationConfiguration() throws Exception {
            var config = mock(AuthenticationConfiguration.class);
            when(config.getAuthenticationManager()).thenReturn(mock(AuthenticationManager.class));
            return config;
        }
        @Bean JwtTokenVerifier verifier() { return new JwtTokenVerifier(SECRET); }
        @Bean JwtTokenProvider provider() {
            var provider = new JwtTokenProvider(SECRET);
            ReflectionTestUtils.setField(provider, "accessTokenExpirationMs", 3_600_000);
            return provider;
        }
        @Bean CustomLogoutHandler logoutHandler() { return new CustomLogoutHandler(); }
        @Bean CustomLogoutSuccessHandler logoutSuccessHandler() { return new CustomLogoutSuccessHandler(); }
        @Bean TransactionDetailsRepository details() { return mock(TransactionDetailsRepository.class); }
        @Bean TransactionStatusRepository statuses() { return mock(TransactionStatusRepository.class); }
        @Bean ProductRepository products() { return mock(ProductRepository.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean AuthService authService(UserRepository users) { return new AuthService(users); }
        @Bean TransactionDetailsService service(TransactionDetailsRepository details, TransactionStatusRepository statuses,
                                               ProductRepository products, AuthService auth) {
            return new TransactionDetailsService(details, statuses, products, auth);
        }
        @Bean TransactionRequestRepository requestsRepository() { return mock(TransactionRequestRepository.class); }
        @Bean ProductCacheInvalidator cache() { return mock(ProductCacheInvalidator.class); }
        @Bean TransactionRequestService requests(TransactionRequestRepository requests, ProductRepository products,
                TransactionDetailsService details, ProductCacheInvalidator cache) {
            return new TransactionRequestService(requests, products, details, cache);
        }
        @Bean TransactionController controller(TransactionDetailsService service, TransactionRequestService requests) {
            return new TransactionController(service, requests);
        }
    }
}
