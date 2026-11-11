package com.ngng.api.isolation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngng.api.global.security.config.SecurityConfig;
import com.ngng.api.global.security.handler.CustomLogoutHandler;
import com.ngng.api.global.security.handler.CustomLogoutSuccessHandler;
import com.ngng.api.global.security.jwt.util.JwtTokenProvider;
import com.ngng.api.global.security.jwt.util.JwtTokenVerifier;
import com.ngng.api.product.product.service.ProductService;
import com.ngng.api.transaction.service.TransactionDetailsService;
import com.ngng.api.user.point.controller.PointController;
import com.ngng.api.user.point.repository.PointHistoryRepository;
import com.ngng.api.user.point.service.PointHistoryService;
import com.ngng.api.user.user.controller.JoinAuthController;
import com.ngng.api.user.user.controller.UserController;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.servlet.config.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.mockito.Mockito.*;

/** Closed list: no application/auto-configuration enablement, component/repository scanning,
 * datasource, transaction advice, Redis/ES/AWS configuration or real SMS constructor.
 * JacksonAutoConfiguration only builds the Boot mapper; MVC uses that mapper explicitly. */
@Configuration(proxyBeanMethods = false)
@EnableWebMvc
@EnableWebSecurity
@Import({SecurityConfig.class, JacksonAutoConfiguration.class, UserController.class, UserService.class,
        PointController.class, PointHistoryService.class, AuthService.class,
        JoinAuthController.class, EmailAuthService.class})
class Rf016IsolationConfiguration implements WebMvcConfigurer {
    private final ObjectMapper mapper;
    Rf016IsolationConfiguration(ObjectMapper mapper) { this.mapper = mapper; }
    @Override public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(new MappingJackson2HttpMessageConverter(mapper));
    }
    @Bean @Primary AuthenticationConfiguration unusedLoginConfiguration() throws Exception {
        AuthenticationConfiguration configuration = mock(AuthenticationConfiguration.class);
        when(configuration.getAuthenticationManager()).thenReturn(mock(AuthenticationManager.class));
        return configuration;
    }
    @Bean JwtTokenVerifier tokenVerifier() { return new JwtTokenVerifier(Rf016SafeContextLoader.SECRET); }
    @Bean JwtTokenProvider tokenProvider() { return new JwtTokenProvider(Rf016SafeContextLoader.SECRET); }
    @Bean CustomLogoutHandler logoutHandler() { return new CustomLogoutHandler(); }
    @Bean CustomLogoutSuccessHandler logoutSuccessHandler() { return new CustomLogoutSuccessHandler(); }
    @Bean UserRepository userRepository() { return mock(UserRepository.class); }
    @Bean PointHistoryRepository pointHistoryRepository() { return mock(PointHistoryRepository.class); }
    // Unused user-page collaborators are service-boundary doubles, never real constructors.
    @Bean ProductService productService() { return mock(ProductService.class); }
    @Bean TransactionDetailsService transactionDetailsService() { return mock(TransactionDetailsService.class); }
    @Bean PhoneNumberAuthService phoneNumberAuthService() { return mock(PhoneNumberAuthService.class); }
    @Bean MemoryMailbox mailbox() { return new MemoryMailbox(); }
    @Bean JavaMailSender javaMailSender(MemoryMailbox mailbox) {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(inv -> { mailbox.messages.add(new MimeMessage((MimeMessage) inv.getArgument(0))); return null; })
                .when(sender).send(any(MimeMessage.class));
        return sender;
    }
    static final class MemoryMailbox { final List<MimeMessage> messages = new ArrayList<>(); }
}
