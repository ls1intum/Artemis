package de.tum.in.www1.artemis.config;

import java.time.Duration;

import org.ehcache.config.builders.*;
import org.ehcache.jsr107.Eh107Configuration;

import io.github.jhipster.config.jcache.BeanClassLoaderAwareJCacheRegionFactory;
import io.github.jhipster.config.JHipsterProperties;

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
        BeanClassLoaderAwareJCacheRegionFactory.setBeanClassLoader(this.getClass().getClassLoader());
        JHipsterProperties.Cache.Ehcache ehcache =
            jHipsterProperties.getCache().getEhcache();

        jcacheConfiguration = Eh107Configuration.fromEhcacheCacheConfiguration(
            CacheConfigurationBuilder.newCacheConfigurationBuilder(Object.class, Object.class,
                ResourcePoolsBuilder.heap(ehcache.getMaxEntries()))
                .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofSeconds(ehcache.getTimeToLiveSeconds())))
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
            cm.createCache(de.tum.in.www1.artemis.domain.Statistic.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizPointStatistic.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizPointStatistic.class.getName() + ".pointCounters", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuestionStatistic.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.MultipleChoiceQuestionStatistic.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.MultipleChoiceQuestionStatistic.class.getName() + ".answerCounters", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropQuestionStatistic.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropQuestionStatistic.class.getName() + ".dropLocationCounters", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.StatisticCounter.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.PointCounter.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.AnswerCounter.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DropLocationCounter.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Course.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Course.class.getName() + ".exercises", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Exercise.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Exercise.class.getName() + ".participations", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.TextExercise.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.FileUploadExercise.class.getName(), jcacheConfiguration);
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
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropQuestion.class.getName() + ".correctMappings", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DropLocation.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragItem.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Participation.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Participation.class.getName() + ".results", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Participation.class.getName() + ".submissions", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.LtiUserId.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ExerciseResult.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ExerciseResult.class.getName() + ".feedbacks", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Feedback.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Submission.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ModelingSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizSubmission.class.getName() + ".submittedAnswers", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ProgrammingSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.TextSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.FileUploadSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropSubmittedAnswer.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropSubmittedAnswer.class.getName() + ".mappings", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropMapping.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ApollonDiagram.class.getName(), jcacheConfiguration);
            // jhipster-needle-ehcache-add-entry
        };
    }
}
