package de.tum.in.www1.artemis.service.athena;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_ATHENA;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.Constants;

@Profile(PROFILE_ATHENA)
@Component
public class AthenaInfoContributor implements InfoContributor {

    @Value("${artemis.athena.allowed-self-learning-feedback-attempts:3}")
    private Integer allowedSelfLearningFeedbackAttempts;

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(Constants.ALLOWED_NUMBER_OF_NON_GRADED_FEEDBACK_REQUESTS, allowedSelfLearningFeedbackAttempts);
    }
}
