import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExamManagementComponent } from 'app/exam/manage/exam-management.component';
import { ExamUpdateComponent } from 'app/exam/manage/exams/exam-update.component';
import { ExamDetailComponent } from 'app/exam/manage/exams/exam-detail.component';
import { ExerciseGroupsComponent } from 'app/exam/manage/exercise-groups/exercise-groups.component';
import { ExerciseGroupUpdateComponent } from 'app/exam/manage/exercise-groups/exercise-group-update.component';
import { ExamStudentsComponent } from 'app/exam/manage/students/exam-students.component';
import { StudentExamsComponent } from 'app/exam/manage/student-exams/student-exams.component';
import { StudentExamDetailComponent } from 'app/exam/manage/student-exams/student-exam-detail.component';
import { TextExerciseUpdateComponent } from 'app/exercises/text/manage/text-exercise/text-exercise-update.component';
import { TextExerciseResolver } from 'app/exercises/text/manage/text-exercise/text-exercise.route';
import { FileUploadExerciseUpdateComponent } from 'app/exercises/file-upload/manage/file-upload-exercise-update.component';
import { QuizExerciseDetailComponent } from 'app/exercises/quiz/manage/quiz-exercise-detail.component';
import { ProgrammingExerciseUpdateComponent } from 'app/exercises/programming/manage/update/programming-exercise-update.component';
import { ProgrammingExerciseResolve } from 'app/exercises/programming/manage/programming-exercise-management-routing.module';
import { ModelingExerciseUpdateComponent } from 'app/exercises/modeling/manage/modeling-exercise-update.component';
import { StudentExamSummaryComponent } from 'app/exam/manage/student-exams/student-exam-summary.component';
import { AssessmentDashboardComponent } from 'app/course/dashboards/assessment-dashboard/assessment-dashboard.component';
import { TestRunManagementComponent } from 'app/exam/manage/test-runs/test-run-management.component';
import { ExamParticipationComponent } from 'app/exam/participate/exam-participation.component';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { Authority } from 'app/shared/constants/authority.constants';
import { ExerciseAssessmentDashboardComponent } from 'app/exercises/shared/dashboards/tutor/exercise-assessment-dashboard.component';
import { ExamParticipantScoresComponent } from 'app/exam/manage/exam-participant-scores/exam-participant-scores.component';
import { ParticipationSubmissionComponent } from 'app/exercises/shared/participation-submission/participation-submission.component';
import { FileUploadAssessmentComponent } from 'app/exercises/file-upload/assess/file-upload-assessment.component';
import { ParticipationComponent } from 'app/exercises/shared/participation/participation.component';
import { ExerciseScoresComponent } from 'app/exercises/shared/exercise-scores/exercise-scores.component';
import { FileUploadAssessmentDashboardComponent } from 'app/exercises/file-upload/assess/file-upload-assessment-dashboard.component';
import { TextAssessmentDashboardComponent } from 'app/exercises/text/assess/text-assessment-dashboard/text-assessment-dashboard.component';
import { ModelingAssessmentDashboardComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-dashboard.component';
import { ModelingAssessmentEditorComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.component';
import { ProgrammingExerciseSubmissionsComponent } from 'app/exercises/programming/assess/programming-assessment-dashboard/programming-exercise-submissions.component';
import { CodeEditorTutorAssessmentContainerComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-container.component';
import { exerciseTypes } from 'app/entities/exercise.model';
import { FileUploadExerciseDetailComponent } from 'app/exercises/file-upload/manage/file-upload-exercise-detail.component';
import { ModelingExerciseDetailComponent } from 'app/exercises/modeling/manage/modeling-exercise-detail.component';
import { ProgrammingExerciseDetailComponent } from 'app/exercises/programming/manage/programming-exercise-detail.component';
import { TextExerciseDetailComponent } from 'app/exercises/text/manage/text-exercise/text-exercise-detail.component';
import { PlagiarismInspectorComponent } from 'app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component';
import { GradingSystemComponent } from 'app/grading-system/grading-system.component';
import { ExampleSubmissionsComponent } from 'app/exercises/shared/example-submission/example-submissions.component';
import { GradingKeyOverviewComponent } from 'app/grading-system/grading-key-overview/grading-key-overview.component';
import { ExerciseStatisticsComponent } from 'app/exercises/shared/statistics/exercise-statistics.component';
import { ProgrammingExerciseConfigureGradingComponent } from 'app/exercises/programming/manage/grading/programming-exercise-configure-grading.component';
import { ExampleModelingSubmissionComponent } from 'app/exercises/modeling/manage/example-modeling/example-modeling-submission.component';
import { QuizParticipationComponent } from 'app/exercises/quiz/participate/quiz-participation.component';
import { QuizReEvaluateComponent } from 'app/exercises/quiz/manage/re-evaluate/quiz-re-evaluate.component';
import { QuizPointStatisticComponent } from 'app/exercises/quiz/manage/statistics/quiz-point-statistic/quiz-point-statistic.component';
import { QuizStatisticComponent } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';
import { MultipleChoiceQuestionStatisticComponent } from 'app/exercises/quiz/manage/statistics/multiple-choice-question-statistic/multiple-choice-question-statistic.component';
import { DragAndDropQuestionStatisticComponent } from 'app/exercises/quiz/manage/statistics/drag-and-drop-question-statistic/drag-and-drop-question-statistic.component';
import { ShortAnswerQuestionStatisticComponent } from 'app/exercises/quiz/manage/statistics/short-answer-question-statistic/short-answer-question-statistic.component';
import { OrionExerciseAssessmentDashboardComponent } from 'app/orion/assessment/orion-exercise-assessment-dashboard.component';
import { OrionTutorAssessmentComponent } from 'app/orion/assessment/orion-tutor-assessment.component';
import { isOrion } from 'app/shared/orion/orion';
import { FileUploadExerciseManagementResolve } from 'app/exercises/file-upload/manage/file-upload-exercise-management-resolve.service';
import { ModelingExerciseResolver } from 'app/exercises/modeling/manage/modeling-exercise-resolver.service';
import { ExamResolve, ExerciseGroupResolve, StudentExamResolve } from 'app/exam/manage/exam-management-resolve.service';
import { BonusComponent } from 'app/grading-system/bonus/bonus.component';

export const examManagementRoute: Routes = [
    {
        path: '',
        component: ExamManagementComponent,
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'new',
        component: ExamUpdateComponent,
        resolve: {
            exam: ExamResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/edit',
        component: ExamUpdateComponent,
        resolve: {
            exam: ExamResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId',
        component: ExamDetailComponent,
        resolve: {
            exam: ExamResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
            requestOptions: {
                withExerciseGroups: true,
            },
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/monitoring',
        loadChildren: () => import('../monitoring/exam-monitoring.module').then((m) => m.ArtemisExamMonitoringModule),
    },
    {
        path: ':examId/participant-scores',
        component: ExamParticipantScoresComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.participantScores.pageTitle',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups',
        component: ExerciseGroupsComponent,
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/new',
        component: ExerciseGroupUpdateComponent,
        resolve: {
            exam: ExamResolve,
            exerciseGroup: ExerciseGroupResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/edit',
        component: ExerciseGroupUpdateComponent,
        resolve: {
            exam: ExamResolve,
            exerciseGroup: ExerciseGroupResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId/tutor-effort-statistics',
        loadChildren: () => import('../../exercises/text/manage/tutor-effort/tutor-effort-statistics.module').then((m) => m.ArtemisTutorEffortStatisticsModule),
    },
    {
        path: ':examId/students',
        component: ExamStudentsComponent,
        resolve: {
            exam: ExamResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
            requestOptions: {
                withStudents: true,
            },
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/student-exams',
        component: StudentExamsComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/grading-system',
        component: GradingSystemComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.gradingSystem',
        },
        canActivate: [UserRouteAccessService],
        loadChildren: () => import('app/grading-system/grading-system.module').then((m) => m.GradingSystemModule),
    },
    {
        path: ':examId/test-runs',
        component: TestRunManagementComponent,
        resolve: {
            exam: ExamResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/test-runs/assess',
        component: AssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            pageTitle: 'artemisApp.examManagement.assessmentDashboard',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/test-runs/:studentExamId',
        component: StudentExamDetailComponent,
        resolve: {
            studentExam: StudentExamResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/student-exams/:studentExamId',
        component: StudentExamDetailComponent,
        resolve: {
            studentExam: StudentExamResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/student-exams/:studentExamId/summary',
        component: StudentExamSummaryComponent,
        resolve: {
            studentExam: StudentExamResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/student-exams/:studentExamId/summary/overview/grading-key',
        component: GradingKeyOverviewComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/test-runs/:testRunId/conduction',
        component: ExamParticipationComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: ':examId/test-runs/:studentExamId/summary',
        component: StudentExamSummaryComponent,
        resolve: {
            studentExam: StudentExamResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Create Modeling Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/new',
        component: ModelingExerciseUpdateComponent,
        resolve: {
            modelingExercise: ModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Import Modeling Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/import/:exerciseId',
        component: ModelingExerciseUpdateComponent,
        resolve: {
            modelingExercise: ModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Edit Modeling Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/edit',
        component: ModelingExerciseUpdateComponent,
        resolve: {
            modelingExercise: ModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Create Text Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/new',
        component: TextExerciseUpdateComponent,
        resolve: {
            textExercise: TextExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Import Text Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/import/:exerciseId',
        component: TextExerciseUpdateComponent,
        resolve: {
            textExercise: TextExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Edit Text Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId/edit',
        component: TextExerciseUpdateComponent,
        resolve: {
            textExercise: TextExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Create File Upload Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/new',
        component: FileUploadExerciseUpdateComponent,
        resolve: {
            fileUploadExercise: FileUploadExerciseManagementResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Edit File Upload Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/:exerciseId/edit',
        component: FileUploadExerciseUpdateComponent,
        resolve: {
            fileUploadExercise: FileUploadExerciseManagementResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Create Quiz Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/new',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Import Quiz Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/import/:exerciseId',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Edit Quiz Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/edit',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Create Programming Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/new',
        component: ProgrammingExerciseUpdateComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Import programming exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/import/:exerciseId',
        component: ProgrammingExerciseUpdateComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.programmingExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    // Edit Programming Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/edit',
        component: ProgrammingExerciseUpdateComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId',
        component: TextExerciseDetailComponent,
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/:exerciseId',
        component: FileUploadExerciseDetailComponent,
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId',
        component: ModelingExerciseDetailComponent,
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId',
        component: ProgrammingExerciseDetailComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/plagiarism',
        component: PlagiarismInspectorComponent,
        resolve: {
            exercise: ModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.plagiarism.plagiarismDetection',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId/plagiarism',
        component: PlagiarismInspectorComponent,
        resolve: {
            exercise: TextExerciseResolver,
        },
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.plagiarism.plagiarismDetection',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/plagiarism',
        component: PlagiarismInspectorComponent,
        resolve: {
            exercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.plagiarism.plagiarismDetection',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/grading/:tab',
        component: ProgrammingExerciseConfigureGradingComponent,
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/preview',
        component: QuizParticipationComponent,
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'preview',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/solution',
        component: QuizParticipationComponent,
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'solution',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/re-evaluate',
        component: QuizReEvaluateComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/quiz-point-statistic',
        component: QuizPointStatisticComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/quiz-statistic',
        component: QuizStatisticComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/mc-question-statistic/:questionId',
        component: MultipleChoiceQuestionStatisticComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/dnd-question-statistic/:questionId',
        component: DragAndDropQuestionStatisticComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/sa-question-statistic/:questionId',
        component: ShortAnswerQuestionStatisticComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/assessment-dashboard',
        component: AssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.examManagement.assessmentDashboard',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/assessment-dashboard/:exerciseId',
        component: !isOrion ? ExerciseAssessmentDashboardComponent : OrionExerciseAssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.exerciseAssessmentDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/test-assessment-dashboard/:exerciseId',
        component: !isOrion ? ExerciseAssessmentDashboardComponent : OrionExerciseAssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.exerciseAssessmentDashboard.testRunPageHeader',
        },
        canActivate: [UserRouteAccessService],
    },
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':examId/exercise-groups/:exerciseGroupId/' + exerciseType + '-exercises/:exerciseId/scores',
            component: ExerciseScoresComponent,
            data: {
                authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
                pageTitle: 'instructorDashboard.exerciseDashboard',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':examId/exercise-groups/:exerciseGroupId/' + exerciseType + '-exercises/:exerciseId/participations',
            component: ParticipationComponent,
            data: {
                authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                pageTitle: 'artemisApp.participation.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':examId/exercise-groups/:exerciseGroupId/' + exerciseType + '-exercises/:exerciseId/exercise-statistics',
            component: ExerciseStatisticsComponent,
            data: {
                authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
                pageTitle: 'exercise-statistics.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':examId/exercise-groups/:exerciseGroupId/' + exerciseType + '-exercises/:exerciseId/participations/:participationId',
            component: ParticipationSubmissionComponent,
            data: {
                authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                pageTitle: 'artemisApp.participation.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    {
        path: ':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/:exerciseId/submissions/:submissionId/assessment',
        component: FileUploadAssessmentComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/:exerciseId/submissions',
        component: FileUploadAssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.assessmentDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId/submissions',
        component: TextAssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.assessmentDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId/example-submissions',
        component: ExampleSubmissionsComponent,
        resolve: {
            exercise: TextExerciseResolver,
        },
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR],
            pageTitle: 'artemisApp.exampleSubmission.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/code-editor',
        loadChildren: () => import('../../exercises/programming/manage/code-editor/code-editor-management.module').then((m) => m.ArtemisCodeEditorManagementModule),
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId',
        loadChildren: () => import('../../exercises/text/assess/text-submission-assessment.module').then((m) => m.ArtemisTextSubmissionAssessmentModule),
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId/example-submissions/:exampleSubmissionId',
        loadChildren: () => import('../../exercises/text/manage/example-text-submission/example-text-submission.module').then((m) => m.ArtemisExampleTextSubmissionModule),
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId/text-cluster-statistics',
        loadChildren: () => import('../../exercises/text/manage/cluster-statistics/cluster-statistics.module').then((m) => m.ArtemisTextClusterStatisticsModule),
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/submissions',
        component: ModelingAssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.assessmentDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/example-submissions',
        component: ExampleSubmissionsComponent,
        resolve: {
            exercise: ModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR],
            pageTitle: 'artemisApp.exampleSubmission.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/example-submissions/:exampleSubmissionId',
        component: ExampleModelingSubmissionComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.exampleSubmission.home.editor',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/submissions/:submissionId/assessment',
        component: ModelingAssessmentEditorComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.apollonDiagram.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/submissions',
        component: ProgrammingExerciseSubmissionsComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.assessmentDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/submissions/:submissionId/assessment',
        component: !isOrion ? CodeEditorTutorAssessmentContainerComponent : OrionTutorAssessmentComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/:exerciseId/submissions/:submissionId/assessments/:resultId',
        component: FileUploadAssessmentComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/submissions/:submissionId/assessments/:resultId',
        component: ModelingAssessmentEditorComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.apollonDiagram.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/bonus/:bonusId',
        component: BonusComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.todo: Ata',
        },
        canActivate: [UserRouteAccessService],
    },
];

const EXAM_MANAGEMENT_ROUTES = [...examManagementRoute];

export const examManagementState: Routes = [
    {
        path: '',
        children: EXAM_MANAGEMENT_ROUTES,
    },
];
