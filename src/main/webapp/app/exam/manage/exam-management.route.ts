import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { IS_AT_LEAST_EDITOR, IS_AT_LEAST_INSTRUCTOR, IS_AT_LEAST_TUTOR } from 'app/shared/constants/authority.constants';
import { ParticipationSubmissionComponent } from 'app/exercise/participation-submission/participation-submission.component';

import { ParticipationComponent } from 'app/exercise/participation/participation.component';
import { ExerciseScoresComponent } from 'app/exercise/exercise-scores/exercise-scores.component';

import { CodeEditorTutorAssessmentContainerComponent } from 'app/programming/manage/assess/code-editor-tutor-assessment-container/code-editor-tutor-assessment-container.component';
import { exerciseTypes } from 'app/exercise/shared/entities/exercise/exercise.model';

import { ExerciseStatisticsComponent } from 'app/exercise/statistics/exercise-statistics.component';

import { FileUploadExerciseManagementResolve } from 'app/fileupload/manage/services/file-upload-exercise-management-resolve.service';
import { ModelingExerciseResolver } from 'app/modeling/manage/services/modeling-exercise-resolver.service';
import { CourseResolve, ExamResolve, ExerciseGroupResolve, StudentExamResolve } from 'app/exam/manage/services/exam-management-resolve.service';
import { ProgrammingExerciseResolve } from 'app/programming/manage/services/programming-exercise-resolve.service';
import { TextExerciseResolver } from 'app/text/manage/text-exercise/service/text-exercise-resolver.service';
import { repositorySubRoutes } from 'app/programming/shared/routes/programming-exercise-repository.route';
import { ExerciseAssessmentDashboardComponent } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/exercise-assessment-dashboard.component';

