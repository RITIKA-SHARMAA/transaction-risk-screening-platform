package com.ritikasharma.risk.domain;

import com.ritikasharma.risk.common.NameNormalizer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@RepositoryTest
class SeedDataTest {

    @Autowired
    private Flyway flyway;
    @Autowired
    private CountryRiskRepository countryRiskRepository;
    @Autowired
    private WatchlistEntryRepository watchlistEntryRepository;
    @Autowired
    private RiskRuleRepository riskRuleRepository;

    @Test
    void allMigrationsAreApplied() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("8.1");
        assertThat(Arrays.stream(flyway.info().all()).map(m -> m.getState()))
                .isNotEmpty()
                .allMatch(state -> state == MigrationState.SUCCESS);
    }

    @Test
    void countryRiskIsSeeded() {
        assertThat(countryRiskRepository.count()).isEqualTo(24);

        assertThat(countryRiskRepository.findById("KP")).get()
                .extracting(CountryRisk::getCountryName, CountryRisk::getRiskLevel, CountryRisk::getRiskScore)
                .containsExactly("North Korea", CountryRiskLevel.PROHIBITED, 100);
        assertThat(countryRiskRepository.findById("US")).get()
                .extracting(CountryRisk::getRiskLevel, CountryRisk::getRiskScore)
                .containsExactly(CountryRiskLevel.LOW, 10);
        assertThat(countryRiskRepository.findById("ZZ")).isEmpty();
        assertThat(countryRiskRepository.findAll()).allSatisfy(c -> assertThat(c.getCreatedAt()).isNotNull());
    }

    @Test
    void syntheticWatchlistIsSeededWithNormalizedNamesMatchingJava() {
        List<WatchlistEntry> entries = watchlistEntryRepository.findAll();

        assertThat(entries).hasSize(13);
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.getListSource()).isEqualTo("SYNTHETIC");
            assertThat(entry.getNormalizedName()).isEqualTo(NameNormalizer.normalize(entry.getFullName()));
        });
        assertThat(entries).filteredOn(WatchlistEntry::isActive).hasSize(12);
        assertThat(entries).filteredOn(e -> e.getEntityType() == WatchlistEntityType.ORGANIZATION).hasSize(5);
    }

    @Test
    void initialRiskRulesAreSeededWithWeightsAndThresholds() {
        assertThat(riskRuleRepository.findAll())
                .extracting(RiskRule::getCode)
                .containsExactlyInAnyOrder(
                        "AMOUNT_HIGH_USD", "AMOUNT_VERY_HIGH_USD", "AMOUNT_HIGH_EUR", "AMOUNT_VERY_HIGH_EUR",
                        "VELOCITY_1H", "VELOCITY_24H", "VELOCITY_10M",
                        "COUNTRY_HIGH_RISK", "COUNTRY_PROHIBITED", "CROSS_BORDER",
                        "WATCHLIST_NAME_MATCH", "DECISION_REVIEW", "DECISION_BLOCK");

        RiskRule highAmount = riskRuleRepository.findByCode("AMOUNT_HIGH_USD").orElseThrow();
        assertThat(highAmount.getRuleType()).isEqualTo(RuleType.AMOUNT_THRESHOLD);
        assertThat(highAmount.getSignalType()).isEqualTo(SignalType.RISK);
        assertThat(highAmount.getWeight()).isEqualByComparingTo("20");
        assertThat(highAmount.getThreshold()).isEqualByComparingTo("5000");
        assertThat(highAmount.getParams()).containsEntry("currency", "USD");
        assertThat(highAmount.isEnabled()).isTrue();

        RiskRule velocity = riskRuleRepository.findByCode("VELOCITY_1H").orElseThrow();
        assertThat(velocity.getParams()).containsEntry("windowMinutes", 60);

        assertThat(riskRuleRepository.findByCode("WATCHLIST_NAME_MATCH")).get()
                .extracting(RiskRule::getSignalType, RiskRule::getThreshold)
                .containsExactly(SignalType.SCREENING, new BigDecimal("0.9000"));

        assertThat(riskRuleRepository.findByCode("VELOCITY_10M")).get()
                .extracting(RiskRule::isEnabled).isEqualTo(false);

        assertThat(riskRuleRepository.findAll())
                .filteredOn(r -> r.getRuleType() == RuleType.DECISION_CUTOFF)
                .extracting(RiskRule::getCode, RiskRule::getDecision, RiskRule::getSignalType)
                .containsExactlyInAnyOrder(
                        tuple("DECISION_REVIEW", Decision.REVIEW, null),
                        tuple("DECISION_BLOCK", Decision.BLOCK, null));
    }
}
