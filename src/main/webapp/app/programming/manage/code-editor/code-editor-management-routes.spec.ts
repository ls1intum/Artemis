import { describe, expect, it } from 'vitest';
import { codeEditorManagementRoutes } from 'app/programming/manage/code-editor/code-editor-management-routes';
import { IS_AT_LEAST_EDITOR } from 'app/foundation/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CodeEditorStudentContainerComponent } from 'app/programming/overview/code-editor-student-container/code-editor-student-container.component';

describe('codeEditorManagementRoutes', () => {
    const PARTICIPATION_PATH = 'participations/:participationId';
    const REPOSITORY_PATH = ':repositoryType/:repositoryId';

    const participationRoute = codeEditorManagementRoutes.find((route) => route.path === PARTICIPATION_PATH);

    it('lets editors and instructors open the code of any participation in the code editor', async () => {
        expect(participationRoute).toBeDefined();
        expect(participationRoute!.data?.['authorities']).toEqual(IS_AT_LEAST_EDITOR);
        expect(participationRoute!.canActivate).toEqual([UserRouteAccessService]);
        expect(await (participationRoute!.loadComponent as () => Promise<unknown>)()).toBe(CodeEditorStudentContainerComponent);
    });

    it('shows that editor as a page of its own, with the instructions the exercise page would show next to it', () => {
        expect(participationRoute!.data?.['fullPageEditor']).toBe(true);
    });

    it('matches before the repositories of the exercise, which would take "participations" for a repository type', () => {
        const paths = codeEditorManagementRoutes.map((route) => route.path);

        expect(paths.indexOf(REPOSITORY_PATH)).toBeGreaterThan(-1);
        expect(paths.indexOf(PARTICIPATION_PATH)).toBeGreaterThan(-1);
        expect(paths.indexOf(PARTICIPATION_PATH)).toBeLessThan(paths.indexOf(REPOSITORY_PATH));
    });
});
