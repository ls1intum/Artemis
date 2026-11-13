package de.tum.cit.aet.artemis.deimos.config;

import static de.tum.cit.aet.artemis.deimos.config.DeimosLimits.DEFAULT_MAX_WINDOW_DAYS;
import static de.tum.cit.aet.artemis.deimos.config.DeimosLimits.MAX_WINDOW_DAYS_PROPERTY;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Publishes the maximum length of a Deimos analysis window to the client, so the date-range dialog validates against
 * the same value the server enforces instead of a copy that can drift.
 */
@Conditional(DeimosEnabled.class)
@Component
@Lazy
public class DeimosInfoContributor implements InfoContributor {

    public static final String MAX_WINDOW_DAYS_INFO_KEY = "deimosMaxWindowDays";

    private final long maxWindowDays;

    public DeimosInfoContributor(@Value("${" + MAX_WINDOW_DAYS_PROPERTY + ":" + DEFAULT_MAX_WINDOW_DAYS + "}") long maxWindowDays) {
        this.maxWindowDays = maxWindowDays;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(MAX_WINDOW_DAYS_INFO_KEY, maxWindowDays);
    }
}
