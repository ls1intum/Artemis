package de.tum.in.www1.artemis.service.profile;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/**
 * Provide the available profiles (combined between all instances of the cluster via REST)
 */
@Component
public class ProfileInfoContributor implements InfoContributor {

    private final ProfileToggleService profileToggleService;

    public ProfileInfoContributor(ProfileToggleService profileToggleService) {
        this.profileToggleService = profileToggleService;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("combinedProfiles", profileToggleService.enabledProfiles());
    }
}
