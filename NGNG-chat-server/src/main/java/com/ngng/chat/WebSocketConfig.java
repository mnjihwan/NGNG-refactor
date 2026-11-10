package com.ngng.chat;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import com.ngng.chat.security.ChatChannelSecurity;
import com.ngng.chat.security.ChatSessions;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final ChatChannelSecurity security;
    private final ChatSessions sessions;
    @Value("${chat.allowed-origin-patterns:*}")
    private String[] allowedOriginPatterns;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(security);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(security.outbound());
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(sessions::decorate);
    }

	@Override
	public void configureMessageBroker(MessageBrokerRegistry config) {
		config.enableSimpleBroker("/public-chats", "/private-chats", "/alarms");
		config.setApplicationDestinationPrefixes("/chats");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.setErrorHandler(new StompSubProtocolErrorHandler() {
            @Override public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
                var headers = StompHeaderAccessor.create(StompCommand.ERROR);
                headers.setMessage("Chat request rejected");
                return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
            }
        });
		registry.addEndpoint("/chat-server")
				.setAllowedOriginPatterns(allowedOriginPatterns);

	}

}
