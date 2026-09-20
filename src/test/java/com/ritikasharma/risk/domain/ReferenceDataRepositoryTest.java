package com.ritikasharma.risk.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
class ReferenceDataRepositoryTest {

    @Autowired
    private RiskRuleRepository riskRuleRepository;
    @Autowired
    private WatchlistEntryRepository watchlistEntryRepository;

    @Test
    void enabledRulesBySignalTypeExcludeDisabledRulesAndOtherSignals() {
        assertThat(riskRuleRepository.findBySignalTypeAndEnabledTrueOrderByCodeAsc(SignalType.RISK))
                .extracting(RiskRule::getCode)
                .containsExactly(
                        "AMOUNT_HIGH_EUR", "AMOUNT_HIGH_USD", "AMOUNT_VERY_HIGH_EUR", "AMOUNT_VERY_HIGH_USD",
                        "COUNTRY_HIGH_RISK", "COUNTRY_PROHIBITED", "CROSS_BORDER", "VELOCITY_1H", "VELOCITY_24H");

        assertThat(riskRuleRepository.findBySignalTypeAndEnabledTrueOrderByCodeAsc(SignalType.SCREENING))
                .extracting(RiskRule::getCode)
                .containsExactly("WATCHLIST_NAME_MATCH");
    }

    @Test
    void enabledDecisionCutoffsAreOrderedByThresholdDescending() {
        assertThat(riskRuleRepository.findEnabledDecisionCutoffs())
                .extracting(RiskRule::getDecision)
                .containsExactly(Decision.BLOCK, Decision.REVIEW);

        assertThat(riskRuleRepository.findByRuleTypeAndEnabledTrueOrderByThresholdDesc(RuleType.VELOCITY))
                .extracting(RiskRule::getCode)
                .containsExactly("VELOCITY_24H", "VELOCITY_1H");
    }

    @Test
    void findByCode() {
        assertThat(riskRuleRepository.findByCode("CROSS_BORDER")).get()
                .extracting(RiskRule::getRuleType).isEqualTo(RuleType.CROSS_BORDER);
        assertThat(riskRuleRepository.findByCode("NOPE")).isEmpty();
    }

    @Test
    void activeWatchlistLookupNormalizesTheInputName() {
        assertThat(watchlistEntryRepository.findActiveByName("  QUENTIN marlow--vance "))
                .extracting(WatchlistEntry::getSourceReference)
                .containsExactly("SYN-0001");
        assertThat(watchlistEntryRepository.findByNormalizedNameAndActiveTrue("northwind shell holdings ltd"))
                .extracting(WatchlistEntry::getEntityType)
                .containsExactly(WatchlistEntityType.ORGANIZATION);
    }

    @Test
    void inactiveAndUnknownNamesAreNotMatched() {
        assertThat(watchlistEntryRepository.findActiveByName("Priya Castellano")).isEmpty();
        assertThat(watchlistEntryRepository.findActiveByName("Jane Ordinary")).isEmpty();
    }
}
