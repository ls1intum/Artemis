package de.tum.cit.aet.artemis.service.plagiarism;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismSubmissionRepository;
import de.tum.cit.aet.artemis.service.notifications.SingleUserNotificationService;
import de.tum.cit.aet.artemis.web.rest.dto.plagiarism.PlagiarismCaseInfoDTO;
import de.tum.cit.aet.artemis.web.rest.dto.plagiarism.PlagiarismVerdictDTO;

@Profile(PROFILE_CORE)
@Service
public class PlagiarismCaseService {

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    private final UserRepository userRepository;

    private final SingleUserNotificationService singleUserNotificationService;

    private final PlagiarismSubmissionRepository plagiarismSubmissionRepository;

    public PlagiarismCaseService(PlagiarismCaseRepository plagiarismCaseRepository, PlagiarismComparisonRepository plagiarismComparisonRepository, UserRepository userRepository,
            SingleUserNotificationService singleUserNotificationService, PlagiarismSubmissionRepository plagiarismSubmissionRepository) {
        this.plagiarismCaseRepository = plagiarismCaseRepository;
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
        this.userRepository = userRepository;
        this.singleUserNotificationService = singleUserNotificationService;
        this.plagiarismSubmissionRepository = plagiarismSubmissionRepository;
    }

    /**
     * Save the verdict for a plagiarism case.
     * <ul>
     * <li>If the verdict is a point deduction additionally save the point deduction.</li>
     * <li>If the verdict is a warning additionally save the warning message.</li>
     * </ul>
     *
     * @param plagiarismCaseId     the ID of the plagiarism case for which to save the verdict
     * @param plagiarismVerdictDTO the verdict to be saved
     * @return the plagiarism case with the verdict
     */
    public PlagiarismCase updatePlagiarismCaseVerdict(long plagiarismCaseId, PlagiarismVerdictDTO plagiarismVerdictDTO) {
        PlagiarismCase plagiarismCase = plagiarismCaseRepository.findByIdElseThrow(plagiarismCaseId);
        plagiarismCase.setVerdict(plagiarismVerdictDTO.verdict());
        if (plagiarismVerdictDTO.verdict().equals(PlagiarismVerdict.POINT_DEDUCTION)) {
            plagiarismCase.setVerdictPointDeduction(plagiarismVerdictDTO.verdictPointDeduction());
        }
        else if (plagiarismVerdictDTO.verdict().equals(PlagiarismVerdict.WARNING)) {
            plagiarismCase.setVerdictMessage(plagiarismVerdictDTO.verdictMessage());
        }
        plagiarismCase.setVerdictDate(ZonedDateTime.now());
        var user = userRepository.getUserWithGroupsAndAuthorities();
        plagiarismCase.setVerdictBy(user);
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);
        // Notify the student about the verdict
        singleUserNotificationService.notifyUserAboutPlagiarismCaseVerdict(plagiarismCase, plagiarismCase.getStudent());
        return plagiarismCase;
    }

    /**
     * Save a post for a plagiarism case and notify the student about the plagiarism case.
     *
     * @param plagiarismCaseId the ID of the plagiarism case for which to save the post
     * @param post             the post which belongs to the plagiarism case
     */
    public void savePostForPlagiarismCaseAndNotifyStudent(long plagiarismCaseId, Post post) {
        PlagiarismCase plagiarismCase = plagiarismCaseRepository.findByIdWithPlagiarismSubmissionsElseThrow(plagiarismCaseId);
        plagiarismCase.setPost(post);
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);
        singleUserNotificationService.notifyUserAboutNewPlagiarismCase(plagiarismCase, plagiarismCase.getStudent());
    }

    /**
     * Save a post for a plagiarism case and notify the student about the plagiarism case.
     *
     * @param plagiarismCaseId the ID of the plagiarism case for which to save the post
     * @param post             the post which belongs to the plagiarism case
     */
    public void saveAnonymousPostForPlagiarismCaseAndNotifyStudent(long plagiarismCaseId, Post post) {
        PlagiarismCase plagiarismCase = plagiarismCaseRepository.findByIdWithPlagiarismSubmissionsElseThrow(plagiarismCaseId);
        plagiarismCase.setPost(post);
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);
        singleUserNotificationService.notifyUserAboutNewContinuousPlagiarismControlPlagiarismCase(plagiarismCase, plagiarismCase.getStudent());
    }

    /**
     * Create or add to plagiarism cases for both students involved in a plagiarism comparison if it is determined to be plagiarism.
     *
     * @param plagiarismComparisonId the ID of the plagiarism comparison
     */
    public void createOrAddToPlagiarismCasesForComparison(long plagiarismComparisonId) {
        var plagiarismComparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparisonId);
        // handle student A
        createOrAddToPlagiarismCaseForStudent(plagiarismComparison, plagiarismComparison.getSubmissionA(), false);
        // handle student B
        createOrAddToPlagiarismCaseForStudent(plagiarismComparison, plagiarismComparison.getSubmissionB(), false);
    }

    /**
     * Get the plagiarism case for a student and exercise.
     *
     * @param exerciseId the ID of the exercise
     * @param userId     the ID of the student
     * @return the plagiarism case for the student and exercise if it exists
     */
    public Optional<PlagiarismCaseInfoDTO> getPlagiarismCaseInfoForExerciseAndUser(long exerciseId, long userId) {
        return plagiarismCaseRepository.findByStudentIdAndExerciseIdWithPost(userId, exerciseId)
                // the student was notified if the plagiarism case is available (due to the nature of the query above)
                // the following line is already checked in the SQL statement, but we want to ensure it 100%
                .filter((plagiarismCase) -> plagiarismCase.getPost() != null).map((plagiarismCase) -> {
                    // Note: we only return the ID and verdict to tell the client there is a confirmed plagiarism case with student notification (post)
                    // and to support navigating to the detail page
                    // all other information might be irrelevant or sensitive and could lead to longer loading times
                    return new PlagiarismCaseInfoDTO(plagiarismCase.getId(), plagiarismCase.getVerdict(), plagiarismCase.isCreatedByContinuousPlagiarismControl());
                });
    }

    /**
     * Create or add to a plagiarism case for a student defined via the submission involved in a plagiarism comparison.
     * The following logic applies:
     *
     * <ul>
     * <li>Create a new plagiarism case if the student isn't already part of a plagiarism case in the exercise</li>
     * <li>Add the submission of the student to existing plagiarism case otherwise</li>
     * </ul>
     *
     * @param plagiarismComparison                 the plagiarism comparison for which to create the plagiarism case
     * @param plagiarismSubmission                 the plagiarism submission of the student for which to create the plagiarism case
     * @param createdByContinuousPlagiarismControl true is the plagiarism comparison was created by the continuous plagiarism control
     * @return the created or updated plagiarism case
     */
    public PlagiarismCase createOrAddToPlagiarismCaseForStudent(PlagiarismComparison<?> plagiarismComparison, PlagiarismSubmission<?> plagiarismSubmission,
            boolean createdByContinuousPlagiarismControl) {
        var plagiarismCase = plagiarismCaseRepository.findByStudentLoginAndExerciseIdWithPlagiarismSubmissions(plagiarismSubmission.getStudentLogin(),
                plagiarismComparison.getPlagiarismResult().getExercise().getId());
        if (plagiarismCase.isPresent()) {
            // add submission to existing PlagiarismCase for student
            plagiarismSubmission.setPlagiarismCase(plagiarismCase.get());
            // we do not save plagiarism comparison or plagiarism submission directly because due to issues with Cascade_All, it will automatically delete matches and re-add them
            // we actually use a custom modifying query to avoid all issues with Cascade ALL
            plagiarismSubmissionRepository.updatePlagiarismCase(plagiarismSubmission.getId(), plagiarismCase.get());
            return plagiarismCase.get();
        }
        else {
            // create new PlagiarismCase for student
            var student = userRepository.getUserByLoginElseThrow(plagiarismSubmission.getStudentLogin());
            PlagiarismCase newPlagiarismCase = new PlagiarismCase();
            newPlagiarismCase.setExercise(plagiarismComparison.getPlagiarismResult().getExercise());
            newPlagiarismCase.setStudent(student);
            newPlagiarismCase.setCreatedByContinuousPlagiarismControl(createdByContinuousPlagiarismControl);
            var savedPlagiarismCase = plagiarismCaseRepository.save(newPlagiarismCase);
            plagiarismSubmission.setPlagiarismCase(savedPlagiarismCase);
            // we do not save plagiarism comparison or plagiarism submission directly because due to issues with Cascade_All, it will automatically delete matches and re-add them
            // we actually use a custom modifying query to avoid all issues with Cascade ALL
            plagiarismSubmissionRepository.updatePlagiarismCase(plagiarismSubmission.getId(), savedPlagiarismCase);
            return savedPlagiarismCase;
        }
    }

    /**
     * Removes the plagiarism submissions from the plagiarism cases of both students involved in the plagiarism comparison.
     * Deletes the plagiarism case of either student if it doesn't contain any submissions afterwards.
     *
     * @param plagiarismComparisonId the ID of the plagiarism comparison
     */
    public void removeSubmissionsInPlagiarismCasesForComparison(long plagiarismComparisonId) {
        // remove plagiarism case from both submissions
        var plagiarismComparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparisonId);
        plagiarismComparison.getSubmissionA().setPlagiarismCase(null);
        plagiarismComparison.getSubmissionB().setPlagiarismCase(null);
        // we do not save plagiarism comparison or plagiarism submission directly because due to issues with Cascade_All, it will automatically delete matches and re-add them
        // we actually use a custom modifying query to avoid all issues with Cascade ALL
        plagiarismSubmissionRepository.updatePlagiarismCase(plagiarismComparison.getSubmissionA().getId(), null);
        plagiarismSubmissionRepository.updatePlagiarismCase(plagiarismComparison.getSubmissionB().getId(), null);

        // delete plagiarism case of Student A if it doesn't contain any submissions now
        var plagiarismCaseA = plagiarismCaseRepository.findByStudentLoginAndExerciseIdWithPlagiarismSubmissions(plagiarismComparison.getSubmissionA().getStudentLogin(),
                plagiarismComparison.getPlagiarismResult().getExercise().getId());
        if (plagiarismCaseA.isPresent() && plagiarismCaseA.get().getPlagiarismSubmissions().isEmpty()) {
            plagiarismCaseRepository.delete(plagiarismCaseA.get());
        }

        // delete plagiarism case of Student B if it doesn't contain any submissions now
        var plagiarismCaseB = plagiarismCaseRepository.findByStudentLoginAndExerciseIdWithPlagiarismSubmissions(plagiarismComparison.getSubmissionB().getStudentLogin(),
                plagiarismComparison.getPlagiarismResult().getExercise().getId());
        if (plagiarismCaseB.isPresent() && plagiarismCaseB.get().getPlagiarismSubmissions().isEmpty()) {
            plagiarismCaseRepository.delete(plagiarismCaseB.get());
        }
    }

    public record PlagiarismMapping(Map<Long, Map<Long, PlagiarismCase>> studentIdToExerciseIdToPlagiarismCaseMap) {

        /**
         * Factory method to create a PlagiarismMapping from a PlagiarismCase collection.
         * Useful for creating PlagiarismMapping from a database repository response.
         *
         * @param plagiarismCases a collection of relavant plagiarism cases with student/team ids and exercise ids present
         * @return a populated PlagiarismMapping instance
         */
        public static PlagiarismMapping createFromPlagiarismCases(Collection<PlagiarismCase> plagiarismCases) {
            Map<Long, Map<Long, PlagiarismCase>> outerMap = new HashMap<>();
            for (PlagiarismCase plagiarismCase : plagiarismCases) {
                for (User student : plagiarismCase.getStudents()) {
                    var innerMap = outerMap.computeIfAbsent(student.getId(), studentId -> new HashMap<>());
                    innerMap.put(plagiarismCase.getExercise().getId(), plagiarismCase);
                }
            }
            return new PlagiarismMapping(outerMap);
        }

        public PlagiarismCase getPlagiarismCase(Long studentId, Long exerciseId) {
            var innerMap = studentIdToExerciseIdToPlagiarismCaseMap.get(studentId);
            return innerMap != null ? innerMap.get(exerciseId) : null;
        }

        public Map<Long, PlagiarismCase> getPlagiarismCasesForStudent(Long studentId) {
            return studentIdToExerciseIdToPlagiarismCaseMap.getOrDefault(studentId, Collections.emptyMap());
        }

        public boolean studentHasVerdict(Long studentId, PlagiarismVerdict plagiarismVerdict) {
            var innerMap = getPlagiarismCasesForStudent(studentId);
            return innerMap.values().stream().anyMatch(plagiarismCase -> plagiarismVerdict.equals(plagiarismCase.getVerdict()));
        }
    }
}
