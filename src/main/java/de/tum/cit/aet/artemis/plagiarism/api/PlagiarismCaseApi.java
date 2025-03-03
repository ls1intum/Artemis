package de.tum.cit.aet.artemis.plagiarism.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.plagiarism.api.dtos.PlagiarismMapping;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseInfoDTO;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismCaseService;

@Profile(PROFILE_CORE)
@Controller
public class PlagiarismCaseApi extends AbstractPlagiarismApi {

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    private final PlagiarismCaseService plagiarismCaseService;

    public PlagiarismCaseApi(PlagiarismCaseRepository plagiarismCaseRepository, PlagiarismCaseService plagiarismCaseService) {
        this.plagiarismCaseRepository = plagiarismCaseRepository;
        this.plagiarismCaseService = plagiarismCaseService;
    }

    public Optional<PlagiarismCaseInfoDTO> getPlagiarismCaseInfoForExerciseAndUser(long exerciseId, long userId) {
        return plagiarismCaseService.getPlagiarismCaseInfoForExerciseAndUser(exerciseId, userId);
    }

    public List<PlagiarismCase> findByCourseIdAndStudentId(Long courseId, Long studentId) {
        return plagiarismCaseRepository.findByCourseIdAndStudentId(courseId, studentId);
    }

    public List<PlagiarismCase> findByCourseId(Long courseId) {
        return plagiarismCaseRepository.findByCourseId(courseId);
    }

    public List<PlagiarismCase> findByStudentIdAndExerciseIds(Long userId, Set<Long> exerciseIds) {
        return plagiarismCaseRepository.findByStudentIdAndExerciseIds(userId, exerciseIds);
    }

    public Optional<PlagiarismCase> findByStudentIdAndExerciseIdWithPostAndAnswerPost(Long userId, Long exerciseId) {
        return plagiarismCaseRepository.findByStudentIdAndExerciseIdWithPostAndAnswerPost(userId, exerciseId);
    }

    public PlagiarismMapping getPlagiarismMappingForExam(Long examId) {
        var plagiarismCasesForStudent = plagiarismCaseRepository.findByExamId(examId);
        return PlagiarismMapping.createFromPlagiarismCases(plagiarismCasesForStudent);
    }

    public List<PlagiarismCase> findByExamIdAndStudentId(Long examId, Long studentId) {
        return plagiarismCaseRepository.findByExamIdAndStudentId(examId, studentId);
    }
}
