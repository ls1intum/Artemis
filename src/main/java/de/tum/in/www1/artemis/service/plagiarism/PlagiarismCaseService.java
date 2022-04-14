package de.tum.in.www1.artemis.service.plagiarism;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismVerdictDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class PlagiarismCaseService {

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    private final UserRepository userRepository;

    private final SingleUserNotificationService singleUserNotificationService;

    public PlagiarismCaseService(PlagiarismCaseRepository plagiarismCaseRepository, PlagiarismComparisonRepository plagiarismComparisonRepository, UserRepository userRepository,
            SingleUserNotificationService singleUserNotificationService) {
        this.plagiarismCaseRepository = plagiarismCaseRepository;
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
        this.userRepository = userRepository;
        this.singleUserNotificationService = singleUserNotificationService;
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
        Optional<PlagiarismCase> optionalPlagiarismCase = plagiarismCaseRepository.findById(plagiarismCaseId);
        if (optionalPlagiarismCase.isEmpty()) {
            throw new EntityNotFoundException("Plagiarism Case", plagiarismCaseId);
        }
        PlagiarismCase plagiarismCase = optionalPlagiarismCase.get();
        plagiarismCase.setVerdict(plagiarismVerdictDTO.getVerdict());
        if (plagiarismVerdictDTO.getVerdict().equals(PlagiarismVerdict.POINT_DEDUCTION)) {
            plagiarismCase.setVerdictPointDeduction(plagiarismVerdictDTO.getVerdictPointDeduction());
        }
        else if (plagiarismVerdictDTO.getVerdict().equals(PlagiarismVerdict.WARNING)) {
            plagiarismCase.setVerdictMessage(plagiarismVerdictDTO.getVerdictMessage());
        }
        plagiarismCase.setVerdictDate(ZonedDateTime.now());
        var user = userRepository.getUserWithGroupsAndAuthorities();
        plagiarismCase.setVerdictBy(user);
        plagiarismCaseRepository.save(plagiarismCase);
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
        PlagiarismCase plagiarismCase = plagiarismCaseRepository.findByIdWithExerciseAndPlagiarismSubmissionsElseThrow(plagiarismCaseId);
        plagiarismCase.setPost(post);
        plagiarismCaseRepository.save(plagiarismCase);
        singleUserNotificationService.notifyUserAboutNewPlagiarismCase(plagiarismCase, plagiarismCase.getStudent());
    }

    /**
     * Create or add a plagiarism case for both students involved in a plagiarism comparison if it is determined to be plagiarism.
     * For each student the following logic applies:
     * <ul>
     *     <li>Create a new plagiarism case if the student isn't already part of a plagiarism case in the exercise</li>
     *     <li>Add the submission of the student to existing plagiarism case otherwise</li>
     * </ul>
     *
     * @param plagiarismComparisonId    the ID of the plagiarism comparison
     */
    public void createOrAddPlagiarismCasesForComparison(long plagiarismComparisonId) {
        var plagiarismComparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparisonId);
        // handle student A
        var plagiarismCaseA = plagiarismCaseRepository.findByStudentLoginAndExerciseId(plagiarismComparison.getSubmissionA().getStudentLogin(),
                plagiarismComparison.getPlagiarismResult().getExercise().getId());
        if (plagiarismCaseA.isPresent()) {
            // add submission to existing PlagiarismCase for student A
            plagiarismComparison.getSubmissionA().setPlagiarismCase(plagiarismCaseA.get());
            plagiarismComparisonRepository.save(plagiarismComparison);
        }
        else {
            // create new PlagiarismCase for student A
            var student = userRepository.getUserByLoginElseThrow(plagiarismComparison.getSubmissionA().getStudentLogin());
            PlagiarismCase plagiarismCase = new PlagiarismCase();
            plagiarismCase.setExercise(plagiarismComparison.getPlagiarismResult().getExercise());
            plagiarismCase.setStudent(student);
            var savedPlagiarismCase = plagiarismCaseRepository.save(plagiarismCase);
            plagiarismComparison.getSubmissionA().setPlagiarismCase(savedPlagiarismCase);
            plagiarismComparisonRepository.save(plagiarismComparison);
        }
        // handle student B
        var plagiarismCaseB = plagiarismCaseRepository.findByStudentLoginAndExerciseId(plagiarismComparison.getSubmissionB().getStudentLogin(),
                plagiarismComparison.getPlagiarismResult().getExercise().getId());
        if (plagiarismCaseB.isPresent()) {
            // add submission to existing PlagiarismCase for student B
            plagiarismComparison.getSubmissionB().setPlagiarismCase(plagiarismCaseB.get());
            plagiarismComparisonRepository.save(plagiarismComparison);
        }
        else {
            // create new PlagiarismCase for student B
            var student = userRepository.getUserByLoginElseThrow(plagiarismComparison.getSubmissionB().getStudentLogin());
            PlagiarismCase plagiarismCase = new PlagiarismCase();
            plagiarismCase.setExercise(plagiarismComparison.getPlagiarismResult().getExercise());
            plagiarismCase.setStudent(student);
            var savedPlagiarismCase = plagiarismCaseRepository.save(plagiarismCase);
            plagiarismComparison.getSubmissionB().setPlagiarismCase(savedPlagiarismCase);
            plagiarismComparisonRepository.save(plagiarismComparison);
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
        plagiarismComparisonRepository.save(plagiarismComparison);
        // delete plagiarism case of Student A if it doesn't contain any submissions now
        var plagiarismCaseA = plagiarismCaseRepository.findByStudentLoginAndExerciseId(plagiarismComparison.getSubmissionA().getStudentLogin(),
                plagiarismComparison.getPlagiarismResult().getExercise().getId());
        if (plagiarismCaseA.isPresent() && plagiarismCaseA.get().getPlagiarismSubmissions().isEmpty()) {
            plagiarismCaseRepository.delete(plagiarismCaseA.get());
        }
        // delete plagiarism case of Student B if it doesn't contain any submissions now
        var plagiarismCaseB = plagiarismCaseRepository.findByStudentLoginAndExerciseId(plagiarismComparison.getSubmissionB().getStudentLogin(),
                plagiarismComparison.getPlagiarismResult().getExercise().getId());
        if (plagiarismCaseB.isPresent() && plagiarismCaseB.get().getPlagiarismSubmissions().isEmpty()) {
            plagiarismCaseRepository.delete(plagiarismCaseB.get());
        }
    }
}
