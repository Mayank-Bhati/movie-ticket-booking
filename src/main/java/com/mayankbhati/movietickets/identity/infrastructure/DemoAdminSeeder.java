package com.mayankbhati.movietickets.identity.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class DemoAdminSeeder implements ApplicationRunner {
    private final UserAccountStore users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    DemoAdminSeeder(UserAccountStore users, PasswordEncoder passwordEncoder,
                    @Value("${app.admin.email:admin@tickets.local}") String email,
                    @Value("${app.admin.password:admin12345}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        users.createAdminIfMissing(email, passwordEncoder.encode(password));
    }
}

