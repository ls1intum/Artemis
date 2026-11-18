package de.tum.cit.aet.artemis.core.config;

import java.util.List;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the built-in feature usage analysis, bound from {@code artemis.feature-usage}.
 * <p>
 * Tracking is on by default. Nothing user-identifying is recorded: a call contributes to a counter keyed by feature,
 * day and the caller's role bucket, and no login, user id or address is ever read or stored. There is therefore nothing
 * for a deployment to opt into, only something to switch off if it does not want the data at all.
 * <p>
 * {@code retentionPeriod} is validated as positive and the binding is {@code @Validated}, so a configuration typo fails
 * startup rather than being applied. That matters because the nightly pruning is irreversible: {@code 0} would make
 * every bucket expired, and a negative value would move the cutoff into the future and delete buckets written minutes
 * ago.
 *
 * @param enabled         Whether usage is recorded at all. When false the interceptor and the flush both become no-ops
 *                            and no rows are written, but existing rows stay readable on the admin page.
 * @param retentionPeriod Days before a daily bucket is deleted. The default of 400 covers the longest window the admin
 *                            page offers (180 days) plus a year-over-year comparison across two semesters, which is the
 *                            natural unit for a teaching platform.
 * @param digest          Settings for the weekly summary email.
 */
@Validated
@ConfigurationProperties(prefix = "artemis.feature-usage")
public record FeatureUsageProperties(@DefaultValue("true") boolean enabled, @DefaultValue("400") @Positive int retentionPeriod, @DefaultValue Digest digest) {

    /**
     * The weekly digest email.
     * <p>
     * Enabled by default, matching the weekly vulnerability report it is modelled on: both are only actually sent when an
     * administrator address is configured, and both skip development environments and test servers. A deployment that does
     * not want the mail switches it off here.
     *
     * @param enabled    Whether the weekly email is sent at all.
     * @param recipients Addresses to send it to. Empty falls back to {@code info.contact}, which is where the other
     *                       administrative mails go, so the common case needs no configuration.
     */
    public record Digest(@DefaultValue("true") boolean enabled, @DefaultValue List<String> recipients) {
    }
}
