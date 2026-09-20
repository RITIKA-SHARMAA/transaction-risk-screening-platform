package com.ritikasharma.risk.domain;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class AppUserRepositoryTest {

    private static final String SOME_BCRYPT_HASH = new BCryptPasswordEncoder(4).encode("secret");

    @Autowired
    private AppUserRepository repository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void devUsersAreSeededWithRolesAndBcryptHashes() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        AppUser merchant = repository.findByUsername("merchant.demo").orElseThrow();
        assertThat(merchant.getRoles()).containsExactly(Role.MERCHANT);
        assertThat(merchant.getMerchantId()).isEqualTo("merchant-demo");
        assertThat(merchant.isEnabled()).isTrue();
        assertThat(encoder.matches("merchant-dev-password", merchant.getPasswordHash())).isTrue();

        AppUser reviewer = repository.findByUsername("reviewer.demo").orElseThrow();
        assertThat(reviewer.getRoles()).containsExactly(Role.REVIEWER);
        assertThat(reviewer.getMerchantId()).isNull();
        assertThat(encoder.matches("reviewer-dev-password", reviewer.getPasswordHash())).isTrue();
    }

    @Test
    void findByUsernameLoadsRolesAndReturnsEmptyForUnknownUser() {
        repository.saveAndFlush(new AppUser(UUID.randomUUID(), "both.roles", SOME_BCRYPT_HASH, "m-1",
                Set.of(Role.MERCHANT, Role.REVIEWER)));
        entityManager.clear();

        assertThat(repository.findByUsername("both.roles")).get()
                .extracting(AppUser::getRoles).isEqualTo(Set.of(Role.MERCHANT, Role.REVIEWER));
        assertThat(repository.findByUsername("nobody")).isEmpty();
    }

    @Test
    void duplicateUsernameIsRejected() {
        assertThatThrownBy(() -> repository.saveAndFlush(
                new AppUser(UUID.randomUUID(), "merchant.demo", SOME_BCRYPT_HASH, "m-2", Set.of(Role.MERCHANT))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_app_users_username");
    }

    @Test
    void upperCaseUsernameAndNonBcryptHashAreRejected() {
        assertThatThrownBy(() -> repository.saveAndFlush(
                new AppUser(UUID.randomUUID(), "Mixed.Case", SOME_BCRYPT_HASH, null, Set.of(Role.REVIEWER))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_app_users_username");
    }

    @Test
    void plaintextPasswordIsRejected() {
        assertThatThrownBy(() -> repository.saveAndFlush(
                new AppUser(UUID.randomUUID(), "plain.text", "hunter2", null, Set.of(Role.REVIEWER))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_app_users_password_hash_bcrypt");
    }
}
