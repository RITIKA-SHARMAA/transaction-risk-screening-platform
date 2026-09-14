package com.ritikasharma.risk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "country_risk")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CountryRisk extends AuditedEntity {

    /** ISO 3166-1 alpha-2. */
    @Id
    @Column(name = "country_code")
    private String countryCode;

    @Column(name = "country_name", nullable = false)
    private String countryName;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private CountryRiskLevel riskLevel;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;
}
