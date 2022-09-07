package de.tum.in.www1.artemis.service.feature;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

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
