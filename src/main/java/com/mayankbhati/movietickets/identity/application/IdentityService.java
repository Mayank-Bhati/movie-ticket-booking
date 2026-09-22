package com.mayankbhati.movietickets.identity.application;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.mayankbhati.movietickets.identity.domain.Actor;
import com.mayankbhati.movietickets.identity.infrastructure.UserAccountStore;
import com.mayankbhati.movietickets.shared.ApiException;

@Service
public class IdentityService {
    private final UserAccountStore users;
    private final PasswordEncoder passwordEncoder;

    public IdentityService(UserAccountStore users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public long registerCustomer(String email, String password) {
        if (users.findByEmail(email).isPresent()) {
            throw ApiException.conflict("EMAIL_EXISTS", "An account already exists for this email");
        }
        return users.createCustomer(email, passwordEncoder.encode(password));
    }

    public Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Authentication is required");
        }
        return users.findByEmail(authentication.getName())
                .map(UserAccountStore.Account::actor)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND",
                        "Authenticated account no longer exists"));
    }
}

