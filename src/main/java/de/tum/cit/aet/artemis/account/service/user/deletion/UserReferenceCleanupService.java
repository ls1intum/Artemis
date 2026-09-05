package de.tum.cit.aet.artemis.account.service.user.deletion;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongToIntFunction;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.repository.cleanup.AccountDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.AssessmentDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.CommunicationDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.CourseContextDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.ExerciseDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.LearningDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.NotificationDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.PlatformDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.ProgrammingDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.UserReferenceCount;

/**
 * Binds every reference a permanent deletion has to resolve to the queries that count and resolve it.
 *
 * <p>
 * {@link UserDeletionReferencePolicy} says <em>what</em> a deletion touches, and a schema test holds that catalogue
 * against every foreign key to {@code jhi_user}. This service says <em>how</em>, and its own test holds it against the
 * catalogue. Together the two keep the pair honest: a new foreign key fails the first test until it has a policy, and
 * the second until that policy can actually be carried out.
 *
 * <p>
 * Each binding names an entity and a field rather than a table and a column, so renaming either stops the build instead
 * of the deletion.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class UserReferenceCleanupService {

    /**
     * What has to happen to one reference when the account it points at is deleted.
     *
     * @param count   how many rows each of the given accounts owns, grouped by account
     * @param resolve delete or detach the rows of one account, returning how many rows it changed
     */
    private record ReferenceCleanup(Function<Collection<Long>, List<UserReferenceCount>> count, LongToIntFunction resolve) {
    }

    private final Map<UserDeletionReferencePolicy, ReferenceCleanup> cleanups;

    public UserReferenceCleanupService(AccountDataCleanupRepository account, CommunicationDataCleanupRepository communication, AssessmentDataCleanupRepository assessment,
            ExerciseDataCleanupRepository exercise, NotificationDataCleanupRepository notification, ProgrammingDataCleanupRepository programming,
            LearningDataCleanupRepository learning, CourseContextDataCleanupRepository courseContext, PlatformDataCleanupRepository platform) {
        Map<UserDeletionReferencePolicy, ReferenceCleanup> bindings = new EnumMap<>(UserDeletionReferencePolicy.class);

        bindings.put(UserDeletionReferencePolicy.CONDUCT_AGREEMENT, bind(account::countConductAgreements, account::deleteConductAgreements));
        bindings.put(UserDeletionReferencePolicy.USER_ACTIVITY, bind(account::countActivities, account::deleteActivities));
        bindings.put(UserDeletionReferencePolicy.USER_AI_PREFERENCE, bind(account::countAiPreferences, account::deleteAiPreferences));
        bindings.put(UserDeletionReferencePolicy.RECOVERY_KEY, bind(account::countRecoveryKeys, account::deleteRecoveryKeys));
        bindings.put(UserDeletionReferencePolicy.PASSKEY, bind(account::countPasskeyCredentials, account::deletePasskeyCredentials));
        bindings.put(UserDeletionReferencePolicy.AUTHORITY, bind(account::countAuthorities, account::deleteAuthorities));
        bindings.put(UserDeletionReferencePolicy.ORGANIZATION_MEMBERSHIP, bind(account::countOrganizationMemberships, account::deleteOrganizationMemberships));

        bindings.put(UserDeletionReferencePolicy.POST_AUTHOR, bind(communication::countPosts, communication::deletePosts));
        bindings.put(UserDeletionReferencePolicy.ANSWER_POST_AUTHOR, bind(communication::countAnswerPosts, communication::deleteAnswerPosts));
        bindings.put(UserDeletionReferencePolicy.ANSWER_POST_VERIFIER, bind(communication::countVerifiedAnswerPosts, communication::detachVerifiedAnswerPosts));
        bindings.put(UserDeletionReferencePolicy.REACTION_AUTHOR, bind(communication::countReactions, communication::deleteReactions));
        bindings.put(UserDeletionReferencePolicy.SAVED_POST, bind(communication::countSavedPosts, communication::deleteSavedPosts));
        bindings.put(UserDeletionReferencePolicy.CONVERSATION_CREATOR, bind(communication::countCreatedConversations, communication::detachCreatedConversations));
        bindings.put(UserDeletionReferencePolicy.CONVERSATION_MEMBERSHIP, bind(communication::countConversationMemberships, communication::deleteConversationMemberships));

        bindings.put(UserDeletionReferencePolicy.COMPLAINT_STUDENT, bind(assessment::countComplaints, assessment::deleteComplaints));
        bindings.put(UserDeletionReferencePolicy.COMPLAINT_REVIEWER, bind(assessment::countReviewedComplaintResponses, assessment::detachReviewedComplaintResponses));
        bindings.put(UserDeletionReferencePolicy.ASSESSMENT_NOTE_CREATOR, bind(assessment::countCreatedAssessmentNotes, assessment::detachCreatedAssessmentNotes));
        bindings.put(UserDeletionReferencePolicy.RESULT_ASSESSOR, bind(assessment::countAssessedResults, assessment::detachAssessedResults));
        bindings.put(UserDeletionReferencePolicy.PARTICIPANT_SCORE, bind(assessment::countStudentScores, assessment::deleteStudentScores));
        bindings.put(UserDeletionReferencePolicy.TUTOR_PARTICIPATION, bind(assessment::countTutorParticipations, assessment::deleteTutorParticipations));

        bindings.put(UserDeletionReferencePolicy.SUBMISSION_VERSION_AUTHOR, bind(exercise::countSubmissionVersions, exercise::deleteSubmissionVersions));
        bindings.put(UserDeletionReferencePolicy.EXERCISE_VERSION_AUTHOR, bind(exercise::countExerciseVersions, exercise::deleteExerciseVersions));
        bindings.put(UserDeletionReferencePolicy.REVIEW_COMMENT_AUTHOR, bind(exercise::countReviewComments, exercise::detachReviewComments));
        bindings.put(UserDeletionReferencePolicy.TEAM_OWNER, bind(exercise::countOwnedTeams, exercise::detachOwnedTeams));
        bindings.put(UserDeletionReferencePolicy.PARTICIPATION, bind(exercise::countStudentParticipations, exercise::deleteStudentParticipations));
        bindings.put(UserDeletionReferencePolicy.TEAM_MEMBERSHIP, bind(exercise::countTeamMemberships, exercise::deleteTeamMemberships));

        bindings.put(UserDeletionReferencePolicy.GLOBAL_NOTIFICATION_SETTING, bind(notification::countGlobalSettings, notification::deleteGlobalSettings));
        bindings.put(UserDeletionReferencePolicy.PUSH_NOTIFICATION_DEVICE, bind(notification::countPushDevices, notification::deletePushDevices));
        bindings.put(UserDeletionReferencePolicy.COURSE_NOTIFICATION_PRESET, bind(notification::countCoursePresets, notification::deleteCoursePresets));
        bindings.put(UserDeletionReferencePolicy.COURSE_NOTIFICATION_SPECIFICATION, bind(notification::countCourseSpecifications, notification::deleteCourseSpecifications));
        bindings.put(UserDeletionReferencePolicy.COURSE_NOTIFICATION_STATUS, bind(notification::countCourseStatuses, notification::deleteCourseStatuses));

        bindings.put(UserDeletionReferencePolicy.PERSONAL_VCS_TOKEN, bind(programming::countPersonalAccessTokens, programming::deletePersonalAccessTokens));
        bindings.put(UserDeletionReferencePolicy.PARTICIPATION_TOKEN, bind(programming::countParticipationAccessTokens, programming::deleteParticipationAccessTokens));
        bindings.put(UserDeletionReferencePolicy.REPOSITORY_TOKEN, bind(programming::countRepositoryAccessTokens, programming::deleteRepositoryAccessTokens));
        bindings.put(UserDeletionReferencePolicy.SSH_PUBLIC_KEY, bind(programming::countSshPublicKeys, programming::deleteSshPublicKeys));
        bindings.put(UserDeletionReferencePolicy.IDE_MAPPING, bind(programming::countIdeMappings, programming::deleteIdeMappings));

        bindings.put(UserDeletionReferencePolicy.QUIZ_QUESTION_PROGRESS, bind(learning::countQuizQuestionProgress, learning::deleteQuizQuestionProgress));
        bindings.put(UserDeletionReferencePolicy.QUIZ_TRAINING_LEADERBOARD, bind(learning::countQuizLeaderboardEntries, learning::deleteQuizLeaderboardEntries));
        bindings.put(UserDeletionReferencePolicy.COMPETENCY_PROGRESS, bind(learning::countCompetencyProgress, learning::deleteCompetencyProgress));
        bindings.put(UserDeletionReferencePolicy.LECTURE_PROGRESS, bind(learning::countLectureUnitCompletions, learning::deleteLectureUnitCompletions));

        bindings.put(UserDeletionReferencePolicy.COURSE_ROLE, bind(courseContext::countCourseRoles, courseContext::deleteCourseRoles));
        bindings.put(UserDeletionReferencePolicy.COURSE_REQUEST, bind(courseContext::countCourseRequests, courseContext::deleteCourseRequests));
        bindings.put(UserDeletionReferencePolicy.CALENDAR_SUBSCRIPTION, bind(courseContext::countCalendarSubscriptions, courseContext::deleteCalendarSubscriptions));
        bindings.put(UserDeletionReferencePolicy.EXAM_REGISTRATION, bind(courseContext::countExamRegistrations, courseContext::deleteExamRegistrations));
        bindings.put(UserDeletionReferencePolicy.STUDENT_EXAM, bind(courseContext::countStudentExams, courseContext::deleteStudentExams));
        bindings.put(UserDeletionReferencePolicy.TUTORIAL_GROUP_REGISTRATION,
                bind(courseContext::countTutorialGroupRegistrations, courseContext::deleteTutorialGroupRegistrations));
        bindings.put(UserDeletionReferencePolicy.TUTORIAL_GROUP_TEACHING_ASSISTANT, bind(courseContext::countTaughtTutorialGroups, courseContext::detachTaughtTutorialGroups));

        bindings.put(UserDeletionReferencePolicy.DATA_EXPORT, bind(platform::countDataExports, platform::deleteDataExports));
        bindings.put(UserDeletionReferencePolicy.LLM_USAGE_ACTOR, bind(platform::countLlmTokenUsageTraces, platform::detachLlmTokenUsageTraces));
        bindings.put(UserDeletionReferencePolicy.PLAGIARISM_CASE_STUDENT, bind(platform::countPlagiarismCases, platform::deletePlagiarismCases));
        bindings.put(UserDeletionReferencePolicy.PLAGIARISM_VERDICT_AUTHOR, bind(platform::countPlagiarismVerdicts, platform::detachPlagiarismVerdicts));
        bindings.put(UserDeletionReferencePolicy.LTI_LAUNCH, bind(platform::countLtiResourceLaunches, platform::deleteLtiResourceLaunches));
        bindings.put(UserDeletionReferencePolicy.USER_LTI_IDENTITY, bind(platform::countLtiIdentities, platform::deleteLtiIdentities));
        bindings.put(UserDeletionReferencePolicy.IRIS_SESSION, bind(platform::countIrisSessions, platform::deleteIrisSessions));

        this.cleanups = bindings;
    }

    private static ReferenceCleanup bind(Function<Collection<Long>, List<UserReferenceCount>> count, LongToIntFunction resolve) {
        return new ReferenceCleanup(count, resolve);
    }

    /**
     * The references this service can carry out, which its test holds against the policy catalogue.
     *
     * @return every bound policy
     */
    public Set<UserDeletionReferencePolicy> boundPolicies() {
        return Set.copyOf(cleanups.keySet());
    }

    /**
     * Counts how many rows each of the given accounts owns for one reference, in a single query.
     *
     * @param policy  the reference to count
     * @param userIds the accounts to count for
     * @return the count per account, holding only the accounts that own any rows
     */
    public Map<Long, Long> count(UserDeletionReferencePolicy policy, Collection<Long> userIds) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        if (userIds.isEmpty()) {
            // An empty collection would be expanded into an empty IN list, which a native query cannot express.
            return counts;
        }
        for (UserReferenceCount count : cleanup(policy).count().apply(userIds)) {
            counts.put(count.getUserId(), count.getCount());
        }
        return counts;
    }

    /**
     * Deletes or detaches the rows one account owns for one reference, as that reference's policy prescribes.
     *
     * @param policy the reference to resolve
     * @param userId the account being deleted
     * @return how many rows changed
     */
    public int resolve(UserDeletionReferencePolicy policy, long userId) {
        return cleanup(policy).resolve().applyAsInt(userId);
    }

    private ReferenceCleanup cleanup(UserDeletionReferencePolicy policy) {
        ReferenceCleanup cleanup = cleanups.get(policy);
        if (cleanup == null) {
            throw new IllegalStateException("No cleanup is bound for " + policy + ", so an account cannot be deleted without leaving its rows behind");
        }
        return cleanup;
    }
}
