package com.ritikasharma.risk.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Guards the database-side rules the entities rely on. */
@RepositoryTest
class SchemaConstraintsTest {

    private static final Pattern QUOTED = Pattern.compile("'([A-Z_]+)'");

    @Autowired
    private JdbcTemplate jdbc;

    static Stream<Arguments> enumBackedColumns() {
        return Stream.of(
                Arguments.of("ck_transactions_status", TransactionStatus.class),
                Arguments.of("ck_idempotency_records_status", IdempotencyStatus.class),
                Arguments.of("ck_decisions_decision", Decision.class),
                Arguments.of("ck_risk_rules_decision", Decision.class),
                Arguments.of("ck_risk_rules_rule_type", RuleType.class),
                Arguments.of("ck_risk_rules_signal_type", SignalType.class),
                Arguments.of("ck_watchlist_entries_entity_type", WatchlistEntityType.class),
                Arguments.of("ck_country_risk_level", CountryRiskLevel.class),
                Arguments.of("ck_outbox_events_status", OutboxStatus.class));
    }

    @ParameterizedTest(name = "{0} allows exactly the constants of {1}")
    @MethodSource("enumBackedColumns")
    void checkConstraintMatchesEnum(String constraintName, Class<? extends Enum<?>> enumType) {
        String definition = jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?", String.class, constraintName);

        Matcher matcher = QUOTED.matcher(definition);
        Stream.Builder<String> allowed = Stream.builder();
        while (matcher.find()) {
            allowed.add(matcher.group(1));
        }

        assertThat(allowed.build().toList())
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(enumType.getEnumConstants()).map(Enum::name).toList());
    }

    @Test
    void unknownStatusIsRejected() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transactions (id, merchant_id, amount, currency, payer_account_id, payer_name,
                                          payer_country, payee_name, payee_country, status)
                VALUES (gen_random_uuid(), 'm', 10, 'USD', 'p', 'n', 'US', 'n', 'US', 'SETTLED')
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transactions_status");
    }

    @Test
    void decisionCutoffMustHaveOutcomeAndNoSignalType() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO risk_rules (code, name, rule_type, signal_type, decision, threshold)
                VALUES ('BAD_CUTOFF', 'bad', 'DECISION_CUTOFF', 'RISK', NULL, 50)
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_risk_rules_cutoff_shape");
    }

    @Test
    void everyTableHasAuditTimestamps() {
        var tablesMissingTimestamps = jdbc.queryForList("""
                SELECT t.table_name
                  FROM information_schema.tables t
                 WHERE t.table_schema = 'public'
                   AND t.table_type = 'BASE TABLE'
                   AND t.table_name <> 'flyway_schema_history'
                   AND (SELECT count(*) FROM information_schema.columns c
                         WHERE c.table_schema = 'public' AND c.table_name = t.table_name
                           AND c.column_name IN ('created_at', 'updated_at')
                           AND c.data_type = 'timestamp with time zone' AND c.is_nullable = 'NO') <> 2
                """, String.class);

        assertThat(tablesMissingTimestamps).isEmpty();
    }
}