export const examManagementRoutes: Routes = [
    {
        path: '',
        loadComponent: () => import('app/exam/manage/exam-management/exam-management.component').then((m) => m.ExamManagementComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'new',
        loadComponent: () => import('app/exam/manage/exams/update/exam-update.component').then((m) => m.ExamUpdateComponent),
        resolve: {
            exam: ExamResolve,
            course: CourseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/edit',
        loadComponent: () => import('app/exam/manage/exams/update/exam-update.component').then((m) => m.ExamUpdateComponent),
        resolve: {
            exam: ExamResolve,
            course: CourseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/scores',
        loadComponent: () => import('app/exam/manage/exam-scores/exam-scores.component').then((m) => m.ExamScoresComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examScores.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId',
        loadComponent: () => import('app/exam/manage/exams/detail/exam-detail.component').then((m) => m.ExamDetailComponent),
        resolve: {
            exam: ExamResolve,
        },
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examManagement.title',
            requestOptions: {
                withExerciseGroups: true,
            },
        },
        canActivate: [UserRouteAccessService],
    },
    // Exam Import
    {
        path: 'import/:examId',
        loadComponent: () => import('app/exam/manage/exams/update/exam-update.component').then((m) => m.ExamUpdateComponent),
        resolve: {
            exam: ExamResolve,
            course: CourseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examManagement.title',
            requestOptions: {
                forImport: true,
            },
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups',
        loadComponent: () => import('app/exam/manage/exercise-groups/exercise-groups.component').then((m) => m.ExerciseGroupsComponent),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/new',
        loadComponent: () => import('app/exam/manage/exercise-groups/update/exercise-group-update.component').then((m) => m.ExerciseGroupUpdateComponent),
        resolve: {
            exam: ExamResolve,
            exerciseGroup: ExerciseGroupResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/edit',
        loadComponent: () => import('app/exam/manage/exercise-groups/update/exercise-group-update.component').then((m) => m.ExerciseGroupUpdateComponent),
        resolve: {
            exam: ExamResolve,
            exerciseGroup: ExerciseGroupResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId/tutor-effort-statistics',
        loadChildren: () => import('app/text/manage/tutor-effort/tutor-effort-statistics.route').then((m) => m.tutorEffortStatisticsRoute),
    },
    {
        path: ':examId/students',
        loadComponent: () => import('app/exam/manage/students/exam-students.component').then((m) => m.ExamStudentsComponent),
        resolve: {
            exam: ExamResolve,
        },
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examManagement.title',
            requestOptions: {
                withStudents: true,
            },
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/students/verify-attendance',
        loadComponent: () =>
            import('app/exam/manage/students/verify-attendance-check/exam-students-attendance-check.component').then((m) => m.ExamStudentsAttendanceCheckComponent),
        resolve: {
            exam: ExamResolve,
        },
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/student-exams',
        loadComponent: () => import('app/exam/manage/student-exams/student-exams.component').then((m) => m.StudentExamsComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/grading-system',
        loadComponent: () => import('app/assessment/manage/grading-system/grading-system.component').then((m) => m.GradingSystemComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examManagement.gradingSystem',
        },
        canActivate: [UserRouteAccessService],
        loadChildren: () => import('app/assessment/manage/grading-system/grading-system.route').then((m) => m.gradingSystemRoutes),
    },
    {
        path: ':examId/bonus',
        loadComponent: () => import('app/assessment/manage/grading-system/bonus/bonus.component').then((m) => m.BonusComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.bonus.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/suspicious-behavior',
        loadComponent: () => import('app/exam/manage/suspicious-behavior/suspicious-behavior.component').then((m) => m.SuspiciousBehaviorComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examManagement.suspiciousBehavior.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/suspicious-behavior/suspicious-sessions',
        loadComponent: () =>
            import('app/exam/manage/suspicious-behavior/suspicious-sessions-overview/suspicious-sessions-overview.component').then((m) => m.SuspiciousSessionsOverviewComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/test-runs',
        loadComponent: () => import('app/exam/manage/test-runs/test-run-management/test-run-management.component').then((m) => m.TestRunManagementComponent),
        resolve: {
            exam: ExamResolve,
        },
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/test-runs/assess',
        loadComponent: () => import('app/assessment/shared/assessment-dashboard/assessment-dashboard.component').then((m) => m.AssessmentDashboardComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.examManagement.assessmentDashboard',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/test-runs/:studentExamId',
        loadComponent: () => import('app/exam/manage/student-exams/student-exam-detail/student-exam-detail.component').then((m) => m.StudentExamDetailComponent),
        resolve: {
            studentExam: StudentExamResolve,
        },
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/student-exams/:studentExamId',
        loadComponent: () => import('app/exam/manage/student-exams/student-exam-detail/student-exam-detail.component').then((m) => m.StudentExamDetailComponent),
        resolve: {
            studentExam: StudentExamResolve,
        },
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/student-exams/:studentExamId/summary',
        loadComponent: () => import('app/exam/manage/student-exams/student-exam-summary/student-exam-summary.component').then((m) => m.StudentExamSummaryComponent),
        resolve: {
            studentExam: StudentExamResolve,
        },
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/student-exams/:studentExamId/programming-exercises/:exerciseId/repository/:repositoryType',
        children: repositorySubRoutes,
    },
    {
        path: ':examId/student-exams/:studentExamId/programming-exercises/:exerciseId/repository/:repositoryType/:repositoryId',
        children: repositorySubRoutes,
    },
    {
        path: ':examId/student-exams/:studentExamId/exam-timeline',
        loadComponent: () => import('app/exam/manage/student-exams/student-exam-timeline/student-exam-timeline.component').then((m) => m.StudentExamTimelineComponent),
        resolve: {
            studentExam: StudentExamResolve,
        },
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/student-exams/:studentExamId/summary/overview/grading-key',
        loadComponent: () => import('app/assessment/manage/grading-system/grading-key-overview/grading-key-overview.component').then((m) => m.GradingKeyOverviewComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/student-exams/:studentExamId/summary/overview/bonus-grading-key',
        loadComponent: () => import('app/assessment/manage/grading-system/grading-key-overview/grading-key-overview.component').then((m) => m.GradingKeyOverviewComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examManagement.title',
            forBonus: true,
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/test-runs/:testRunId/conduction',
        loadComponent: () => import('app/exam/overview/exam-participation/exam-participation.component').then((m) => m.ExamParticipationComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: ':examId/test-runs/:studentExamId/summary',
        loadComponent: () => import('app/exam/manage/student-exams/student-exam-summary/student-exam-summary.component').then((m) => m.StudentExamSummaryComponent),
        resolve: {
            studentExam: StudentExamResolve,
        },
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Create Modeling Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/new',
        loadComponent: () => import('app/modeling/manage/update/modeling-exercise-update.component').then((m) => m.ModelingExerciseUpdateComponent),
        resolve: {
            modelingExercise: ModelingExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Import Modeling Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/import/:exerciseId',
        loadComponent: () => import('app/modeling/manage/update/modeling-exercise-update.component').then((m) => m.ModelingExerciseUpdateComponent),
        resolve: {
            modelingExercise: ModelingExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Edit Modeling Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/edit',
        loadComponent: () => import('app/modeling/manage/update/modeling-exercise-update.component').then((m) => m.ModelingExerciseUpdateComponent),
        resolve: {
            modelingExercise: ModelingExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Create Text Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/new',
        loadComponent: () => import('app/text/manage/text-exercise/update/text-exercise-update.component').then((m) => m.TextExerciseUpdateComponent),
        resolve: {
            textExercise: TextExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Import Text Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/import/:exerciseId',
        loadComponent: () => import('app/text/manage/text-exercise/update/text-exercise-update.component').then((m) => m.TextExerciseUpdateComponent),
        resolve: {
            textExercise: TextExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Edit Text Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId/edit',
        loadComponent: () => import('app/text/manage/text-exercise/update/text-exercise-update.component').then((m) => m.TextExerciseUpdateComponent),
        resolve: {
            textExercise: TextExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Create File Upload Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/new',
        loadComponent: () => import('app/fileupload/manage/update/file-upload-exercise-update.component').then((m) => m.FileUploadExerciseUpdateComponent),
        resolve: {
            fileUploadExercise: FileUploadExerciseManagementResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Edit File Upload Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/:exerciseId/edit',
        loadComponent: () => import('app/fileupload/manage/update/file-upload-exercise-update.component').then((m) => m.FileUploadExerciseUpdateComponent),
        resolve: {
            fileUploadExercise: FileUploadExerciseManagementResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Import File Upload Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/import/:exerciseId',
        loadComponent: () => import('app/fileupload/manage/update/file-upload-exercise-update.component').then((m) => m.FileUploadExerciseUpdateComponent),
        resolve: {
            fileUploadExercise: FileUploadExerciseManagementResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.fileUploadExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    // Create Quiz Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/new',
        loadComponent: () => import('app/quiz/manage/update/quiz-exercise-update.component').then((m) => m.QuizExerciseUpdateComponent),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Import Quiz Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/import/:exerciseId',
        loadComponent: () => import('app/quiz/manage/update/quiz-exercise-update.component').then((m) => m.QuizExerciseUpdateComponent),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Edit Quiz Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/edit',
        loadComponent: () => import('app/quiz/manage/update/quiz-exercise-update.component').then((m) => m.QuizExerciseUpdateComponent),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Create Programming Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/new',
        loadComponent: () => import('app/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Import programming exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/import/:exerciseId',
        loadComponent: () => import('app/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    // Import programming exercise from file
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/import-from-file',
        loadComponent: () => import('app/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    // Edit Programming Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/edit',
        loadComponent: () => import('app/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId',
        loadComponent: () => import('app/text/manage/detail/text-exercise-detail.component').then((m) => m.TextExerciseDetailComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/:exerciseId',
        loadComponent: () => import('app/fileupload/manage/exercise-details/file-upload-exercise-detail.component').then((m) => m.FileUploadExerciseDetailComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId',
        loadComponent: () => import('app/modeling/manage/detail/modeling-exercise-detail.component').then((m) => m.ModelingExerciseDetailComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId',
        loadComponent: () => import('app/programming/manage/detail/programming-exercise-detail.component').then((m) => m.ProgrammingExerciseDetailComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/repository/:repositoryType',
        children: repositorySubRoutes,
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/repository/:repositoryType/:repositoryId',
        children: repositorySubRoutes,
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId',
        loadComponent: () => import('app/quiz/manage/detail/quiz-exercise-detail.component').then((m) => m.QuizExerciseDetailComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId/plagiarism',
        loadComponent: () => import('app/plagiarism/manage/plagiarism-inspector/plagiarism-inspector.component').then((m) => m.PlagiarismInspectorComponent),
        resolve: {
            exercise: TextExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.plagiarism.plagiarismDetection',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/plagiarism',
        loadComponent: () => import('app/plagiarism/manage/plagiarism-inspector/plagiarism-inspector.component').then((m) => m.PlagiarismInspectorComponent),
        resolve: {
            exercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.plagiarism.plagiarismDetection',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/grading/:tab',
        loadComponent: () =>
            import('app/programming/manage/grading/configure/programming-exercise-configure-grading.component').then((m) => m.ProgrammingExerciseConfigureGradingComponent),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/edit-build-plan',
        loadComponent: () => import('app/programming/manage/build-plan-editor/build-plan-editor.component').then((m) => m.BuildPlanEditorComponent),
        resolve: {
            exercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.buildPlanEditor',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/preview',
        loadComponent: () => import('app/quiz/overview/participation/quiz-participation.component').then((m) => m.QuizParticipationComponent),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'preview',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/solution',
        loadComponent: () => import('app/quiz/overview/participation/quiz-participation.component').then((m) => m.QuizParticipationComponent),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'solution',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/re-evaluate',
        loadComponent: () => import('app/quiz/manage/re-evaluate/quiz-re-evaluate.component').then((m) => m.QuizReEvaluateComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/quiz-point-statistic',
        loadComponent: () => import('app/quiz/manage/statistics/quiz-point-statistic/quiz-point-statistic.component').then((m) => m.QuizPointStatisticComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/quiz-statistic',
        loadComponent: () => import('app/quiz/manage/statistics/quiz-statistic/quiz-statistic.component').then((m) => m.QuizStatisticComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/mc-question-statistic/:questionId',
        loadComponent: () =>
            import('app/quiz/manage/statistics/multiple-choice-question-statistic/multiple-choice-question-statistic.component').then(
                (m) => m.MultipleChoiceQuestionStatisticComponent,
            ),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/dnd-question-statistic/:questionId',
        loadComponent: () =>
            import('app/quiz/manage/statistics/drag-and-drop-question-statistic/drag-and-drop-question-statistic.component').then((m) => m.DragAndDropQuestionStatisticComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/sa-question-statistic/:questionId',
        loadComponent: () =>
            import('app/quiz/manage/statistics/short-answer-question-statistic/short-answer-question-statistic.component').then((m) => m.ShortAnswerQuestionStatisticComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/assessment-dashboard',
        loadComponent: () => import('app/assessment/shared/assessment-dashboard/assessment-dashboard.component').then((m) => m.AssessmentDashboardComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.examManagement.assessmentDashboard',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/assessment-dashboard/:exerciseId',
        component: ExerciseAssessmentDashboardComponent,
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.exerciseAssessmentDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/test-assessment-dashboard/:exerciseId',
        component: ExerciseAssessmentDashboardComponent,
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.exerciseAssessmentDashboard.testRunPageHeader',
        },
        canActivate: [UserRouteAccessService],
    },
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':examId/exercise-groups/:exerciseGroupId/' + exerciseType + '-exercises/:exerciseId/scores',
            component: ExerciseScoresComponent,
            data: {
                authorities: IS_AT_LEAST_TUTOR,
                pageTitle: 'artemisApp.instructorDashboard.exerciseDashboard',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':examId/exercise-groups/:exerciseGroupId/' + exerciseType + '-exercises/:exerciseId/participations',
            component: ParticipationComponent,
            data: {
                authorities: IS_AT_LEAST_TUTOR,
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
                authorities: IS_AT_LEAST_TUTOR,
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
                authorities: IS_AT_LEAST_INSTRUCTOR,
                pageTitle: 'artemisApp.participation.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId/example-submissions',
        loadComponent: () => import('app/exercise/example-submission/example-submissions.component').then((m) => m.ExampleSubmissionsComponent),
        resolve: {
            exercise: TextExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.exampleSubmission.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/code-editor',
        loadChildren: () => import('app/programming/manage/code-editor/code-editor-management-routes').then((m) => m.codeEditorManagementRoutes),
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId',
        loadChildren: () => import('../../text/manage/assess/text-submission-assessment.route').then((m) => m.textSubmissionAssessmentRoutes),
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId/example-submissions/:exampleSubmissionId',
        loadChildren: () => import('../../text/manage/example-text-submission/example-text-submission.route').then((m) => m.exampleTextSubmissionRoute),
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/example-submissions',
        loadComponent: () => import('app/exercise/example-submission/example-submissions.component').then((m) => m.ExampleSubmissionsComponent),
        resolve: {
            exercise: ModelingExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.exampleSubmission.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/example-submissions/:exampleSubmissionId',
        loadComponent: () => import('app/modeling/manage/example-modeling/example-modeling-submission.component').then((m) => m.ExampleModelingSubmissionComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.exampleSubmission.home.editor',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/submissions/:submissionId/assessment',
        loadComponent: () => import('app/modeling/manage/assess/modeling-assessment-editor/modeling-assessment-editor.component').then((m) => m.ModelingAssessmentEditorComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.apollonDiagram.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/submissions/:submissionId/assessment',
        component: CodeEditorTutorAssessmentContainerComponent,
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/submissions/:submissionId/assessments/:resultId',
        component: CodeEditorTutorAssessmentContainerComponent,
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/:exerciseId/submissions/:submissionId/assessment',
        loadComponent: () => import('app/fileupload/manage/assess/file-upload-assessment.component').then((m) => m.FileUploadAssessmentComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/:exerciseId/submissions/:submissionId/assessments/:resultId',
        loadComponent: () => import('app/fileupload/manage/assess/file-upload-assessment.component').then((m) => m.FileUploadAssessmentComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/submissions/:submissionId/assessments/:resultId',
        loadComponent: () => import('app/modeling/manage/assess/modeling-assessment-editor/modeling-assessment-editor.component').then((m) => m.ModelingAssessmentEditorComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.apollonDiagram.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
