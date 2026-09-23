package com.mayankbhati.movietickets.identity.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.mayankbhati.movietickets.identity.domain.Actor;
import com.mayankbhati.movietickets.identity.domain.UserAccount;

@Repository
public class UserAccountStore {
    private final UserAccountRepository repository;
    private final Clock clock;

    public UserAccountStore(UserAccountRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Optional<Account> findByEmail(String email) {
        return repository.findByEmailIgnoreCase(email).map(Account::from);
    }

    @Transactional
    public long createCustomer(String email, String passwordHash) {
        return repository.save(new UserAccount(email, passwordHash, Actor.Role.CUSTOMER,
                OffsetDateTime.now(clock))).getId();
    }

    @Transactional
    public void createAdminIfMissing(String email, String passwordHash) {
        if (findByEmail(email).isEmpty()) {
            repository.save(new UserAccount(email, passwordHash, Actor.Role.ADMIN,
                    OffsetDateTime.now(clock)));
        }
    }

    public record Account(long id, String email, String passwordHash, Actor.Role role) {
        static Account from(UserAccount account) {
            return new Account(account.getId(), account.getEmail(), account.getPasswordHash(), account.getRole());
        }
        public Actor actor() {
            return new Actor(id, email, role);
        }
    }
}
