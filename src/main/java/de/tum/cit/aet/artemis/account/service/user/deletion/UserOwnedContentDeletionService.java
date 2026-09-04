package de.tum.cit.aet.artemis.account.service.user.deletion;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.AssessmentDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.CommunicationDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.CourseContextDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.ExamUserImagePaths;
import de.tum.cit.aet.artemis.account.repository.cleanup.ExerciseDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.LearningDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.PlatformDataCleanupRepository;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;

/**
 * Removes the content an account owns rather than merely points at.
 *
 * <p>
 * {@link UserReferenceCleanupService} resolves the direct foreign keys to {@code jhi_user}, one statement each. That is
 * not enough where the row it removes is itself the parent of other rows: a thread carries answers and reactions
 * written by other people, a complaint carries its response, an exam carries the sittings recorded for it. This service
 * takes those structures down from the leaves up, so that the reference policies then find nothing in their way.
 *
 * <p>
 * Everything here goes straight to the database rather than through the owning module's API. Artemis runs on a single
 * database, and an account has to be deletable whether or not the module that produced a row is enabled in this
 * deployment - the tables exist either way, and a disabled module must not leave rows behind that block the deletion.
 * The queries name entities and fields, so a rename stops the build rather than the deletion.
 *
 * <p>
 * Only an administrator-confirmed deletion reaches this service. Automatic and provisional deletion require that no
 * business-domain reference is left, so there is nothing here for them to take down.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class UserOwnedContentDeletionService {

    private final UserRepository userRepository;

    private final ParticipationDeletionService participationDeletionService;

    private final CommunicationDataCleanupRepository communicationDataCleanupRepository;

    private final AssessmentDataCleanupRepository assessmentDataCleanupRepository;

    private final ExerciseDataCleanupRepository exerciseDataCleanupRepository;

    private final CourseContextDataCleanupRepository courseContextDataCleanupRepository;

    private final PlatformDataCleanupRepository platformDataCleanupRepository;

    private final LearningDataCleanupRepository learningDataCleanupRepository;

    public UserOwnedContentDeletionService(UserRepository userRepository, ParticipationDeletionService participationDeletionService,
            CommunicationDataCleanupRepository communicationDataCleanupRepository, AssessmentDataCleanupRepository assessmentDataCleanupRepository,
            ExerciseDataCleanupRepository exerciseDataCleanupRepository, CourseContextDataCleanupRepository courseContextDataCleanupRepository,
            PlatformDataCleanupRepository platformDataCleanupRepository, LearningDataCleanupRepository learningDataCleanupRepository) {
        this.userRepository = userRepository;
        this.participationDeletionService = participationDeletionService;
        this.communicationDataCleanupRepository = communicationDataCleanupRepository;
        this.assessmentDataCleanupRepository = assessmentDataCleanupRepository;
        this.exerciseDataCleanupRepository = exerciseDataCleanupRepository;
        this.courseContextDataCleanupRepository = courseContextDataCleanupRepository;
        this.platformDataCleanupRepository = platformDataCleanupRepository;
        this.learningDataCleanupRepository = learningDataCleanupRepository;
    }

    /**
     * Removes the account's data exports and reports the archives they left on disk.
     *
     * @param userId the account being deleted
     * @return the archive paths the caller has to delete once the database work is done
     */
    public List<Path> deleteDataExports(long userId) {
        List<Path> archivePaths = platformDataCleanupRepository.findDataExportFilePaths(userId).stream().map(Path::of).toList();
        platformDataCleanupRepository.deleteDataExports(userId);
        return archivePaths;
    }

    /**
     * Removes the account's learner profile, per-course parts first, after the account has let go of it.
     *
     * @param userId           the account being deleted
     * @param learnerProfileId its learner profile
     */
    public void deleteLearnerProfile(long userId, long learnerProfileId) {
        userRepository.clearLearnerProfileForDeletion(userId);
        learningDataCleanupRepository.deleteCourseLearnerProfiles(learnerProfileId);
        learningDataCleanupRepository.deleteLearnerProfile(learnerProfileId);
    }

    /**
     * Hands over or removes the teams the account was part of.
     *
     * <p>
     * A team that has other members carries on under one of them. A team the account was the only member of goes with
     * it, together with the work the team submitted.
     *
     * @param userId the account being deleted
     */
    public void deleteTeams(long userId) {
        List<Long> exclusivelyOwnedTeamIds = exerciseDataCleanupRepository.findExclusivelyOwnedTeamIds(userId);

        for (long teamId : exerciseDataCleanupRepository.findOwnedTeamIds(userId)) {
            List<Long> remainingStudents = exerciseDataCleanupRepository.findRemainingTeamStudentIds(teamId, userId);
            if (remainingStudents.isEmpty()) {
                exerciseDataCleanupRepository.detachTeamOwner(teamId);
            }
            else {
                exerciseDataCleanupRepository.replaceTeamOwner(teamId, remainingStudents.getFirst());
            }
        }

        exerciseDataCleanupRepository.deleteTeamMemberships(userId);
        for (long teamId : exclusivelyOwnedTeamIds) {
            participationDeletionService.deleteAllByTeamId(teamId);
            exerciseDataCleanupRepository.deleteTeam(teamId);
        }
    }

    /**
     * Removes the account's participations one at a time, because each owns submissions, results and, for a
     * programming exercise, a repository and a build plan outside the database.
     *
     * @param userId the account being deleted
     */
    public void deleteParticipations(long userId) {
        exerciseDataCleanupRepository.findStudentParticipationIds(userId).forEach(participationId -> participationDeletionService.delete(participationId, true));
    }

    /**
     * Removes the account's exams and exam registrations and reports the personal images they left on disk.
     *
     * @param userId the account being deleted
     * @return the image paths the caller has to delete once the database work is done
     */
    public List<Path> deleteExamAttendance(long userId) {
        courseContextDataCleanupRepository.deleteExamSessions(userId);
        courseContextDataCleanupRepository.deleteStudentExamExerciseLinks(userId);
        courseContextDataCleanupRepository.deleteStudentExams(userId);

        List<Path> imagePaths = new ArrayList<>();
        for (ExamUserImagePaths registration : courseContextDataCleanupRepository.findExamUserImagePaths(userId)) {
            addImagePath(imagePaths, registration.getSigningImagePath(), FilePathType.EXAM_USER_SIGNATURE);
            addImagePath(imagePaths, registration.getStudentImagePath(), FilePathType.EXAM_USER_IMAGE);
        }
        courseContextDataCleanupRepository.deleteExamRegistrations(userId);
        return imagePaths;
    }

    private static void addImagePath(List<Path> imagePaths, @Nullable String imageUri, FilePathType filePathType) {
        if (imageUri != null) {
            imagePaths.add(FilePathConverter.fileSystemPathForExternalUri(URI.create(imageUri), filePathType));
        }
    }

    /**
     * Removes the complaints the account raised, together with the responses they were given.
     *
     * @param userId the account being deleted
     */
    public void deleteComplaints(long userId) {
        assessmentDataCleanupRepository.deleteResponsesToComplaintsOf(userId);
        assessmentDataCleanupRepository.deleteComplaints(userId);
    }

    /**
     * Removes the plagiarism cases the account is the subject of, together with the discussion held on them.
     *
     * <p>
     * The compared submissions are detached rather than deleted: they are evidence in a comparison that also concerns
     * the other student.
     *
     * @param userId the account being deleted
     */
    public void deletePlagiarismCases(long userId) {
        communicationDataCleanupRepository.deleteReactionsOnPlagiarismCaseAnswers(userId);
        communicationDataCleanupRepository.deletePlagiarismCaseAnswers(userId);
        communicationDataCleanupRepository.deleteReactionsOnPlagiarismCasePosts(userId);
        communicationDataCleanupRepository.deletePlagiarismCasePosts(userId);
        platformDataCleanupRepository.detachPlagiarismSubmissions(userId);
        platformDataCleanupRepository.deletePlagiarismCases(userId);
    }

    /**
     * Removes what the account wrote and the discussion that grew below it.
     *
     * <p>
     * The answers and reactions other people left on a thread the account started go with the thread: a message board
     * cannot keep a reply to something that is no longer there.
     *
     * @param userId the account being deleted
     */
    public void deleteCommunicationContent(long userId) {
        communicationDataCleanupRepository.deleteReactionsOnAnswersAuthoredBy(userId);
        communicationDataCleanupRepository.deleteAnswersAuthoredBy(userId);
        communicationDataCleanupRepository.deleteReactionsOnPostsAuthoredBy(userId);
        communicationDataCleanupRepository.deletePosts(userId);
        communicationDataCleanupRepository.deleteReactions(userId);
    }

    /**
     * Removes the account's tutor participations, together with the links to the example submissions it trained on.
     *
     * @param userId the account being deleted
     */
    public void deleteTutorParticipations(long userId) {
        assessmentDataCleanupRepository.deleteTrainedExampleSubmissionLinks(userId);
        assessmentDataCleanupRepository.deleteTutorParticipations(userId);
    }

    /**
     * Renames the account behind the research events it produced, which record a login rather than a foreign key.
     * Without this the events would keep pointing at a name that could later be handed to somebody else.
     *
     * @param login  the login the account had
     * @param userId the account being deleted, which makes the placeholder unique
     */
    public void anonymiseScienceEvents(String login, long userId) {
        learningDataCleanupRepository.renameScienceEventIdentity(login, "deleted-user-" + userId);
    }
}
