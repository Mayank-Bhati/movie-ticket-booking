package com.mayankbhati.movietickets.identity.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mayankbhati.movietickets.identity.domain.Actor;

@Repository
public class UserAccountStore {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public UserAccountStore(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Optional<Account> findByEmail(String email) {
        List<Account> accounts = jdbc.query("""
                SELECT id, email, password_hash, role
                FROM app_user
                WHERE LOWER(email) = LOWER(:email)
                """, Map.of("email", email), (rs, rowNum) -> new Account(
                rs.getLong("id"), rs.getString("email"), rs.getString("password_hash"),
                Actor.Role.valueOf(rs.getString("role"))));
        return accounts.stream().findFirst();
    }

    public long createCustomer(String email, String passwordHash) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO app_user(email, password_hash, role, created_at)
                VALUES (:email, :passwordHash, 'CUSTOMER', :createdAt)
                """, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("email", email.toLowerCase())
                .addValue("passwordHash", passwordHash)
                .addValue("createdAt", OffsetDateTime.now(clock)), keyHolder, new String[]{"id"});
        return keyHolder.getKey().longValue();
    }

    public void createAdminIfMissing(String email, String passwordHash) {
        if (findByEmail(email).isEmpty()) {
            jdbc.update("""
                    INSERT INTO app_user(email, password_hash, role, created_at)
                    VALUES (:email, :passwordHash, 'ADMIN', :createdAt)
                    """, Map.of("email", email.toLowerCase(), "passwordHash", passwordHash,
                    "createdAt", OffsetDateTime.now(clock)));
        }
    }

    public record Account(long id, String email, String passwordHash, Actor.Role role) {
        public Actor actor() {
            return new Actor(id, email, role);
        }
    }
}

