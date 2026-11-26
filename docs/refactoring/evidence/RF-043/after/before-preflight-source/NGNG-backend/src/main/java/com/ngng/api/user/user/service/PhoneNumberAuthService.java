package com.ngng.api.user.user.service;

import com.ngng.api.user.user.dto.request.PhoneNumberAuthRequest;
import com.ngng.api.user.user.dto.response.PhoneNumberAuthResponse;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.signup.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PhoneNumberAuthService {
    private final UserRepository userRepository;
    private final SignupVerification verification;
    private final SignupSmsSender sender;
    private final boolean sendEnabled;
    public PhoneNumberAuthService(UserRepository userRepository, SignupVerification verification, SignupSmsSender sender,
                                  @Value("${message.send-enabled:true}") boolean sendEnabled) {
        this.userRepository = userRepository; this.verification = verification; this.sender = sender; this.sendEnabled = sendEnabled;
    }
    public PhoneNumberAuthResponse sendMessage(PhoneNumberAuthRequest request) {
        verification.gate();
        String phone = SignupTarget.phone(request.phoneNumber());
        if (!sendEnabled || userRepository.existsByPhoneNumber(phone)) return PhoneNumberAuthResponse.fail();
        SignupVerification.Reservation reservation = verification.reserve(request.attemptId(), request.attemptSecret(), "phone", phone);
        boolean sent;
        try { sent = sender.send(phone, request.name(), reservation.code()); }
        catch (RuntimeException e) { verification.complete(reservation, false); throw SignupFailure.unavailable(); }
        SignupVerification.Challenge challenge = verification.complete(reservation, sent);
        return PhoneNumberAuthResponse.success(challenge.challengeId(), challenge.expiresAt());
    }
}
