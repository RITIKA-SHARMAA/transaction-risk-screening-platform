package com.ritikasharma.risk.domain;

import com.ritikasharma.risk.common.NameNormalizer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchlistEntryRepository extends JpaRepository<WatchlistEntry, Long> {

    List<WatchlistEntry> findByNormalizedNameAndActiveTrue(String normalizedName);

    /** Exact match on the normalized form of {@code rawName} against active entries. */
    default List<WatchlistEntry> findActiveByName(String rawName) {
        return findByNormalizedNameAndActiveTrue(NameNormalizer.normalize(rawName));
    }
}
