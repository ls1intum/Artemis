import { EntitySummaryCategory } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { summaryCategories, summaryCategory, summaryItem } from 'app/shared-ui/delete-dialog/entity-summary.util';
import { ExamDeletionSummaryDTO } from 'app/exam/shared/entities/exam-deletion-summary.model';
import { ExerciseGroupDeletionSummaryDTO } from 'app/exam/shared/entities/exercise-group-deletion-summary.model';

const PREFIX = 'artemisApp.examManagement.delete.summary';

/**
 * Groups what is deleted together with an exam into categories an instructor can grasp at a glance. Rows without data are left out.
 */
export function buildExamDeletionSummary(summary: ExamDeletionSummaryDTO): EntitySummaryCategory[] {
    return summaryCategories([
        summaryCategory(`${PREFIX}.category.structure`, [
            summaryItem(`${PREFIX}.numberExerciseGroups`, summary.numberOfExerciseGroups),
            summaryItem(`${PREFIX}.numberProgrammingExercises`, summary.numberOfProgrammingExercises),
            summaryItem(`${PREFIX}.numberQuizExercises`, summary.numberOfQuizExercises),
            summaryItem(`${PREFIX}.numberTextExercises`, summary.numberOfTextExercises),
            summaryItem(`${PREFIX}.numberModelingExercises`, summary.numberOfModelingExercises),
            summaryItem(`${PREFIX}.numberFileUploadExercises`, summary.numberOfFileUploadExercises),
        ]),
        summaryCategory(`${PREFIX}.category.students`, [
            summaryItem(`${PREFIX}.numberRegisteredStudents`, summary.numberRegisteredStudents, true),
            summaryItem(`${PREFIX}.numberNotStartedExams`, summary.numberNotStartedExams),
            summaryItem(`${PREFIX}.numberStartedExams`, summary.numberStartedExams),
            summaryItem(`${PREFIX}.numberSubmittedExams`, summary.numberSubmittedExams),
            summaryItem(`${PREFIX}.numberTestRuns`, summary.numberOfTestRuns),
            summaryItem(`${PREFIX}.numberExamSessions`, summary.numberOfExamSessions),
            summaryItem(`${PREFIX}.numberExamLiveEvents`, summary.numberOfExamLiveEvents),
        ]),
        summaryCategory(`${PREFIX}.category.studentWork`, [
            summaryItem(`${PREFIX}.numberStudentParticipations`, summary.numberOfStudentParticipations),
            summaryItem(`${PREFIX}.numberSubmissions`, summary.numberOfSubmissions),
        ]),
        summaryCategory(`${PREFIX}.category.assessment`, [
            summaryItem(`${PREFIX}.numberResults`, summary.numberOfResults),
            summaryItem(`${PREFIX}.numberAssessments`, summary.numberOfAssessments),
            summaryItem(`${PREFIX}.numberComplaints`, summary.numberOfComplaints),
        ]),
        summaryCategory(`${PREFIX}.category.plagiarism`, [summaryItem(`${PREFIX}.numberPlagiarismComparisons`, summary.numberOfPlagiarismComparisons)]),
        summaryCategory(`${PREFIX}.category.communication`, [
            summaryItem(`${PREFIX}.numberCommunicationPosts`, summary.numberOfCommunicationPosts),
            summaryItem(`${PREFIX}.numberAnswerPosts`, summary.numberOfAnswerPosts),
        ]),
        summaryCategory(`${PREFIX}.category.infrastructure`, [summaryItem(`${PREFIX}.numberBuilds`, summary.numberOfBuilds, true)]),
    ]);
}

/**
 * Groups what is deleted together with an exercise group into categories. Uses the labels of the exam summary where they mean the same.
 */
export function buildExerciseGroupDeletionSummary(summary: ExerciseGroupDeletionSummaryDTO): EntitySummaryCategory[] {
    return summaryCategories([
        summaryCategory(`${PREFIX}.category.structure`, [
            summaryItem(`${PREFIX}.numberProgrammingExercises`, summary.numberOfProgrammingExercises),
            summaryItem(`${PREFIX}.numberQuizExercises`, summary.numberOfQuizExercises),
            summaryItem(`${PREFIX}.numberTextExercises`, summary.numberOfTextExercises),
            summaryItem(`${PREFIX}.numberModelingExercises`, summary.numberOfModelingExercises),
            summaryItem(`${PREFIX}.numberFileUploadExercises`, summary.numberOfFileUploadExercises),
        ]),
        summaryCategory(`${PREFIX}.category.studentWork`, [
            summaryItem(`${PREFIX}.numberStudentExamsOfGroup`, summary.numberOfStudentExams),
            summaryItem(`${PREFIX}.numberStudentParticipations`, summary.numberOfStudentParticipations),
            summaryItem(`${PREFIX}.numberSubmissions`, summary.numberOfSubmissions),
        ]),
        summaryCategory(`${PREFIX}.category.assessment`, [
            summaryItem(`${PREFIX}.numberResults`, summary.numberOfResults),
            summaryItem(`${PREFIX}.numberAssessments`, summary.numberOfAssessments),
            summaryItem(`${PREFIX}.numberComplaints`, summary.numberOfComplaints),
        ]),
        summaryCategory(`${PREFIX}.category.plagiarism`, [summaryItem(`${PREFIX}.numberPlagiarismComparisons`, summary.numberOfPlagiarismComparisons)]),
        summaryCategory(`${PREFIX}.category.infrastructure`, [summaryItem(`${PREFIX}.numberBuilds`, summary.numberOfBuilds, true)]),
    ]);
}
