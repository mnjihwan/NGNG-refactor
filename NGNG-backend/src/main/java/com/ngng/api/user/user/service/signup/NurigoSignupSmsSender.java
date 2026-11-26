package com.ngng.api.user.user.service.signup;

import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NurigoSignupSmsSender implements SignupSmsSender {
    private final String apiKey;
    private final String secret;
    private final String from;
    public NurigoSignupSmsSender(@Value("${message.key.api}") String apiKey,
                                @Value("${message.key.secret}") String secret,
                                @Value("${message.from}") String from) {
        this.apiKey = apiKey; this.secret = secret; this.from = from;
    }
    @Override public boolean send(String phone, String name, String code) {
        Message message = new Message(); message.setFrom(from); message.setTo(phone);
        message.setText(name + "님의 내꺼니꺼 인증번호\n" + code);
        SingleMessageSentResponse response = NurigoApp.INSTANCE.initialize(apiKey, secret, "https://api.coolsms.co.kr")
                .sendOne(new SingleMessageSendingRequest(message));
        return response != null && "2000".equals(response.getStatusCode());
    }
}
