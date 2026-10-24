import {
    programmingExerciseBuildPlanRoute,
    programmingExerciseDetailRoute,
    programmingExerciseGradingRoute,
    programmingExerciseUpdateRoute,
    programmingExerciseVersionHistoryRoute,
} from 'app/programming/manage/programming-exercise-shared.route';
import { quizExerciseUpdateRoute } from 'app/quiz/manage/quiz-exercise-update.route';
import { fileUploadExerciseUpdateRoute } from 'app/fileupload/manage/file-upload-exercise-update.route';
import { textExerciseUpdateRoute } from 'app/text/manage/text-exercise/text-exercise-update.route';
import { modelingExerciseUpdateRoute } from 'app/modeling/manage/modeling-exercise-update.route';
import { Route, Routes, UrlSegment } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { PendingChangesGuard } from 'app/foundation/guard/pending-changes.guard';
import { IS_AT_LEAST_EDITOR, IS_AT_LEAST_INSTRUCTOR, IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';
import { exerciseTypes } from 'app/exercise/shared/entities/exercise/exercise.model';
import { scoresRedirect } from 'app/exercise/participation/participation.route';

import { ModelingExerciseResolver } from 'app/modeling/manage/services/modeling-exercise-resolver.service';
import { CourseResolve, ExamResolve, StudentExamResolve } from 'app/exam/manage/services/exam-management-resolve.service';
import { ProgrammingExerciseResolve } from 'app/programming/manage/services/programming-exercise-resolve.service';
import { TextExerciseResolver } from 'app/text/manage/text-exercise/service/text-exercise-resolver.service';
import { repositorySubRoutes } from 'app/programming/shared/routes/programming-exercise-repository.route';

export const examManagementRoutes: Routes = [
    // ==========================================
    // STANDALONE / FULL-BLEED ROUTES (No Exam Shell)
    // These routes render directly inside CourseManagementContainerComponent
    // without the exam sidebar, exam title bar, or nested scroll container.
    // ==========================================

    // 1. Test-Run Conduction (full-bleed via removePadding in CourseManagementContainerComponent)
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

    // 2. Programming Exercise Code Editor (IDE)
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/code-editor',
        loadChildren: () => import('app/programming/manage/code-editor/code-editor-management-routes').then((m) => m.codeEditorManagementRoutes),
    },

    // 3. Programming Exercise Build Plan Editor
    programmingExerciseBuildPlanRoute(':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/edit-build-plan'),

    // 4. Repository Views (exercise-groups)
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/repository/:repositoryType',
        children: repositorySubRoutes,
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/repository/:repositoryType/:repositoryId',
        children: repositorySubRoutes,
    },

    // 5. Repository Views (student-exams)
    {
        path: ':examId/student-exams/:studentExamId/programming-exercises/:exerciseId/repository/:repositoryType',
        children: repositorySubRoutes,
    },
    {
        path: ':examId/student-exams/:studentExamId/programming-exercises/:exerciseId/repository/:repositoryType/:repositoryId',
        children: repositorySubRoutes,
    },

    // 6. Text Assessment Routes
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId',
        // Only the assessment editors belong out here. The route file's empty path is the text exercise detail page,
        // which is an ordinary exam page and stays inside the shell with the detail pages of the other exercise types.
        // Since this entry is matched before the shell, it has to hand the bare exercise url back rather than claim it.
        canMatch: [(_route: Route, segments: UrlSegment[]) => segments.some((segment) => segment.path === 'submissions')],
        loadChildren: () => import('../../text/manage/assess/text-submission-assessment.route').then((m) => m.textSubmissionAssessmentRoutes),
    },

    // 7. Text Example Submission Editor
    {
        path: ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId/example-submissions/:exampleSubmissionId',
        loadChildren: () => import('../../text/manage/example-text-submission/example-text-submission.route').then((m) => m.exampleTextSubmissionRoute),
    },

    // 8. Modeling Example Submission Editor
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/example-submissions/:exampleSubmissionId',
        loadComponent: () => import('app/modeling/manage/example-modeling/example-modeling-submission.component').then((m) => m.ExampleModelingSubmissionComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.exampleSubmission.home.editor',
        },
        canActivate: [UserRouteAccessService],
    },

    // 9. Modeling Assessment Editors
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/submissions/:submissionId/assessment',
        loadComponent: () => import('app/modeling/manage/assess/modeling-assessment-editor/modeling-assessment-editor.component').then((m) => m.ModelingAssessmentEditorComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/submissions/:submissionId/assessments/:resultId',
        loadComponent: () => import('app/modeling/manage/assess/modeling-assessment-editor/modeling-assessment-editor.component').then((m) => m.ModelingAssessmentEditorComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },

    // 10. Programming Assessment Editors
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/submissions/:submissionId/assessment',
        loadComponent: () =>
            import('app/programming/manage/assess/code-editor-tutor-assessment-container/code-editor-tutor-assessment-container.component').then(
                (m) => m.CodeEditorTutorAssessmentContainerComponent,
            ),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/submissions/:submissionId/assessments/:resultId',
        loadComponent: () =>
            import('app/programming/manage/assess/code-editor-tutor-assessment-container/code-editor-tutor-assessment-container.component').then(
                (m) => m.CodeEditorTutorAssessmentContainerComponent,
            ),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },

    // 11. File Upload Assessment Editors
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

    // ==========================================
    // EXAM MANAGEMENT SHELL (Sidebar + Header)
    // These routes render inside ExamManagementComponent which provides the
    // exam navigation sidebar, exam title bar, and scrollable content area.
    // ==========================================
    {
        path: '',
        loadComponent: () => import('app/exam/manage/exam-management/exam-management.component').then((m) => m.ExamManagementComponent),
        data: {
            hasSidebar: true,
            authorities: IS_AT_LEAST_TUTOR,
            dontReuseOnParamChange: true, // this fixes data not updating when comparing same pages between different exam
        },
        children: [
            {
                path: '',
                loadComponent: () => import('app/exam/manage/exam-management/exam-management-overview.component').then((m) => m.ExamManagementOverviewComponent),
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
                path: ':examId/plagiarism-cases',
                loadChildren: () => import('app/plagiarism/manage/instructor-view/plagiarism-instructor-view.route').then((m) => m.plagiarismInstructorRoutes),
                data: {
                    authorities: IS_AT_LEAST_INSTRUCTOR,
                    pageTitle: 'artemisApp.plagiarism.cases.pageTitle',
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
                redirectTo: ':examId/students',
                pathMatch: 'full',
            },
            {
                path: ':examId/grading',
                loadComponent: () => import('app/assessment/manage/grading/grading.component').then((m) => m.GradingComponent),
                data: {
                    authorities: IS_AT_LEAST_INSTRUCTOR,
                    pageTitle: 'artemisApp.examManagement.gradingSystem',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':examId/bonus',
                loadComponent: () => import('app/assessment/manage/grading/bonus/bonus.component').then((m) => m.BonusComponent),
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
                    import('app/exam/manage/suspicious-behavior/suspicious-sessions-overview/suspicious-sessions-overview.component').then(
                        (m) => m.SuspiciousSessionsOverviewComponent,
                    ),
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
                loadComponent: () => import('app/assessment/manage/grading/grading-key-overview/grading-key-overview.component').then((m) => m.GradingKeyOverviewComponent),
                data: {
                    authorities: IS_AT_LEAST_INSTRUCTOR,
                    pageTitle: 'artemisApp.examManagement.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':examId/student-exams/:studentExamId/summary/overview/bonus-grading-key',
                loadComponent: () => import('app/assessment/manage/grading/grading-key-overview/grading-key-overview.component').then((m) => m.GradingKeyOverviewComponent),
                data: {
                    authorities: IS_AT_LEAST_INSTRUCTOR,
                    pageTitle: 'artemisApp.examManagement.title',
                    forBonus: true,
                },
                canActivate: [UserRouteAccessService],
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
            modelingExerciseUpdateRoute(':examId/exercise-groups/:exerciseGroupId/modeling-exercises/new'),
            // Import Modeling Exercise
            modelingExerciseUpdateRoute(':examId/exercise-groups/:exerciseGroupId/modeling-exercises/import/:exerciseId'),
            // Edit Modeling Exercise
            modelingExerciseUpdateRoute(':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/edit'),
            // Create Text Exercise
            textExerciseUpdateRoute(':examId/exercise-groups/:exerciseGroupId/text-exercises/new'),
            // Import Text Exercise
            textExerciseUpdateRoute(':examId/exercise-groups/:exerciseGroupId/text-exercises/import/:exerciseId'),
            // Edit Text Exercise
            textExerciseUpdateRoute(':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId/edit'),
            // Create File Upload Exercise
            fileUploadExerciseUpdateRoute(':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/new'),
            // Edit File Upload Exercise
            fileUploadExerciseUpdateRoute(':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/:exerciseId/edit'),
            // Import File Upload Exercise
            fileUploadExerciseUpdateRoute(':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/import/:exerciseId', 'artemisApp.fileUploadExercise.home.importLabel'),
            // Create Quiz Exercise
            quizExerciseUpdateRoute(':examId/exercise-groups/:exerciseGroupId/quiz-exercises/new'),
            // Import Quiz Exercise
            quizExerciseUpdateRoute(':examId/exercise-groups/:exerciseGroupId/quiz-exercises/import/:exerciseId'),
            // Edit Quiz Exercise
            quizExerciseUpdateRoute(':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/edit'),
            // Create Programming Exercise
            programmingExerciseUpdateRoute(':examId/exercise-groups/:exerciseGroupId/programming-exercises/new'),
            // Import programming exercise
            programmingExerciseUpdateRoute(':examId/exercise-groups/:exerciseGroupId/programming-exercises/import/:exerciseId', 'artemisApp.programmingExercise.home.importLabel'),
            // Import programming exercise from file
            programmingExerciseUpdateRoute(':examId/exercise-groups/:exerciseGroupId/programming-exercises/import-from-file', 'artemisApp.programmingExercise.home.importLabel'),
            // Edit Programming Exercise
            programmingExerciseUpdateRoute(':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/edit'),
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
            programmingExerciseDetailRoute(':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId'),
            programmingExerciseVersionHistoryRoute(':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/version-history'),
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
            programmingExerciseGradingRoute(':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/grading/:tab'),
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
                    import('app/quiz/manage/statistics/drag-and-drop-question-statistic/drag-and-drop-question-statistic.component').then(
                        (m) => m.DragAndDropQuestionStatisticComponent,
                    ),
                data: {
                    authorities: IS_AT_LEAST_INSTRUCTOR,
                    pageTitle: 'artemisApp.course.home.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId/sa-question-statistic/:questionId',
                loadComponent: () =>
                    import('app/quiz/manage/statistics/short-answer-question-statistic/short-answer-question-statistic.component').then(
                        (m) => m.ShortAnswerQuestionStatisticComponent,
                    ),
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
                loadComponent: () =>
                    import('app/assessment/shared/assessment-dashboard/exercise-dashboard/exercise-assessment-dashboard.component').then(
                        (m) => m.ExerciseAssessmentDashboardComponent,
                    ),
                data: {
                    authorities: IS_AT_LEAST_TUTOR,
                    pageTitle: 'artemisApp.exerciseAssessmentDashboard.home.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':examId/test-assessment-dashboard/:exerciseId',
                loadComponent: () =>
                    import('app/assessment/shared/assessment-dashboard/exercise-dashboard/exercise-assessment-dashboard.component').then(
                        (m) => m.ExerciseAssessmentDashboardComponent,
                    ),
                data: {
                    authorities: IS_AT_LEAST_INSTRUCTOR,
                    pageTitle: 'artemisApp.exerciseAssessmentDashboard.testRunPageHeader',
                },
                canActivate: [UserRouteAccessService],
            },
            ...exerciseTypes.map((exerciseType) => {
                return {
                    path: ':examId/exercise-groups/:exerciseGroupId/' + exerciseType + '-exercises/:exerciseId/scores',
                    redirectTo: scoresRedirect(':examId/exercise-groups/:exerciseGroupId/' + exerciseType + '-exercises/:exerciseId/participations'),
                };
            }),
            ...exerciseTypes.map((exerciseType) => {
                return {
                    path: ':examId/exercise-groups/:exerciseGroupId/' + exerciseType + '-exercises/:exerciseId/participations',
                    loadComponent: () => import('app/exercise/participation/participation.component').then((m) => m.ParticipationComponent),
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
                    loadComponent: () => import('app/exercise/statistics/exercise-statistics.component').then((m) => m.ExerciseStatisticsComponent),
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
                    loadComponent: () => import('app/exercise/participation-submission/participation-submission.component').then((m) => m.ParticipationSubmissionComponent),
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
        ],
    },
];
