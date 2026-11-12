import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_EDITOR, IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';
import { ProgrammingExerciseResolve } from 'app/programming/manage/services/programming-exercise-resolve.service';
import { Routes } from '@angular/router';
import { examManagementRoutes } from 'app/exam/manage/exam-management.route';
import { PendingChangesGuard } from 'app/foundation/guard/pending-changes.guard';
import { routes as modelingRoutes } from 'app/modeling/manage/modeling-exercise.route';
import { modelingExerciseUpdateRoute } from 'app/modeling/manage/modeling-exercise-update.route';
import { textExerciseRoute as textRoutes } from 'app/text/manage/text-exercise/text-exercise.route';
import { textExerciseUpdateRoute } from 'app/text/manage/text-exercise/text-exercise-update.route';
import { routes as fileUploadRoutes } from 'app/fileupload/manage/file-upload-exercise-management.route';
import { fileUploadExerciseUpdateRoute } from 'app/fileupload/manage/file-upload-exercise-update.route';
import { routes as programmingRoutes } from 'app/programming/manage/programming-exercise-management.route';
import { programmingExerciseUpdateRoute } from 'app/programming/manage/programming-exercise-shared.route';
import { quizManagementRoute as quizRoutes } from 'app/quiz/manage/quiz-management.route';
import { quizExerciseUpdateRoute } from 'app/quiz/manage/quiz-exercise-update.route';

const courseRoutes: { name: string; routes: Routes; expectedPaths: string[] }[] = [
    {
        name: 'modeling',
        routes: modelingRoutes,
        expectedPaths: [
            'modeling-exercises/new',
            'modeling-exercises/:exerciseId/edit',
            'modeling-exercises/:exerciseId/import',
            'modeling-exercises/:exerciseId',
            'modeling-exercises/:exerciseId/example-submissions',
            'modeling-exercises/:exerciseId/exercise-statistics',
            'modeling-exercises/:exerciseId/example-submissions/:exampleSubmissionId',
            'modeling-exercises/:exerciseId/submissions/:submissionId',
        ],
    },
    {
        name: 'text',
        routes: textRoutes,
        expectedPaths: [
            'text-exercises/new',
            'text-exercises/:exerciseId',
            'text-exercises/:exerciseId/edit',
            'text-exercises/:exerciseId/import',
            'text-exercises/:exerciseId/plagiarism',
            'text-exercises/:exerciseId/example-submissions',
            'text-exercises/:exerciseId/exercise-statistics',
            'text-exercises/:exerciseId/tutor-effort-statistics',
            'text-exercises/:exerciseId/example-submissions/:exampleSubmissionId',
        ],
    },
    {
        name: 'fileUpload',
        routes: fileUploadRoutes,
        expectedPaths: [
            'file-upload-exercises/new',
            'file-upload-exercises/:exerciseId/import',
            'file-upload-exercises/:exerciseId/edit',
            'file-upload-exercises/:exerciseId',
            'file-upload-exercises/:exerciseId/exercise-statistics',
            'file-upload-exercises/:exerciseId/submissions/:submissionId',
        ],
    },
    {
        name: 'programming',
        routes: programmingRoutes,
        expectedPaths: [
            'programming-exercises/new',
            'programming-exercises/:exerciseId/edit',
            'programming-exercises/import/:exerciseId',
            'programming-exercises/import-from-file',
            'programming-exercises/import-from-sharing',
            'programming-exercises/:exerciseId',
            'programming-exercises/:exerciseId/version-history',
            'programming-exercises/:exerciseId/generation/runs/:runId',
            'programming-exercises/:exerciseId/generation',
            'programming-exercises/:exerciseId/plagiarism',
            'programming-exercises/:exerciseId/grading/:tab',
            'programming-exercises/:exerciseId/exercise-statistics',
            'programming-exercises/:exerciseId/comprehension-check',
            'programming-exercises/:exerciseId/comprehension-check/review',
            'programming-exercises/:exerciseId/edit-build-plan',
            'programming-exercises/:exerciseId/repository/:repositoryType',
            'programming-exercises/:exerciseId/repository/:repositoryType/:repositoryId',
            'programming-exercises/:exerciseId/submissions/:submissionId/assessment',
        ],
    },
    {
        name: 'quiz',
        routes: quizRoutes,
        expectedPaths: [
            'quiz-exercises/new',
            'quiz-exercises/:exerciseId',
            'quiz-exercises/:exerciseId/re-evaluate',
            'quiz-exercises/:exerciseId/edit',
            'quiz-exercises/:exerciseId/import',
            'quiz-exercises/:exerciseId/preview',
            'quiz-exercises/:exerciseId/solution',
        ],
    },
];

