package com.ngng.api.user.user.controller;
import com.ngng.api.user.user.service.signup.SignupFailure;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;
@RestControllerAdvice(assignableTypes = {JoinController.class, JoinAuthController.class})
public class SignupExceptionHandler {
    @ExceptionHandler(SignupFailure.class)
    public ResponseEntity<?> handle(SignupFailure failure) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(failure.status()).cacheControl(CacheControl.noStore());
        if (failure.retryAfter() > 0) response.header("Retry-After", Long.toString(failure.retryAfter()));
        return response.body(Map.of("code", failure.code()));
    }
}
