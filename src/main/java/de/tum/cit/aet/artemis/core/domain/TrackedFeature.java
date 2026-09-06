package de.tum.cit.aet.artemis.core.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * The inventory of everything whose usage can be measured: one row per REST endpoint, git operation and instrumented
 * background feature.
 * <p>
 * This table exists so the analysis can report features with <b>no</b> usage. Counters alone can only rank what was
 * called at least once, which answers "what is popular" but not "what can we delete", and the second question is the
 * one that is hard to answer any other way. Endpoint rows are therefore written at startup from Spring's mapping
 * table rather than lazily on first call.
 * <p>
 * One row is one endpoint, never one logical feature. {@link #featureLabel} is only a grouping attribute, filled from
 * {@code @FeatureUsage}. Keeping the grain at the endpoint means annotating an endpoint later relabels its existing
 * row, so historic data regroups immediately and no detail is ever lost.
 */
@Entity
@Table(name = "tracked_feature")
public class TrackedFeature extends DomainObject {

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_kind", length = 16, nullable = false)
    private FeatureKind featureKind;

    @Column(name = "module", length = 32, nullable = false)
    private String module;

    @Column(name = "identifier", length = 255, nullable = false)
    private String identifier;

    @Column(name = "feature_label", length = 128)
    private String featureLabel;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    /**
     * The last time a server reported that this feature still exists.
     * <p>
     * Advanced on every startup for REST endpoints, which is what lets the page separate a feature that exists and is
     * unused (a decision to make) from one that was removed releases ago (a decision already made). Only meaningful for
     * {@link FeatureKind#REST}: git and background features cannot be enumerated, so theirs stays at creation time.
     */
    @Column(name = "last_registered_at", nullable = false)
    private Instant lastRegisteredAt;

    public TrackedFeature() {
        // needed by Hibernate
    }

    public TrackedFeature(FeatureKind featureKind, String module, String identifier, String featureLabel, Instant firstSeenAt) {
        this.featureKind = featureKind;
        this.module = module;
        this.identifier = identifier;
        this.featureLabel = featureLabel;
        this.firstSeenAt = firstSeenAt;
        this.lastRegisteredAt = firstSeenAt;
    }

    public FeatureKind getFeatureKind() {
        return featureKind;
    }

    public void setFeatureKind(FeatureKind featureKind) {
        this.featureKind = featureKind;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getFeatureLabel() {
        return featureLabel;
    }

    public void setFeatureLabel(String featureLabel) {
        this.featureLabel = featureLabel;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public Instant getLastRegisteredAt() {
        return lastRegisteredAt;
    }

    public void setLastRegisteredAt(Instant lastRegisteredAt) {
        this.lastRegisteredAt = lastRegisteredAt;
    }
}
