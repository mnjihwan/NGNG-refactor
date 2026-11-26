package com.ngng.api.user.user.service;

import com.ngng.api.user.user.dto.request.EmailAuthRequest;
import com.ngng.api.user.user.dto.response.EmailAuthResponse;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.signup.*;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class EmailAuthService {
    private final JavaMailSender javaMailSender;
    private final UserRepository userRepository;
    private final SignupVerification verification;
    @Value("${spring.mail.username}") private String from;
    public EmailAuthResponse sendMail(EmailAuthRequest request) {
        verification.gate();
        String email = SignupTarget.email(request.email());
        if (userRepository.existsByEmail(email)) return EmailAuthResponse.fail();
        SignupVerification.Reservation reservation = verification.reserve(request.attemptId(), request.attemptSecret(), "email", email);
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            message.setFrom(from); message.setRecipients(Message.RecipientType.TO, email);
            message.setSubject("[내꺼니꺼] 회원가입 인증번호 알림");
            message.setText("내꺼니꺼 인증번호 : " + reservation.code());
            javaMailSender.send(message);
        } catch (Exception e) {
            verification.complete(reservation, false);
            throw SignupFailure.unavailable();
        }
        SignupVerification.Challenge challenge = verification.complete(reservation, true);
        return EmailAuthResponse.success(challenge.challengeId(), challenge.expiresAt());
    }
}
