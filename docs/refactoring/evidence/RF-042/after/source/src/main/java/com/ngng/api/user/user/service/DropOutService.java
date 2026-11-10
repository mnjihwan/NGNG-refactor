package com.ngng.api.user.user.service;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.user.user.dto.request.DropOutRequest;
import com.ngng.api.user.user.dto.response.DropOutResponse;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class DropOutService {

    private final UserRepository userRepository;
    private final AuthService authService;

    @Transactional
    public DropOutResponse dropOut(DropOutRequest request) {

        requireOwnUser(request == null ? null : request.id());

        User user = userRepository.findById(request.id()).orElseThrow(() -> new UsernameNotFoundException("no user"));

        user.changeVisible();

        return DropOutResponse.success();
    }

    private void requireOwnUser(Long requestedUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof CustomUserDetails details)
                || details.getUser() == null || details.getUser().getEmail() == null
                || details.getUser().getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        User caller = authService.readAuthUser();
        if (caller == null || caller.getUserId() == null || requestedUserId == null
                || !caller.getUserId().equals(requestedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
