import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { Authority } from 'app/shared/constants/authority.constants';
import { ExerciseAssessmentDashboardComponent } from 'app/exercises/shared/dashboards/tutor/exercise-assessment-dashboard.component';
import { ParticipationSubmissionComponent } from 'app/exercises/shared/participation-submission/participation-submission.component';

import { ParticipationComponent } from 'app/exercises/shared/participation/participation.component';
import { ExerciseScoresComponent } from 'app/exercises/shared/exercise-scores/exercise-scores.component';

import { CodeEditorTutorAssessmentContainerComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-container.component';
import { exerciseTypes } from 'app/entities/exercise.model';

import { ExerciseStatisticsComponent } from 'app/exercises/shared/statistics/exercise-statistics.component';

import { OrionExerciseAssessmentDashboardComponent } from 'app/orion/assessment/orion-exercise-assessment-dashboard.component';
import { OrionTutorAssessmentComponent } from 'app/orion/assessment/orion-tutor-assessment.component';
import { isOrion } from 'app/shared/orion/orion';
import { FileUploadExerciseManagementResolve } from 'app/exercises/file-upload/manage/file-upload-exercise-management-resolve.service';
import { ModelingExerciseResolver } from 'app/exercises/modeling/manage/modeling-exercise-resolver.service';
import { CourseResolve, ExamResolve, ExerciseGroupResolve, StudentExamResolve } from 'app/exam/manage/exam-management-resolve.service';

import { LocalVCGuard } from 'app/localvc/localvc-guard.service';
import { ProgrammingExerciseResolve } from 'app/exercises/programming/manage/programming-exercise-resolve.service';
import { TextExerciseResolver } from 'app/exercises/text/manage/text-exercise/text-exercise-resolver.service';

export const examManagementRoute: Routes = [
    {
        path: '',
        loadComponent: () => import('app/exam/manage/exam-management.component').then((m) => m.ExamManagementComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'new',
        loadComponent: () => import('app/exam/manage/exams/exam-update.component').then((m) => m.ExamUpdateComponent),
        resolve: {
            exam: ExamResolve,
            course: CourseResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/edit',
        loadComponent: () => import('app/exam/manage/exams/exam-update.component').then((m) => m.ExamUpdateComponent),
        resolve: {
            exam: ExamResolve,
            course: CourseResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/scores',
        loadComponent: () => import('app/exam/exam-scores/exam-scores.component').then((m) => m.ExamScoresComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.examScores.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId',
        loadComponent: () => import('app/exam/manage/exams/exam-detail.component').then((m) => m.ExamDetailComponent),
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
    // Exam Import
    {
        path: 'import/:examId',
        loadComponent: () => import('app/exam/manage/exams/exam-update.component').then((m) => m.ExamUpdateComponent),
        resolve: {
            exam: ExamResolve,
            course: CourseResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
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
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/new',
        loadComponent: () => import('app/exam/manage/exercise-groups/exercise-group-update.component').then((m) => m.ExerciseGroupUpdateComponent),
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
        loadComponent: () => import('app/exam/manage/exercise-groups/exercise-group-update.component').then((m) => m.ExerciseGroupUpdateComponent),
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
        loadChildren: () => import('../../exercises/text/manage/tutor-effort/tutor-effort-statistics.route').then((m) => m.tutorEffortStatisticsRoute),
    },
    {
        path: ':examId/students',
        loadComponent: () => import('app/exam/manage/students/exam-students.component').then((m) => m.ExamStudentsComponent),
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
        path: ':examId/students/verify-attendance',
        loadComponent: () =>
            import('app/exam/manage/students/verify-attendance-check/exam-students-attendance-check.component').then((m) => m.ExamStudentsAttendanceCheckComponent),
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
        path: ':examId/student-exams',
        loadComponent: () => import('app/exam/manage/student-exams/student-exams.component').then((m) => m.StudentExamsComponent),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/grading-system',
        loadComponent: () => import('app/grading-system/grading-system.component').then((m) => m.GradingSystemComponent),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.gradingSystem',
        },
        canActivate: [UserRouteAccessService],
        loadChildren: () => import('app/grading-system/grading-system.route').then((m) => m.gradingSystemState),
    },
    {
        path: ':examId/bonus',
        loadComponent: () => import('app/grading-system/bonus/bonus.component').then((m) => m.BonusComponent),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.bonus.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/suspicious-behavior',
        loadComponent: () => import('app/exam/manage/suspicious-behavior/suspicious-behavior.component').then((m) => m.SuspiciousBehaviorComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.examManagement.suspiciousBehavior.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/suspicious-behavior/suspicious-sessions',
        loadComponent: () =>
            import('app/exam/manage/suspicious-behavior/suspicious-sessions-overview/suspicious-sessions-overview.component').then((m) => m.SuspiciousSessionsOverviewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/test-runs',
        loadComponent: () => import('app/exam/manage/test-runs/test-run-management.component').then((m) => m.TestRunManagementComponent),
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
        loadComponent: () => import('app/course/dashboards/assessment-dashboard/assessment-dashboard.component').then((m) => m.AssessmentDashboardComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            pageTitle: 'artemisApp.examManagement.assessmentDashboard',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/test-runs/:studentExamId',
        loadComponent: () => import('app/exam/manage/student-exams/student-exam-detail.component').then((m) => m.StudentExamDetailComponent),
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
        loadComponent: () => import('app/exam/manage/student-exams/student-exam-detail.component').then((m) => m.StudentExamDetailComponent),
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
        loadComponent: () => import('app/exam/manage/student-exams/student-exam-summary.component').then((m) => m.StudentExamSummaryComponent),
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
        path: ':examId/student-exams/:studentExamId/summary/exercises/:exerciseId/repository/:participationId',
        loadComponent: () => import('app/localvc/repository-view/repository-view.component').then((m) => m.RepositoryViewComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':examId/student-exams/:studentExamId/summary/exercises/:exerciseId/repository/:participationId/commit-history',
        loadComponent: () => import('app/localvc/commit-history/commit-history.component').then((m) => m.CommitHistoryComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.repository.commitHistory.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':examId/student-exams/:studentExamId/summary/exercises/:exerciseId/repository/:participationId/commit-history/:commitHash',
        loadComponent: () => import('app/localvc/commit-details-view/commit-details-view.component').then((m) => m.CommitDetailsViewComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.repository.commitHistory.commitDetails.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':examId/student-exams/:studentExamId/exam-timeline',
        loadComponent: () => import('app/exam/manage/student-exams/student-exam-timeline/student-exam-timeline.component').then((m) => m.StudentExamTimelineComponent),
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
        loadComponent: () => import('app/grading-system/grading-key-overview/grading-key-overview.component').then((m) => m.GradingKeyOverviewComponent),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/student-exams/:studentExamId/summary/overview/bonus-grading-key',
        loadComponent: () => import('app/grading-system/grading-key-overview/grading-key-overview.component').then((m) => m.GradingKeyOverviewComponent),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
            forBonus: true,
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/test-runs/:testRunId/conduction',
        loadComponent: () => import('app/exam/participate/exam-participation.component').then((m) => m.ExamParticipationComponent),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: ':examId/test-runs/:studentExamId/summary',
        loadComponent: () => import('app/exam/manage/student-exams/student-exam-summary.component').then((m) => m.StudentExamSummaryComponent),
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
        loadComponent: () => import('app/exercises/modeling/manage/modeling-exercise-update.component').then((m) => m.ModelingExerciseUpdateComponent),
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
        loadComponent: () => import('app/exercises/modeling/manage/modeling-exercise-update.component').then((m) => m.ModelingExerciseUpdateComponent),
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
        loadComponent: () => import('app/exercises/modeling/manage/modeling-exercise-update.component').then((m) => m.ModelingExerciseUpdateComponent),
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
        loadComponent: () => import('app/exercises/text/manage/text-exercise/text-exercise-update.component').then((m) => m.TextExerciseUpdateComponent),
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
        loadComponent: () => import('app/exercises/text/manage/text-exercise/text-exercise-update.component').then((m) => m.TextExerciseUpdateComponent),
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
        loadComponent: () => import('app/exercises/text/manage/text-exercise/text-exercise-update.component').then((m) => m.TextExerciseUpdateComponent),
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
        loadComponent: () => import('app/exercises/file-upload/manage/file-upload-exercise-update.component').then((m) => m.FileUploadExerciseUpdateComponent),
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
        loadComponent: () => import('app/exercises/file-upload/manage/file-upload-exercise-update.component').then((m) => m.FileUploadExerciseUpdateComponent),
        resolve: {
            fileUploadExercise: FileUploadExerciseManagementResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Quiz Pool Configuration
    {
        path: ':examId/quiz-pool',
        loadComponent: () => import('app/exercises/quiz/manage/quiz-pool.component').then((m) => m.QuizPoolComponent),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Import File Upload Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/import/:exerciseId',
        loadComponent: () => import('app/exercises/file-upload/manage/file-upload-exercise-update.component').then((m) => m.FileUploadExerciseUpdateComponent),
        resolve: {
            fileUploadExercise: FileUploadExerciseManagementResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.fileUploadExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    // Create Quiz Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/new',
        loadComponent: () => import('app/exercises/quiz/manage/quiz-exercise-update.component').then((m) => m.QuizExerciseUpdateComponent),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Import Quiz Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/import/:exerciseId',
        loadComponent: () => import('app/exercises/quiz/manage/quiz-exercise-update.component').then((m) => m.QuizExerciseUpdateComponent),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Edit Quiz Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/edit',
        loadComponent: () => import('app/exercises/quiz/manage/quiz-exercise-update.component').then((m) => m.QuizExerciseUpdateComponent),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Create Programming Exercise
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/new',
        loadComponent: () => import('app/exercises/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
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
        loadComponent: () => import('app/exercises/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.programmingExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    // Import programming exercise from file
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/import-from-file',
        loadComponent: () => import('app/exercises/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
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
        loadComponent: () => import('app/exercises/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
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
        loadComponent: () => import('app/exercises/text/manage/text-exercise/text-exercise-detail.component').then((m) => m.TextExerciseDetailComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/:exerciseId',
        loadComponent: () => import('app/exercises/file-upload/manage/file-upload-exercise-detail.component').then((m) => m.FileUploadExerciseDetailComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId',
        loadComponent: () => import('app/exercises/modeling/manage/modeling-exercise-detail.component').then((m) => m.ModelingExerciseDetailComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId',
        loadComponent: () => import('app/exercises/programming/manage/programming-exercise-detail.component').then((m) => m.ProgrammingExerciseDetailComponent),
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
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/repository/:repositoryType',
        loadComponent: () => import('app/localvc/repository-view/repository-view.component').then((m) => m.RepositoryViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/repository/:repositoryType/vcs-access-log',
        loadComponent: () => import('app/localvc/vcs-repository-access-log-view/vcs-repository-access-log-view.component').then((m) => m.VcsRepositoryAccessLogViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/repository/:repositoryType/repo/:repositoryId',
        loadComponent: () => import('app/localvc/repository-view/repository-view.component').then((m) => m.RepositoryViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/repository/:repositoryType/commit-history',
        loadComponent: () => import('app/localvc/commit-history/commit-history.component').then((m) => m.CommitHistoryComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/repository/:repositoryType/commit-history/:commitHash',
        loadComponent: () => import('app/localvc/commit-details-view/commit-details-view.component').then((m) => m.CommitDetailsViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/participations/:participationId/repository',
        loadComponent: () => import('app/localvc/repository-view/repository-view.component').then((m) => m.RepositoryViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/participations/:participationId/repository/vcs-access-log',
        loadComponent: () => import('app/localvc/vcs-repository-access-log-view/vcs-repository-access-log-view.component').then((m) => m.VcsRepositoryAccessLogViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/participations/:participationId/repository/commit-history',
        loadComponent: () => import('app/localvc/commit-history/commit-history.component').then((m) => m.CommitHistoryComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/participations/:participationId/repository/commit-history/:commitHash',
        loadComponent: () => import('app/localvc/commit-details-view/commit-details-view.component').then((m) => m.CommitDetailsViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId',
        loadComponent: () => import('app/exercises/quiz/manage/quiz-exercise-detail.component').then((m) => m.QuizExerciseDetailComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/plagiarism',
        loadComponent: () => import('app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component').then((m) => m.PlagiarismInspectorComponent),
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
        loadComponent: () => import('app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component').then((m) => m.PlagiarismInspectorComponent),
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
        loadComponent: () => import('app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component').then((m) => m.PlagiarismInspectorComponent),
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
        loadComponent: () =>
            import('app/exercises/programming/manage/grading/programming-exercise-configure-grading.component').then((m) => m.ProgrammingExerciseConfigureGradingComponent),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/edit-build-plan',
        loadComponent: () => import('app/exercises/programming/manage/build-plan-editor.component').then((m) => m.BuildPlanEditorComponent),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.programmingExercise.buildPlanEditor',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/preview',
        loadComponent: () => import('app/exercises/quiz/participate/quiz-participation.component').then((m) => m.QuizParticipationComponent),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'preview',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/solution',
        loadComponent: () => import('app/exercises/quiz/participate/quiz-participation.component').then((m) => m.QuizParticipationComponent),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'solution',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/re-evaluate',
        loadComponent: () => import('app/exercises/quiz/manage/re-evaluate/quiz-re-evaluate.component').then((m) => m.QuizReEvaluateComponent),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/quiz-point-statistic',
        loadComponent: () => import('app/exercises/quiz/manage/statistics/quiz-point-statistic/quiz-point-statistic.component').then((m) => m.QuizPointStatisticComponent),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/quiz-statistic',
        loadComponent: () => import('app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component').then((m) => m.QuizStatisticComponent),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/mc-question-statistic/:questionId',
        loadComponent: () =>
            import('app/exercises/quiz/manage/statistics/multiple-choice-question-statistic/multiple-choice-question-statistic.component').then(
                (m) => m.MultipleChoiceQuestionStatisticComponent,
            ),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/dnd-question-statistic/:questionId',
        loadComponent: () =>
            import('app/exercises/quiz/manage/statistics/drag-and-drop-question-statistic/drag-and-drop-question-statistic.component').then(
                (m) => m.DragAndDropQuestionStatisticComponent,
            ),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/sa-question-statistic/:questionId',
        loadComponent: () =>
            import('app/exercises/quiz/manage/statistics/short-answer-question-statistic/short-answer-question-statistic.component').then(
                (m) => m.ShortAnswerQuestionStatisticComponent,
            ),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/assessment-dashboard',
        loadComponent: () => import('app/course/dashboards/assessment-dashboard/assessment-dashboard.component').then((m) => m.AssessmentDashboardComponent),
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
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId/example-submissions',
        loadComponent: () => import('app/exercises/shared/example-submission/example-submissions.component').then((m) => m.ExampleSubmissionsComponent),
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
        loadChildren: () => import('../../exercises/programming/manage/code-editor/code-editor-management-routes').then((m) => m.routes),
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId',
        loadChildren: () => import('../../exercises/text/assess/text-submission-assessment.route').then((m) => m.textSubmissionAssessmentRoutes),
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId/example-submissions/:exampleSubmissionId',
        loadChildren: () => import('../../exercises/text/manage/example-text-submission/example-text-submission.route').then((m) => m.exampleTextSubmissionRoute),
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/example-submissions',
        loadComponent: () => import('app/exercises/shared/example-submission/example-submissions.component').then((m) => m.ExampleSubmissionsComponent),
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
        loadComponent: () => import('app/exercises/modeling/manage/example-modeling/example-modeling-submission.component').then((m) => m.ExampleModelingSubmissionComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.exampleSubmission.home.editor',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/submissions/:submissionId/assessment',
        loadComponent: () =>
            import('app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.component').then((m) => m.ModelingAssessmentEditorComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.apollonDiagram.detail.title',
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
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/submissions/:submissionId/assessments/:resultId',
        component: !isOrion ? CodeEditorTutorAssessmentContainerComponent : OrionTutorAssessmentComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/:exerciseId/submissions/:submissionId/assessment',
        loadComponent: () => import('app/exercises/file-upload/assess/file-upload-assessment.component').then((m) => m.FileUploadAssessmentComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/:exerciseId/submissions/:submissionId/assessments/:resultId',
        loadComponent: () => import('app/exercises/file-upload/assess/file-upload-assessment.component').then((m) => m.FileUploadAssessmentComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/submissions/:submissionId/assessments/:resultId',
        loadComponent: () =>
            import('app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.component').then((m) => m.ModelingAssessmentEditorComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.apollonDiagram.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
