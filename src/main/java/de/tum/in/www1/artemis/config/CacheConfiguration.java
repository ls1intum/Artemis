package de.tum.in.www1.artemis.config;

import io.github.jhipster.config.JHipsterProperties;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.ehcache.jsr107.Eh107Configuration;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.cache.JCacheManagerCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.annotation.*;

@Configuration
@EnableCaching
public class CacheConfiguration {

    private final javax.cache.configuration.Configuration<Object, Object> jcacheConfiguration;

    public CacheConfiguration(JHipsterProperties jHipsterProperties) {
        JHipsterProperties.Cache.Ehcache ehcache =
            jHipsterProperties.getCache().getEhcache();

        jcacheConfiguration = Eh107Configuration.fromEhcacheCacheConfiguration(
            CacheConfigurationBuilder.newCacheConfigurationBuilder(Object.class, Object.class,
                ResourcePoolsBuilder.heap(ehcache.getMaxEntries()))
                .withExpiry(Expirations.timeToLiveExpiration(Duration.of(ehcache.getTimeToLiveSeconds(), TimeUnit.SECONDS)))
                .build());
    }

    @Bean
    public JCacheManagerCustomizer cacheManagerCustomizer() {
        return cm -> {
            cm.createCache(de.tum.in.www1.artemis.repository.UserRepository.USERS_BY_LOGIN_CACHE, jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.repository.UserRepository.USERS_BY_EMAIL_CACHE, jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.User.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Authority.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.User.class.getName() + ".authorities", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Submission.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Course.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Course.class.getName() + ".exercises", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Exercise.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Exercise.class.getName() + ".participations", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ProgrammingExercise.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ModelingExercise.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizExercise.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizExercise.class.getName() + ".questions", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.LtiOutcomeUrl.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.SubmittedAnswer.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Question.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.MultipleChoiceQuestion.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.MultipleChoiceQuestion.class.getName() + ".answerOptions", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.AnswerOption.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.MultipleChoiceSubmittedAnswer.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.MultipleChoiceSubmittedAnswer.class.getName() + ".selectedOptions", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropQuestion.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropQuestion.class.getName() + ".dropLocations", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropQuestion.class.getName() + ".dragItems", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DropLocation.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragItem.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Participation.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Participation.class.getName() + ".results", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.LtiUserId.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Result.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Result.class.getName() + ".feedbacks", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Feedback.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ModelingSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizSubmission.class.getName() + ".submittedAnswers", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropSubmittedAnswer.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropSubmittedAnswer.class.getName() + ".assignments", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropAssignment.class.getName(), jcacheConfiguration);
            // jhipster-needle-ehcache-add-entry
        };
    }
}
