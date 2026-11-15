package de.tum.cit.aet.artemis.exam.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.dto.ExamDeletionSummaryDTO;
import de.tum.cit.aet.artemis.exam.dto.ExerciseGroupDeletionSummaryDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamLiveEventRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamSessionRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeCountDTO;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.localci.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismResultApi;

/**
 * Counts what deleting an exam or an exercise group removes, so the instructor sees the consequences before they confirm.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExamDeletionSummaryService {

    private final ExamRepository examRepository;

    private final ExerciseGroupRepository exerciseGroupRepository;

    private final ParticipationRepository participationRepository;

    private final SubmissionRepository submissionRepository;

    private final ResultRepository resultRepository;

    private final ComplaintRepository complaintRepository;

    private final BuildJobRepository buildJobRepository;

    private final ChannelRepository channelRepository;

    private final PostRepository postRepository;

    private final AnswerPostRepository answerPostRepository;

    private final ExamUserRepository examUserRepository;

    private final StudentExamRepository studentExamRepository;

    private final ExamSessionRepository examSessionRepository;

    private final ExamLiveEventRepository examLiveEventRepository;

    private final Optional<PlagiarismResultApi> plagiarismResultApi;

    public ExamDeletionSummaryService(ExamRepository examRepository, ExerciseGroupRepository exerciseGroupRepository, ParticipationRepository participationRepository,
            SubmissionRepository submissionRepository, ResultRepository resultRepository, ComplaintRepository complaintRepository, BuildJobRepository buildJobRepository,
            ChannelRepository channelRepository, PostRepository postRepository, AnswerPostRepository answerPostRepository, ExamUserRepository examUserRepository,
            StudentExamRepository studentExamRepository, ExamSessionRepository examSessionRepository, ExamLiveEventRepository examLiveEventRepository,
            Optional<PlagiarismResultApi> plagiarismResultApi) {
        this.examRepository = examRepository;
        this.exerciseGroupRepository = exerciseGroupRepository;
        this.participationRepository = participationRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
        this.complaintRepository = complaintRepository;
        this.buildJobRepository = buildJobRepository;
        this.channelRepository = channelRepository;
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
        this.examUserRepository = examUserRepository;
        this.studentExamRepository = studentExamRepository;
        this.examSessionRepository = examSessionRepository;
        this.examLiveEventRepository = examLiveEventRepository;
        this.plagiarismResultApi = plagiarismResultApi;
    }

    /**
     * Get the exam deletion summary for the given exam.
     *
     * @param examId the ID of the exam for which the deletion summary should be fetched
     * @return the exam deletion summary
     */
    public ExamDeletionSummaryDTO getExamDeletionSummary(long examId) {
        Set<Long> exerciseIds = examRepository.findExerciseIdsByExamId(examId);
        List<ExerciseTypeCountDTO> exercisesByType = exerciseGroupRepository.countExercisesByExamIdGroupedByType(examId);

        // The channel is deleted first when an exam is deleted, so it is missing if an earlier deletion failed afterwards. The summary shown before the retry must still work.
        Channel channel = channelRepository.findChannelByExamId(examId);
        long numberOfCommunicationPosts = channel == null ? 0 : postRepository.countByConversationId(channel.getId());
        long numberOfAnswerPosts = channel == null ? 0 : answerPostRepository.countByConversationId(channel.getId());

        return new ExamDeletionSummaryDTO(countBuilds(exerciseIds), numberOfCommunicationPosts, numberOfAnswerPosts, examUserRepository.countByExamId(examId),
                studentExamRepository.countStudentExamsNotStartedByExamIdIgnoreTestRuns(examId), studentExamRepository.countStudentExamsStartedByExamIdIgnoreTestRuns(examId),
                studentExamRepository.countStudentExamsSubmittedByExamIdIgnoreTestRuns(examId), exerciseGroupRepository.countByExamId(examId),
                countOfType(exercisesByType, ExerciseType.PROGRAMMING), countOfType(exercisesByType, ExerciseType.QUIZ), countOfType(exercisesByType, ExerciseType.TEXT),
                countOfType(exercisesByType, ExerciseType.MODELING), countOfType(exercisesByType, ExerciseType.FILE_UPLOAD), countParticipations(exerciseIds),
                countSubmissions(exerciseIds), countResults(exerciseIds), resultRepository.countNumberOfAssessments(exerciseIds), countComplaints(exerciseIds),
                countPlagiarismComparisons(exerciseIds), studentExamRepository.countTestRunsByExamId(examId), examSessionRepository.countByExamId(examId),
                examLiveEventRepository.countByExamId(examId));
    }

    /**
     * Get the deletion summary for the given exercise group.
     *
     * @param exerciseGroupId the ID of the exercise group for which the deletion summary should be fetched
     * @return the exercise group deletion summary
     */
    public ExerciseGroupDeletionSummaryDTO getExerciseGroupDeletionSummary(long exerciseGroupId) {
        Set<Long> exerciseIds = exerciseGroupRepository.findExerciseDeletionInfoByExerciseGroupId(exerciseGroupId).stream().map(info -> info.id()).collect(Collectors.toSet());
        List<ExerciseTypeCountDTO> exercisesByType = exerciseGroupRepository.countExercisesByExerciseGroupIdGroupedByType(exerciseGroupId);

        return new ExerciseGroupDeletionSummaryDTO(countOfType(exercisesByType, ExerciseType.PROGRAMMING), countOfType(exercisesByType, ExerciseType.QUIZ),
                countOfType(exercisesByType, ExerciseType.TEXT), countOfType(exercisesByType, ExerciseType.MODELING), countOfType(exercisesByType, ExerciseType.FILE_UPLOAD),
                countParticipations(exerciseIds), countSubmissions(exerciseIds), countResults(exerciseIds), resultRepository.countNumberOfAssessments(exerciseIds),
                countComplaints(exerciseIds), countPlagiarismComparisons(exerciseIds), countBuilds(exerciseIds),
                exerciseIds.isEmpty() ? 0 : studentExamRepository.countRealStudentExamsContainingExercises(exerciseIds));
    }

    private static long countOfType(List<ExerciseTypeCountDTO> exercisesByType, ExerciseType type) {
        return exercisesByType.stream().filter(entry -> entry.exerciseType() == type).mapToLong(ExerciseTypeCountDTO::count).sum();
    }

    // The queries below are not run for an empty set of exercises, because an empty IN clause can lead to performance issues

    private long countBuilds(Set<Long> exerciseIds) {
        return exerciseIds.isEmpty() ? 0 : buildJobRepository.countBuildJobsByExerciseIds(exerciseIds);
    }

    private long countParticipations(Set<Long> exerciseIds) {
        return exerciseIds.isEmpty() ? 0 : participationRepository.countByExerciseIds(exerciseIds);
    }

    private long countSubmissions(Set<Long> exerciseIds) {
        return exerciseIds.isEmpty() ? 0 : submissionRepository.countByExerciseIds(exerciseIds);
    }

    private long countResults(Set<Long> exerciseIds) {
        return exerciseIds.isEmpty() ? 0 : resultRepository.countByExerciseIds(exerciseIds);
    }

    private long countComplaints(Set<Long> exerciseIds) {
        return exerciseIds.isEmpty() ? 0 : complaintRepository.countByExerciseIds(exerciseIds);
    }

    private long countPlagiarismComparisons(Set<Long> exerciseIds) {
        return plagiarismResultApi.map(api -> api.countComparisonsByExerciseIds(exerciseIds)).orElse(0L);
    }
}
