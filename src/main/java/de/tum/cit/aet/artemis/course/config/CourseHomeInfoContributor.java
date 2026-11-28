package de.tum.cit.aet.artemis.course.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.stream.Stream;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;

/** Exposes consent options without constructing the optional model client or exposing provider credentials. */
@Component
@Lazy
@Profile(PROFILE_CORE)
public class CourseHomeInfoContributor implements InfoContributor {

    private final Environment environment;

    public CourseHomeInfoContributor(Environment environment) {
        this.environment = environment;
    }

    /**
     * Advertises whether Home can use a configured provider and where inference takes place.
     *
     * @param builder the installation metadata builder
     */
    @Override
    public void contribute(Info.Builder builder) {
        var configuration = Binder.get(environment).bind(CourseHomeAiProperties.PREFIX, CourseHomeAiProperties.class)
                .orElseGet(() -> new CourseHomeAiProperties(false, null, null, null, null));
        var locations = Stream.of(AiSelectionDecision.LOCAL_AI, AiSelectionDecision.CLOUD_AI).filter(decision -> configuration.presetFor(decision).isPresent())
                .map(decision -> decision == AiSelectionDecision.LOCAL_AI ? "LOCAL" : "CLOUD").toList();
        builder.withDetail("courseHomeAiAvailable", !locations.isEmpty());
        if (locations.size() == 1) {
            builder.withDetail("courseHomeAiLocation", locations.getFirst());
        }
        else if (locations.size() > 1) {
            builder.withDetail("courseHomeAiLocations", locations);
        }
    }
}
