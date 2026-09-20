package com.ritikasharma.risk.common;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NameNormalizerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Quentin Marlow-Vance          | quentin marlow vance",
            "'  ELENA   v.  Draskovic  '   | elena v draskovic",
            "Bluefin Trade & Logistics LLC | bluefin trade logistics llc",
            "Lucía Ferreira Montalbán      | lucia ferreira montalban",
            "'--'                          | ''",
    })
    void normalizesToLowerCaseAlphanumericWords(String raw, String expected) {
        assertThat(NameNormalizer.normalize(raw)).isEqualTo(expected);
    }
}