describe('exercise management route composition', () => {
    it.each(courseRoutes)('keeps the ordered $name paths, including detail and nested editor routes', ({ routes, expectedPaths }) => {
        expect(routes.map((route) => route.path)).toEqual(expectedPaths);
    });

    it('keeps exam update paths in their established order with context-specific titles', () => {
        const children = examManagementRoutes.find((route) => route.path === '' && route.children)?.children ?? [];
        const prefix = ':examId/exercise-groups/:exerciseGroupId/';
        const expected = [
            modelingExerciseUpdateRoute(prefix + 'modeling-exercises/new'),
            modelingExerciseUpdateRoute(prefix + 'modeling-exercises/import/:exerciseId'),
            modelingExerciseUpdateRoute(prefix + 'modeling-exercises/:exerciseId/edit'),
            textExerciseUpdateRoute(prefix + 'text-exercises/new'),
            textExerciseUpdateRoute(prefix + 'text-exercises/import/:exerciseId'),
            textExerciseUpdateRoute(prefix + 'text-exercises/:exerciseId/edit'),
            fileUploadExerciseUpdateRoute(prefix + 'file-upload-exercises/new'),
            fileUploadExerciseUpdateRoute(prefix + 'file-upload-exercises/:exerciseId/edit'),
            fileUploadExerciseUpdateRoute(prefix + 'file-upload-exercises/import/:exerciseId', 'artemisApp.fileUploadExercise.home.importLabel'),
            quizExerciseUpdateRoute(prefix + 'quiz-exercises/new'),
            quizExerciseUpdateRoute(prefix + 'quiz-exercises/import/:exerciseId'),
            quizExerciseUpdateRoute(prefix + 'quiz-exercises/:exerciseId/edit'),
            programmingExerciseUpdateRoute(prefix + 'programming-exercises/new'),
            programmingExerciseUpdateRoute(prefix + 'programming-exercises/import/:exerciseId', 'artemisApp.programmingExercise.home.importLabel'),
            programmingExerciseUpdateRoute(prefix + 'programming-exercises/import-from-file', 'artemisApp.programmingExercise.home.importLabel'),
            programmingExerciseUpdateRoute(prefix + 'programming-exercises/:exerciseId/edit'),
        ];
        const first = children.findIndex((route) => route.path === expected[0].path);
        expect(first).toBeGreaterThan(-1);
        expect(children.slice(first, first + expected.length)).toEqual(expected);
    });

    it('keeps programming detail, grading, history and build-plan policies identical in both contexts', () => {
        const shell = examManagementRoutes.find((route) => route.path === '' && route.children);
        const examRoutes = [...examManagementRoutes, ...(shell?.children ?? [])];
        for (const context of [
            { routes: programmingRoutes, prefix: 'programming-exercises/' },
            { routes: examRoutes, prefix: ':examId/exercise-groups/:exerciseGroupId/programming-exercises/' },
        ]) {
            for (const policy of [
                {
                    suffix: ':exerciseId',
                    title: 'artemisApp.programmingExercise.home.title',
                    editor: false,
                    resolve: { programmingExercise: ProgrammingExerciseResolve },
                    buildPlan: false,
                },
                { suffix: ':exerciseId/grading/:tab', title: 'artemisApp.programmingExercise.home.title', editor: true, resolve: undefined, buildPlan: false },
                { suffix: ':exerciseId/version-history', title: 'artemisApp.exercise.versionHistory.title', editor: true, resolve: undefined, buildPlan: false },
                {
                    suffix: ':exerciseId/edit-build-plan',
                    title: 'artemisApp.programmingExercise.buildPlanEditor',
                    editor: true,
                    resolve: { exercise: ProgrammingExerciseResolve },
                    buildPlan: true,
                },
            ]) {
                const route = context.routes.find((candidate) => candidate.path === context.prefix + policy.suffix);
                expect(route, context.prefix + policy.suffix).toBeDefined();
                expect(route!.data).toEqual({ pageTitle: policy.title, authorities: policy.editor ? IS_AT_LEAST_EDITOR : IS_AT_LEAST_TUTOR });
                expect(route!.resolve).toEqual(policy.resolve);
                expect(route!.canActivate?.[0]).toBe(UserRouteAccessService);
                expect(route!.canActivate).toHaveLength(policy.buildPlan ? 2 : 1);
                expect(route!.canDeactivate).toEqual(policy.buildPlan ? [PendingChangesGuard] : undefined);
                if (policy.buildPlan) {
                    expect(route!.canActivate?.[1]).toBeTypeOf('function');
                }
            }
        }
    });

    it('keeps the quiz leaving guard in course management and preserves course import titles', () => {
        for (const { name, routes } of courseRoutes) {
            const updates = routes.filter((route) => route.path?.endsWith('/new') || route.path?.endsWith('/edit') || route.path?.includes('/import'));
            for (const route of updates) {
                expect(route.canDeactivate).toEqual(name === 'quiz' ? [PendingChangesGuard] : undefined);
                const isImport = route.path?.includes('/import');
                expect(route.data?.pageTitle).toBe(`artemisApp.${name}Exercise.home.${isImport ? 'importLabel' : 'title'}`);
            }
        }
    });
});
