package com.mayankbhati.movietickets.identity.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mayankbhati.movietickets.identity.application.IdentityService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/customers")
public class IdentityController {
    private final IdentityService identity;

    public IdentityController(IdentityService identity) {
        this.identity = identity;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CustomerCreated register(@Valid @RequestBody RegisterCustomer request) {
        return new CustomerCreated(identity.registerCustomer(request.email(), request.password()), request.email());
    }

    public record RegisterCustomer(@NotBlank @Email String email,
                                   @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record CustomerCreated(long id, String email) {
    }
}

