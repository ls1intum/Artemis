package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.domain.FeatureKind;
import de.tum.cit.aet.artemis.core.domain.TrackedFeature;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Access to the feature inventory. Written once per startup for REST endpoints and on first sighting for git and
 * background features; read by the admin usage page.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface TrackedFeatureRepository extends ArtemisJpaRepository<TrackedFeature, Long> {

    /**
     * Resolves a single feature by its natural key. Used for git and background features, which cannot be enumerated
     * up front and are therefore registered the first time they are recorded.
     *
     * @param featureKind the namespace of the feature
     * @param identifier  the canonical identifier within that namespace
     * @return the existing row, if there is one
     */
    Optional<TrackedFeature> findByFeatureKindAndIdentifier(FeatureKind featureKind, String identifier);

    /**
     * Reclassifies an existing feature.
     * <p>
     * Called when the feature, the interaction or the resource of an endpoint has changed since the last startup, which
     * happens whenever someone reassigns an endpoint with {@code @FeatureUsage}, overrides its interaction with
     * {@code @UsageInteraction} or moves it to another controller. Because all three are only grouping attributes on a
     * row that is keyed by endpoint, historic counters regroup immediately.
     *
     * @param featureId    the feature to reclassify
     * @param featureLabel the new feature label, or {@code null} to drop it
     * @param interaction  the new interaction
     * @param resource     the new resource, or {@code null} when there is none
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE TrackedFeature feature
            SET feature.featureLabel = :featureLabel,
                feature.interaction = :interaction,
                feature.resource = :resource
            WHERE feature.id = :featureId
            """)
    void updateClassification(@Param("featureId") long featureId, @Param("featureLabel") String featureLabel, @Param("interaction") FeatureInteraction interaction,
            @Param("resource") String resource);

    /**
     * Records that the given features still exist, as of this node's startup.
     * <p>
     * The timestamp only ever moves forward. Every node runs this with its own startup time, and on a cluster whose nodes
     * restart at different times the guard keeps the most recent one instead of letting a late-starting node pull the
     * value backwards and make live features look retired.
     *
     * @param featureIds the features that were found in the mapping table
     * @param now        this node's startup time
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE TrackedFeature feature
            SET feature.lastRegisteredAt = :now
            WHERE feature.id IN :featureIds
                AND feature.lastRegisteredAt < :now
            """)
    void markStillRegistered(@Param("featureIds") List<Long> featureIds, @Param("now") Instant now);
}
