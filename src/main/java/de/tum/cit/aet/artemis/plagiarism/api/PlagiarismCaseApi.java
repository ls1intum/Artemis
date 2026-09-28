package de.tum.cit.aet.artemis.plagiarism.api;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.plagiarism.api.dtos.PlagiarismCaseScoreDTO;
import de.tum.cit.aet.artemis.plagiarism.api.dtos.PlagiarismMapping;
import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseInfoDTO;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismCaseService;

@Conditional(PlagiarismEnabled.class)
@Controller
@Lazy
public class PlagiarismCaseApi extends AbstractPlagiarismApi {

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    private final PlagiarismCaseService plagiarismCaseService;

    private final UserRepository userRepository;

    public PlagiarismCaseApi(PlagiarismCaseRepository plagiarismCaseRepository, PlagiarismCaseService plagiarismCaseService, UserRepository userRepository) {
        this.plagiarismCaseRepository = plagiarismCaseRepository;
        this.plagiarismCaseService = plagiarismCaseService;
        this.userRepository = userRepository;
    }

    /**
     * Whether the user takes part in the discussion of a plagiarism case: the student or a member of the team the case is about, or an instructor of its course.
     *
     * @param plagiarismCaseId the id of the plagiarism case
     * @param login            the login of the user
     * @return true if the case is about the user or the user's team, or the user is at least instructor in its course
     */
    public boolean isStudentOrInstructorOfPlagiarismCase(long plagiarismCaseId, String login) {
        if (plagiarismCaseRepository.existsByIdAndStudentOrTeamMemberLogin(plagiarismCaseId, login)) {
            return true;
        }
        return plagiarismCaseRepository.findCourseIdById(plagiarismCaseId).filter(courseId -> userRepository.isAtLeastInstructorInCourse(login, courseId)).isPresent();
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

    public List<PlagiarismCaseScoreDTO> findScoreInformationByStudentIdAndExerciseIds(long userId, Set<Long> exerciseIds) {
        return plagiarismCaseRepository.findScoreInformationByStudentIdAndExerciseIds(userId, exerciseIds);
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

    /**
     * Deletes all plagiarism cases of course exercises whose course ended before the given date (data-privacy cleanup).
     *
     * @param endDateBefore only cases of courses that ended strictly before this are deleted
     * @return the number of deleted plagiarism cases
     */
    public int deletePlagiarismCasesOfCoursesEndedBefore(ZonedDateTime endDateBefore) {
        return plagiarismCaseService.deletePlagiarismCasesOfCoursesEndedBefore(endDateBefore);
    }

    /**
     * Counts the plagiarism cases of course exercises whose course ended before the given date.
     *
     * @param endDateBefore only cases of courses that ended strictly before this are counted
     * @return the number of matching plagiarism cases
     */
    public int countPlagiarismCasesOfCoursesEndedBefore(ZonedDateTime endDateBefore) {
        return plagiarismCaseService.countPlagiarismCasesOfCoursesEndedBefore(endDateBefore);
    }
}
