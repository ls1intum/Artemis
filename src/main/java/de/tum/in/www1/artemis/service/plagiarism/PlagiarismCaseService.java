package de.tum.in.www1.artemis.service.plagiarism;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismSubmissionRepository;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismVerdictDTO;

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
     *     <li>If the verdict is a point deduction additionally save the point deduction.</li>
     *     <li>If the verdict is a warning additionally save the warning message.</li>
     * </ul>
     *
     * @param plagiarismCaseId      the ID of the plagiarism case for which to save the verdict
     * @param plagiarismVerdictDTO  the verdict to be saved
     * @return                      the plagiarism case with the verdict
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
     * @param plagiarismCaseId  the ID of the plagiarism case for which to save the post
     * @param post              the post which belongs to the plagiarism case
     */
    public void savePostForPlagiarismCaseAndNotifyStudent(long plagiarismCaseId, Post post) {
        PlagiarismCase plagiarismCase = plagiarismCaseRepository.findByIdWithPlagiarismSubmissionsElseThrow(plagiarismCaseId);
        plagiarismCase.setPost(post);
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);
        singleUserNotificationService.notifyUserAboutNewPlagiarismCase(plagiarismCase, plagiarismCase.getStudent());
    }

    /**
     * Create or add to plagiarism cases for both students involved in a plagiarism comparison if it is determined to be plagiarism.
     *
     * @param plagiarismComparisonId    the ID of the plagiarism comparison
     */
    public void createOrAddToPlagiarismCasesForComparison(long plagiarismComparisonId) {
        var plagiarismComparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparisonId);
        // handle student A
        createOrAddToPlagiarismCaseForStudent(plagiarismComparison, plagiarismComparison.getSubmissionA());
        // handle student B
        createOrAddToPlagiarismCaseForStudent(plagiarismComparison, plagiarismComparison.getSubmissionB());
    }

    /**
     * Create or add to a plagiarism case for a student defined via the submission involved in a plagiarism comparison.
     * The following logic applies:
     *      * <ul>
     *      *     <li>Create a new plagiarism case if the student isn't already part of a plagiarism case in the exercise</li>
     *      *     <li>Add the submission of the student to existing plagiarism case otherwise</li>
     *      * </ul>
     *
     * @param plagiarismComparison  the plagiarism comparison for which to create the plagiarism case
     * @param plagiarismSubmission  the plagiarism submission of the student for which to create the plagiarism case
     */
    public void createOrAddToPlagiarismCaseForStudent(PlagiarismComparison<?> plagiarismComparison, PlagiarismSubmission<?> plagiarismSubmission) {
        var plagiarismCase = plagiarismCaseRepository.findByStudentLoginAndExerciseIdWithPlagiarismSubmissions(plagiarismSubmission.getStudentLogin(),
                plagiarismComparison.getPlagiarismResult().getExercise().getId());
        if (plagiarismCase.isPresent()) {
            // add submission to existing PlagiarismCase for student
            plagiarismSubmission.setPlagiarismCase(plagiarismCase.get());
            // we do not save plagiarism comparison or plagiarism submission directly because due to issues with Cascade_All, it will automatically delete matches and re-add them
            // we actually use a custom modifying query to avoid all issues with Cascade ALL
            plagiarismSubmissionRepository.updatePlagiarismCase(plagiarismSubmission.getId(), plagiarismCase.get());
        }
        else {
            // create new PlagiarismCase for student
            var student = userRepository.getUserByLoginElseThrow(plagiarismSubmission.getStudentLogin());
            PlagiarismCase newPlagiarismCase = new PlagiarismCase();
            newPlagiarismCase.setExercise(plagiarismComparison.getPlagiarismResult().getExercise());
            newPlagiarismCase.setStudent(student);
            var savedPlagiarismCase = plagiarismCaseRepository.save(newPlagiarismCase);
            plagiarismSubmission.setPlagiarismCase(savedPlagiarismCase);
            // we do not save plagiarism comparison or plagiarism submission directly because due to issues with Cascade_All, it will automatically delete matches and re-add them
            // we actually use a custom modifying query to avoid all issues with Cascade ALL
            plagiarismSubmissionRepository.updatePlagiarismCase(plagiarismSubmission.getId(), savedPlagiarismCase);
        }
    }

    /**
     * Removes the plagiarism submissions from the plagiarism cases of both students involved in the plagiarism comparison.
     * Deletes the plagiarism case of either student if it doesn't contain any submissions afterwards.
     *
     * @param plagiarismComparisonId    the ID of the plagiarism comparison
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
