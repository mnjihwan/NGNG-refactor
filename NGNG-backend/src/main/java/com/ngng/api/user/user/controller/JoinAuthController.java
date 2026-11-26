package com.ngng.api.user.user.controller;

import com.ngng.api.user.user.dto.request.*;
import com.ngng.api.user.user.dto.response.*;
import com.ngng.api.user.user.service.EmailAuthService;
import com.ngng.api.user.user.service.PhoneNumberAuthService;
import com.ngng.api.user.user.service.signup.SignupVerification;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/join/auth")
@RestController
public class JoinAuthController {
    private final EmailAuthService emailAuthService;
    private final PhoneNumberAuthService phoneNumberAuthService;
    private final SignupVerification verification;
    @PostMapping("/attempt")
    public ResponseEntity<?> attempt() { return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(verification.create()); }
    @PostMapping("/phonenumber")
    public ResponseEntity<?> sendMessage(@RequestBody PhoneNumberAuthRequest request) {
        PhoneNumberAuthResponse response = phoneNumberAuthService.sendMessage(request);
        return response.isSuccess() ? ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response)
                : ResponseEntity.status(409).cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping("/email")
    public ResponseEntity<?> sendEmail(@RequestBody EmailAuthRequest request) {
        EmailAuthResponse response = emailAuthService.sendMail(request);
        return response.isSuccess() ? ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response)
                : ResponseEntity.status(409).cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping("/email/verify")
    public ResponseEntity<?> emailVerify(@RequestBody SignupVerifyRequest request) { return verify(request, "email"); }
    @PostMapping("/phonenumber/verify")
    public ResponseEntity<?> phoneVerify(@RequestBody SignupVerifyRequest request) { return verify(request, "phone"); }
    private ResponseEntity<?> verify(SignupVerifyRequest request, String channel) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(verification.verify(request.attemptId(), request.attemptSecret(), channel, request.challengeId(), request.code()));
    }
}
