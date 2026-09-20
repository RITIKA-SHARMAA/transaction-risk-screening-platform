package com.ritikasharma.risk.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskRuleRepository extends JpaRepository<RiskRule, Long> {

    Optional<RiskRule> findByCode(String code);

    /** Enabled rules a worker evaluates, in a stable order. */
    List<RiskRule> findBySignalTypeAndEnabledTrueOrderByCodeAsc(SignalType signalType);

    List<RiskRule> findByRuleTypeAndEnabledTrueOrderByThresholdDesc(RuleType ruleType);

    /** Enabled decision cut-offs, highest threshold first: the first one the score reaches wins. */
    default List<RiskRule> findEnabledDecisionCutoffs() {
        return findByRuleTypeAndEnabledTrueOrderByThresholdDesc(RuleType.DECISION_CUTOFF);
    }
}
