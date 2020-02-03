package de.tum.in.www1.artemis.config;

import java.time.Duration;

import javax.cache.CacheManager;

import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.jsr107.Eh107Configuration;
import org.hibernate.cache.jcache.ConfigSettings;
import org.springframework.boot.autoconfigure.cache.JCacheManagerCustomizer;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.*;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.UserRepository;
import io.github.jhipster.config.JHipsterProperties;

@Configuration
@EnableCaching
public class CacheConfiguration {

    private final javax.cache.configuration.Configuration<Object, Object> jcacheConfiguration;

    public CacheConfiguration(JHipsterProperties jHipsterProperties) {
        JHipsterProperties.Cache.Ehcache ehcache = jHipsterProperties.getCache().getEhcache();

        jcacheConfiguration = Eh107Configuration.fromEhcacheCacheConfiguration(
                CacheConfigurationBuilder.newCacheConfigurationBuilder(Object.class, Object.class, ResourcePoolsBuilder.heap(ehcache.getMaxEntries()))
                        .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofSeconds(ehcache.getTimeToLiveSeconds()))).build());
    }

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer(javax.cache.CacheManager cacheManager) {
        return hibernateProperties -> hibernateProperties.put(ConfigSettings.CACHE_MANAGER, cacheManager);
    }

    @Bean
    public JCacheManagerCustomizer cacheManagerCustomizer() {
        return cm -> {
            createIfNotExists(cm, UserRepository.USERS_CACHE, jcacheConfiguration);
            createIfNotExists(cm, User.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, Authority.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, User.class.getName() + ".authorities", jcacheConfiguration);
            createIfNotExists(cm, User.class.getName() + ".persistentTokens", jcacheConfiguration);
            createIfNotExists(cm, Course.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, Course.class.getName() + ".exercises", jcacheConfiguration);
            createIfNotExists(cm, Exercise.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, Exercise.class.getName() + ".studentParticipations", jcacheConfiguration);
            createIfNotExists(cm, Exercise.class.getName() + ".exampleSubmissions", jcacheConfiguration);
            createIfNotExists(cm, LtiOutcomeUrl.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, LtiUserId.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, Participation.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, Participation.class.getName() + ".results", jcacheConfiguration);
            createIfNotExists(cm, Result.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, ProgrammingExercise.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, ModelingExercise.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, QuizExercise.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, QuizExercise.class.getName() + ".quizQuestions", jcacheConfiguration);
            createIfNotExists(cm, SubmittedAnswer.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, QuizQuestion.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, MultipleChoiceQuestion.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, MultipleChoiceQuestion.class.getName() + ".answerOptions", jcacheConfiguration);
            createIfNotExists(cm, AnswerOption.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, MultipleChoiceSubmittedAnswer.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, MultipleChoiceSubmittedAnswer.class.getName() + ".selectedOptions", jcacheConfiguration);
            createIfNotExists(cm, DragAndDropQuestion.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, DragAndDropQuestion.class.getName() + ".dropLocations", jcacheConfiguration);
            createIfNotExists(cm, DragAndDropQuestion.class.getName() + ".dragItems", jcacheConfiguration);
            createIfNotExists(cm, DragAndDropQuestion.class.getName() + ".correctMappings", jcacheConfiguration);
            createIfNotExists(cm, DropLocation.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, DragItem.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, Submission.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, ModelingSubmission.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, QuizSubmission.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, QuizSubmission.class.getName() + ".submittedAnswers", jcacheConfiguration);
            createIfNotExists(cm, ProgrammingSubmission.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, TextSubmission.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, FileUploadSubmission.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, DragAndDropSubmittedAnswer.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, QuizQuestion.class.getName() + ".quizExercises", jcacheConfiguration);
            createIfNotExists(cm, Result.class.getName() + ".feedbacks", jcacheConfiguration);
            createIfNotExists(cm, Feedback.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, QuizStatistic.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, QuizPointStatistic.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, QuizQuestionStatistic.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, AnswerCounter.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, DropLocationCounter.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, QuizPointStatistic.class.getName() + ".ratedPointCounters", jcacheConfiguration);
            createIfNotExists(cm, QuizPointStatistic.class.getName() + ".unRatedPointCounters", jcacheConfiguration);
            createIfNotExists(cm, MultipleChoiceQuestionStatistic.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, MultipleChoiceQuestionStatistic.class.getName() + ".ratedAnswerCounters", jcacheConfiguration);
            createIfNotExists(cm, MultipleChoiceQuestionStatistic.class.getName() + ".unRatedAnswerCounters", jcacheConfiguration);
            createIfNotExists(cm, DragAndDropQuestionStatistic.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, DragAndDropQuestionStatistic.class.getName() + ".ratedDropLocationCounters", jcacheConfiguration);
            createIfNotExists(cm, DragAndDropQuestionStatistic.class.getName() + ".unRatedDropLocationCounters", jcacheConfiguration);
            createIfNotExists(cm, QuizStatisticCounter.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, PointCounter.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, QuizPointStatistic.class.getName() + ".pointCounters", jcacheConfiguration);
            createIfNotExists(cm, MultipleChoiceQuestionStatistic.class.getName() + ".answerCounters", jcacheConfiguration);
            createIfNotExists(cm, DragAndDropQuestionStatistic.class.getName() + ".dropLocationCounters", jcacheConfiguration);
            createIfNotExists(cm, DragItem.class.getName() + ".mappings", jcacheConfiguration);
            createIfNotExists(cm, DropLocation.class.getName() + ".mappings", jcacheConfiguration);
            createIfNotExists(cm, DragAndDropSubmittedAnswer.class.getName() + ".mappings", jcacheConfiguration);
            createIfNotExists(cm, DragAndDropMapping.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, ApollonDiagram.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, Participation.class.getName() + ".submissions", jcacheConfiguration);
            createIfNotExists(cm, TextExercise.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, FileUploadExercise.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, ShortAnswerQuestionStatistic.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, ShortAnswerQuestionStatistic.class.getName() + ".shortAnswerSpotCounters", jcacheConfiguration);
            createIfNotExists(cm, ShortAnswerSpotCounter.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, ShortAnswerQuestion.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, ShortAnswerQuestion.class.getName() + ".spots", jcacheConfiguration);
            createIfNotExists(cm, ShortAnswerQuestion.class.getName() + ".solutions", jcacheConfiguration);
            createIfNotExists(cm, ShortAnswerQuestion.class.getName() + ".correctMappings", jcacheConfiguration);
            createIfNotExists(cm, ShortAnswerSpot.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, ShortAnswerSolution.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, ShortAnswerSubmittedAnswer.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, ShortAnswerSubmittedAnswer.class.getName() + ".submittedTexts", jcacheConfiguration);
            createIfNotExists(cm, ShortAnswerSubmittedText.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, ShortAnswerMapping.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, ShortAnswerSpot.class.getName() + ".mappings", jcacheConfiguration);
            createIfNotExists(cm, ShortAnswerSolution.class.getName() + ".mappings", jcacheConfiguration);
            createIfNotExists(cm, Complaint.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, ComplaintResponse.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, TutorParticipation.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, TutorParticipation.class.getName() + ".trainedExampleSubmissions", jcacheConfiguration);
            createIfNotExists(cm, ExampleSubmission.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, Exercise.class.getName() + ".tutorParticipations", jcacheConfiguration);
            createIfNotExists(cm, Course.class.getName() + ".lectures", jcacheConfiguration);
            createIfNotExists(cm, Course.class.getName() + ".tutorGroups", jcacheConfiguration);
            createIfNotExists(cm, Exercise.class.getName() + ".attachments", jcacheConfiguration);
            createIfNotExists(cm, StudentQuestion.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, StudentQuestion.class.getName() + ".answers", jcacheConfiguration);
            createIfNotExists(cm, StudentQuestionAnswer.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, TutorGroup.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, TutorGroup.class.getName() + ".students", jcacheConfiguration);
            createIfNotExists(cm, Notification.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, SystemNotification.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, GroupNotification.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, SingleUserNotification.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, Lecture.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, Lecture.class.getName() + ".attachments", jcacheConfiguration);
            createIfNotExists(cm, Attachment.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, Exercise.class.getName() + ".studentQuestions", jcacheConfiguration);
            createIfNotExists(cm, Lecture.class.getName() + ".studentQuestions", jcacheConfiguration);
            createIfNotExists(cm, ModelAssessmentConflict.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, ModelAssessmentConflict.class.getName() + ".resultsInConflict", jcacheConfiguration);
            createIfNotExists(cm, ConflictingResult.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, ExampleSubmission.class.getName() + ".tutorParticipations", jcacheConfiguration);
            createIfNotExists(cm, ProgrammingExerciseTestCase.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, ExerciseHint.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, GuidedTourSetting.class.getName(), jcacheConfiguration);
            createIfNotExists(cm, User.class.getName() + ".guidedTourSettings", jcacheConfiguration);
            // jhipster-needle-ehcache-add-entry
            createIfNotExists(cm, "files", jcacheConfiguration);
        };
    }

    // This method is a hotfix for the issue described in: https://github.com/jhipster/generator-jhipster/issues/5354.
    // During the execution of tests, spring boot will try to instantiate the same cache multiple times, leading to an error.
    // This issue appears if e.g. a MockBean is used in a test.
    private void createIfNotExists(CacheManager cacheManager, String cacheName, javax.cache.configuration.Configuration<Object, Object> cacheConfiguration) {
        if (cacheManager.getCache(cacheName) == null) {
            cacheManager.createCache(cacheName, cacheConfiguration);
        }
    }
}
