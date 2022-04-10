package de.tum.in.www1.artemis.service.plagiarism;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

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

    public void createOrAddPlagiarismCaseForComparison(long plagiarismComparisonId) {
        var plagiarismComparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparisonId);
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
            singleUserNotificationService.notifyUserAboutNewPlagiarismCase(savedPlagiarismCase, student);
        }
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
            singleUserNotificationService.notifyUserAboutNewPlagiarismCase(savedPlagiarismCase, student);
        }
    }
}
