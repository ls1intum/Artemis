import { describe, expect, it } from 'vitest';
import { CanMatchFn, Route, Routes, UrlSegment } from '@angular/router';
import { examManagementRoutes } from 'app/exam/manage/exam-management.route';
import { courseManagementRoutes } from 'app/course/manage/course-management.route';
import appRoutes from 'app/app.routes';
import { IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

describe('examManagementRoutes', () => {
    const TEXT_EXERCISE_PATH = ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId';

    const shellRoute = examManagementRoutes.find((route) => route.path === '' && !!route.children?.length);
    const standaloneRoutes = examManagementRoutes.filter((route) => route !== shellRoute);

    const segmentsOf = (url: string) => url.split('/').map((path) => new UrlSegment(path, {}));

    /**
     * The full-width workspaces are matched before the shell, so anything they claim never reaches the shell's own
     * children. These guards keep that ordering from swallowing ordinary exam pages.
     */
    it('keeps the post-exam tutor training routes outside editor guards and resolvers', async () => {
        const root = appRoutes.find((route) => route.path === 'course-management' && !!route.loadChildren)!;
        const container = courseManagementRoutes.find((route) => route.path === '' && !!route.children)!;
        const examMount = container.children!.find((route) => route.path === ':courseId/exams')!;
        for (const ancestor of [root, container, examMount]) {
            expect(ancestor.canActivate).toBeUndefined();
            expect(ancestor.resolve).toBeUndefined();
        }
        expect(examMount.data?.['authorities']).toEqual(IS_AT_LEAST_TUTOR);
        const modeling = standaloneRoutes.find(
            (route) => route.path === ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId/example-submissions/:exampleSubmissionId',
        )!;
        expect(modeling.resolve).toBeUndefined();
        expect(modeling.data?.['authorities']).toEqual(IS_AT_LEAST_TUTOR);
        expect(modeling.canActivate).toEqual([UserRouteAccessService]);
        const text = standaloneRoutes.find(
            (route) => route.path === ':examId/exercise-groups/:exerciseGroupId/text-exercises/:exerciseId/example-submissions/:exampleSubmissionId',
        )!;
        expect(text.resolve).toBeUndefined();
        expect(text.canActivate).toBeUndefined();
        const children = await (text.loadChildren as () => Promise<Routes>)();
        expect(children[0].resolve).toBeUndefined();
        expect(children[0].data?.['authorities']).toEqual(IS_AT_LEAST_TUTOR);
        expect(children[0].canActivate).toEqual([UserRouteAccessService]);
    });

    it('matches the full-width workspaces before the shell', () => {
        expect(shellRoute).toBeDefined();
        expect(examManagementRoutes.indexOf(shellRoute!)).toBe(examManagementRoutes.length - 1);
        expect(standaloneRoutes.some((route) => route.path === ':examId/test-runs/:testRunId/conduction')).toBe(true);
    });

    it('leaves the text exercise detail page to the shell and takes only its assessment editors', () => {
        const standaloneTextRoute = standaloneRoutes.find((route) => route.path === TEXT_EXERCISE_PATH);
        expect(standaloneTextRoute).toBeDefined();
        expect(shellRoute!.children?.some((route) => route.path === TEXT_EXERCISE_PATH)).toBe(true);

        // The lazily loaded config has an empty path of its own (the detail page), so without this guard the
        // standalone entry would claim the bare exercise url and render it without the exam sidebar and title bar.
        const canMatch = standaloneTextRoute!.canMatch?.[0] as CanMatchFn;
        expect(canMatch).toBeDefined();

        // The guard only reads the segments; the route and the partial snapshot are there to satisfy the signature.
        const snapshot = {} as Parameters<CanMatchFn>[2];
        const detailUrl = '39/exercise-groups/41/text-exercises/29';
        expect(canMatch({} as Route, segmentsOf(detailUrl), snapshot)).toBe(false);
        expect(canMatch({} as Route, segmentsOf(`${detailUrl}/submissions/7/assessment`), snapshot)).toBe(true);
        expect(canMatch({} as Route, segmentsOf(`${detailUrl}/submissions/new/assessment`), snapshot)).toBe(true);
    });

    it('keeps the exercise detail pages of the other exercise types inside the shell', () => {
        const detailPaths = [
            ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/:exerciseId',
            ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId',
            ':examId/exercise-groups/:exerciseGroupId/file-upload-exercises/:exerciseId',
            ':examId/exercise-groups/:exerciseGroupId/quiz-exercises/:exerciseId',
        ];

        for (const path of detailPaths) {
            expect(
                shellRoute!.children?.some((route) => route.path === path),
                path,
            ).toBe(true);
            expect(
                standaloneRoutes.some((route) => route.path === path),
                path,
            ).toBe(false);
        }
    });
});
