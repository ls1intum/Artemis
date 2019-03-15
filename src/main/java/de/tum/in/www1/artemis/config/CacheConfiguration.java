package de.tum.in.www1.artemis.config;

import de.tum.in.www1.artemis.repository.UserRepository;
import io.github.jhipster.config.JHipsterProperties;
import io.github.jhipster.config.jcache.BeanClassLoaderAwareJCacheRegionFactory;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.jsr107.Eh107Configuration;
import org.springframework.boot.autoconfigure.cache.JCacheManagerCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

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
            cm.createCache(UserRepository.USERS_CACHE, jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.User.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Authority.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.User.class.getName() + ".authorities", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.PersistentToken.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.User.class.getName() + ".persistentTokens", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Course.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Course.class.getName() + ".exercises", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Exercise.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Exercise.class.getName() + ".participations", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Exercise.class.getName() + ".exampleSubmissions", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.LtiOutcomeUrl.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.LtiUserId.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Participation.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Participation.class.getName() + ".results", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Result.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ProgrammingExercise.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ModelingExercise.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizExercise.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizExercise.class.getName() + ".quizQuestions", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.SubmittedAnswer.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizQuestion.class.getName(), jcacheConfiguration);
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
            cm.createCache(de.tum.in.www1.artemis.domain.Submission.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ModelingSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizSubmission.class.getName() + ".submittedAnswers", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ProgrammingSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.TextSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.FileUploadSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropSubmittedAnswer.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizQuestion.class.getName() + ".quizExercises", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Result.class.getName() + ".feedbacks", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Feedback.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizStatistic.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizPointStatistic.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizQuestionStatistic.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.AnswerCounter.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DropLocationCounter.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizPointStatistic.class.getName() + ".ratedPointCounters", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizPointStatistic.class.getName() + ".unRatedPointCounters", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.MultipleChoiceQuestionStatistic.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.MultipleChoiceQuestionStatistic.class.getName() + ".ratedAnswerCounters", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.MultipleChoiceQuestionStatistic.class.getName() + ".unRatedAnswerCounters", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropQuestionStatistic.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropQuestionStatistic.class.getName() + ".ratedDropLocationCounters", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropQuestionStatistic.class.getName() + ".unRatedDropLocationCounters", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizStatisticCounter.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.PointCounter.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.QuizPointStatistic.class.getName() + ".pointCounters", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.MultipleChoiceQuestionStatistic.class.getName() + ".answerCounters", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropQuestionStatistic.class.getName() + ".dropLocationCounters", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragItem.class.getName() + ".mappings", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DropLocation.class.getName() + ".mappings", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropSubmittedAnswer.class.getName() + ".mappings", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.DragAndDropMapping.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ApollonDiagram.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Participation.class.getName() + ".submissions", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.TextExercise.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.FileUploadExercise.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ShortAnswerQuestionStatistic.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ShortAnswerQuestionStatistic.class.getName() + ".shortAnswerSpotCounters", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ShortAnswerSpotCounter.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ShortAnswerQuestion.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ShortAnswerQuestion.class.getName() + ".spots", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ShortAnswerQuestion.class.getName() + ".solutions", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ShortAnswerQuestion.class.getName() + ".correctMappings", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ShortAnswerSpot.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ShortAnswerSolution.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ShortAnswerSubmittedAnswer.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ShortAnswerSubmittedAnswer.class.getName() + ".submittedTexts", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ShortAnswerSubmittedText.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ShortAnswerMapping.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ShortAnswerSpot.class.getName() + ".mappings", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ShortAnswerSolution.class.getName() + ".mappings", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Complaint.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ComplaintResponse.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.TutorParticipation.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.TutorParticipation.class.getName() + ".trainedExampleSubmissions", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ExampleSubmission.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.ExampleSubmission.class.getName() + ".exercises", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Exercise.class.getName() + ".tutorParticipations", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Course.class.getName() + ".lectures", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Course.class.getName() + ".tutorGroups", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Exercise.class.getName() + ".attachments", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.StudentQuestion.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.StudentQuestion.class.getName() + ".answers", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.StudentQuestionAnswer.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.TutorGroup.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.TutorGroup.class.getName() + ".students", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Notification.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.SystemNotification.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.GroupNotification.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.SingleUserNotification.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Lecture.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Lecture.class.getName() + ".attachments", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Attachment.class.getName(), jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Exercise.class.getName() + ".studentQuestions", jcacheConfiguration);
            cm.createCache(de.tum.in.www1.artemis.domain.Lecture.class.getName() + ".studentQuestions", jcacheConfiguration);

            // jhipster-needle-ehcache-add-entry
            cm.createCache("files", jcacheConfiguration);
        };
    }
}
