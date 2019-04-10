package de.tum.in.www1.artemis.config;

import java.time.Duration;

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
            cm.createCache(UserRepository.USERS_CACHE, jcacheConfiguration);
            cm.createCache(User.class.getName(), jcacheConfiguration);
            cm.createCache(Authority.class.getName(), jcacheConfiguration);
            cm.createCache(User.class.getName() + ".authorities", jcacheConfiguration);
            cm.createCache(PersistentToken.class.getName(), jcacheConfiguration);
            cm.createCache(User.class.getName() + ".persistentTokens", jcacheConfiguration);
            cm.createCache(Course.class.getName(), jcacheConfiguration);
            cm.createCache(Course.class.getName() + ".exercises", jcacheConfiguration);
            cm.createCache(Exercise.class.getName(), jcacheConfiguration);
            cm.createCache(Exercise.class.getName() + ".participations", jcacheConfiguration);
            cm.createCache(Exercise.class.getName() + ".exampleSubmissions", jcacheConfiguration);
            cm.createCache(LtiOutcomeUrl.class.getName(), jcacheConfiguration);
            cm.createCache(LtiUserId.class.getName(), jcacheConfiguration);
            cm.createCache(Participation.class.getName(), jcacheConfiguration);
            cm.createCache(Participation.class.getName() + ".results", jcacheConfiguration);
            cm.createCache(Result.class.getName(), jcacheConfiguration);
            cm.createCache(ProgrammingExercise.class.getName(), jcacheConfiguration);
            cm.createCache(ModelingExercise.class.getName(), jcacheConfiguration);
            cm.createCache(QuizExercise.class.getName(), jcacheConfiguration);
            cm.createCache(QuizExercise.class.getName() + ".quizQuestions", jcacheConfiguration);
            cm.createCache(SubmittedAnswer.class.getName(), jcacheConfiguration);
            cm.createCache(QuizQuestion.class.getName(), jcacheConfiguration);
            cm.createCache(MultipleChoiceQuestion.class.getName(), jcacheConfiguration);
            cm.createCache(MultipleChoiceQuestion.class.getName() + ".answerOptions", jcacheConfiguration);
            cm.createCache(AnswerOption.class.getName(), jcacheConfiguration);
            cm.createCache(MultipleChoiceSubmittedAnswer.class.getName(), jcacheConfiguration);
            cm.createCache(MultipleChoiceSubmittedAnswer.class.getName() + ".selectedOptions", jcacheConfiguration);
            cm.createCache(DragAndDropQuestion.class.getName(), jcacheConfiguration);
            cm.createCache(DragAndDropQuestion.class.getName() + ".dropLocations", jcacheConfiguration);
            cm.createCache(DragAndDropQuestion.class.getName() + ".dragItems", jcacheConfiguration);
            cm.createCache(DragAndDropQuestion.class.getName() + ".correctMappings", jcacheConfiguration);
            cm.createCache(DropLocation.class.getName(), jcacheConfiguration);
            cm.createCache(DragItem.class.getName(), jcacheConfiguration);
            cm.createCache(Submission.class.getName(), jcacheConfiguration);
            cm.createCache(ModelingSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(QuizSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(QuizSubmission.class.getName() + ".submittedAnswers", jcacheConfiguration);
            cm.createCache(ProgrammingSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(TextSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(FileUploadSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(DragAndDropSubmittedAnswer.class.getName(), jcacheConfiguration);
            cm.createCache(QuizQuestion.class.getName() + ".quizExercises", jcacheConfiguration);
            cm.createCache(Result.class.getName() + ".feedbacks", jcacheConfiguration);
            cm.createCache(Feedback.class.getName(), jcacheConfiguration);
            cm.createCache(QuizStatistic.class.getName(), jcacheConfiguration);
            cm.createCache(QuizPointStatistic.class.getName(), jcacheConfiguration);
            cm.createCache(QuizQuestionStatistic.class.getName(), jcacheConfiguration);
            cm.createCache(AnswerCounter.class.getName(), jcacheConfiguration);
            cm.createCache(DropLocationCounter.class.getName(), jcacheConfiguration);
            cm.createCache(QuizPointStatistic.class.getName() + ".ratedPointCounters", jcacheConfiguration);
            cm.createCache(QuizPointStatistic.class.getName() + ".unRatedPointCounters", jcacheConfiguration);
            cm.createCache(MultipleChoiceQuestionStatistic.class.getName(), jcacheConfiguration);
            cm.createCache(MultipleChoiceQuestionStatistic.class.getName() + ".ratedAnswerCounters", jcacheConfiguration);
            cm.createCache(MultipleChoiceQuestionStatistic.class.getName() + ".unRatedAnswerCounters", jcacheConfiguration);
            cm.createCache(DragAndDropQuestionStatistic.class.getName(), jcacheConfiguration);
            cm.createCache(DragAndDropQuestionStatistic.class.getName() + ".ratedDropLocationCounters", jcacheConfiguration);
            cm.createCache(DragAndDropQuestionStatistic.class.getName() + ".unRatedDropLocationCounters", jcacheConfiguration);
            cm.createCache(QuizStatisticCounter.class.getName(), jcacheConfiguration);
            cm.createCache(PointCounter.class.getName(), jcacheConfiguration);
            cm.createCache(QuizPointStatistic.class.getName() + ".pointCounters", jcacheConfiguration);
            cm.createCache(MultipleChoiceQuestionStatistic.class.getName() + ".answerCounters", jcacheConfiguration);
            cm.createCache(DragAndDropQuestionStatistic.class.getName() + ".dropLocationCounters", jcacheConfiguration);
            cm.createCache(DragItem.class.getName() + ".mappings", jcacheConfiguration);
            cm.createCache(DropLocation.class.getName() + ".mappings", jcacheConfiguration);
            cm.createCache(DragAndDropSubmittedAnswer.class.getName() + ".mappings", jcacheConfiguration);
            cm.createCache(DragAndDropMapping.class.getName(), jcacheConfiguration);
            cm.createCache(ApollonDiagram.class.getName(), jcacheConfiguration);
            cm.createCache(Participation.class.getName() + ".submissions", jcacheConfiguration);
            cm.createCache(TextExercise.class.getName(), jcacheConfiguration);
            cm.createCache(FileUploadExercise.class.getName(), jcacheConfiguration);
            cm.createCache(ShortAnswerQuestionStatistic.class.getName(), jcacheConfiguration);
            cm.createCache(ShortAnswerQuestionStatistic.class.getName() + ".shortAnswerSpotCounters", jcacheConfiguration);
            cm.createCache(ShortAnswerSpotCounter.class.getName(), jcacheConfiguration);
            cm.createCache(ShortAnswerQuestion.class.getName(), jcacheConfiguration);
            cm.createCache(ShortAnswerQuestion.class.getName() + ".spots", jcacheConfiguration);
            cm.createCache(ShortAnswerQuestion.class.getName() + ".solutions", jcacheConfiguration);
            cm.createCache(ShortAnswerQuestion.class.getName() + ".correctMappings", jcacheConfiguration);
            cm.createCache(ShortAnswerSpot.class.getName(), jcacheConfiguration);
            cm.createCache(ShortAnswerSolution.class.getName(), jcacheConfiguration);
            cm.createCache(ShortAnswerSubmittedAnswer.class.getName(), jcacheConfiguration);
            cm.createCache(ShortAnswerSubmittedAnswer.class.getName() + ".submittedTexts", jcacheConfiguration);
            cm.createCache(ShortAnswerSubmittedText.class.getName(), jcacheConfiguration);
            cm.createCache(ShortAnswerMapping.class.getName(), jcacheConfiguration);
            cm.createCache(ShortAnswerSpot.class.getName() + ".mappings", jcacheConfiguration);
            cm.createCache(ShortAnswerSolution.class.getName() + ".mappings", jcacheConfiguration);
            cm.createCache(Complaint.class.getName(), jcacheConfiguration);
            cm.createCache(ComplaintResponse.class.getName(), jcacheConfiguration);
            cm.createCache(TutorParticipation.class.getName(), jcacheConfiguration);
            cm.createCache(TutorParticipation.class.getName() + ".trainedExampleSubmissions", jcacheConfiguration);
            cm.createCache(ExampleSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(ExampleSubmission.class.getName() + ".exercises", jcacheConfiguration);
            cm.createCache(Exercise.class.getName() + ".tutorParticipations", jcacheConfiguration);
            cm.createCache(Course.class.getName() + ".lectures", jcacheConfiguration);
            cm.createCache(Course.class.getName() + ".tutorGroups", jcacheConfiguration);
            cm.createCache(Exercise.class.getName() + ".attachments", jcacheConfiguration);
            cm.createCache(StudentQuestion.class.getName(), jcacheConfiguration);
            cm.createCache(StudentQuestion.class.getName() + ".answers", jcacheConfiguration);
            cm.createCache(StudentQuestionAnswer.class.getName(), jcacheConfiguration);
            cm.createCache(TutorGroup.class.getName(), jcacheConfiguration);
            cm.createCache(TutorGroup.class.getName() + ".students", jcacheConfiguration);
            cm.createCache(Notification.class.getName(), jcacheConfiguration);
            cm.createCache(SystemNotification.class.getName(), jcacheConfiguration);
            cm.createCache(GroupNotification.class.getName(), jcacheConfiguration);
            cm.createCache(SingleUserNotification.class.getName(), jcacheConfiguration);
            cm.createCache(Lecture.class.getName(), jcacheConfiguration);
            cm.createCache(Lecture.class.getName() + ".attachments", jcacheConfiguration);
            cm.createCache(Attachment.class.getName(), jcacheConfiguration);
            cm.createCache(Exercise.class.getName() + ".studentQuestions", jcacheConfiguration);
            cm.createCache(Lecture.class.getName() + ".studentQuestions", jcacheConfiguration);
            cm.createCache(ModelAssessmentConflict.class.getName(), jcacheConfiguration);
            cm.createCache(ModelAssessmentConflict.class.getName() + ".resultsInConflict", jcacheConfiguration);
            cm.createCache(ConflictingResult.class.getName(), jcacheConfiguration);
            // jhipster-needle-ehcache-add-entry
            cm.createCache("files", jcacheConfiguration);
        };
    }

    private void createCache(javax.cache.CacheManager cm, String cacheName) {
        javax.cache.Cache<Object, Object> cache = cm.getCache(cacheName);
        if (cache != null) {
            cm.destroyCache(cacheName);
        }
        cm.createCache(cacheName, jcacheConfiguration);
    }
}
