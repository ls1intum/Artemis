import { vi } from 'vitest';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_EDITOR } from 'app/foundation/constants/authority.constants';
import { PendingChangesGuard } from 'app/foundation/guard/pending-changes.guard';
import { createExerciseUpdateRoute } from './exercise-update-route';

describe('createExerciseUpdateRoute', () => {
    it('keeps loading lazy while sharing the resolver and fixed editor access policy', () => {
        class UpdateComponent {}
        const loadComponent = vi.fn(async () => UpdateComponent);
        const resolve = { exercise: () => ({ id: 1 }) };
        const updateRoute = createExerciseUpdateRoute({ loadComponent, resolve, pageTitle: 'title' });
        const route = updateRoute('new');
        expect(loadComponent).not.toHaveBeenCalled();
        expect(route).toEqual({
            path: 'new',
            loadComponent,
            resolve,
            data: { authorities: IS_AT_LEAST_EDITOR, pageTitle: 'title' },
            canActivate: [UserRouteAccessService],
        });
    });

    it('lets a context override the title and leaving guard without affecting other routes', () => {
        class UpdateComponent {}
        const updateRoute = createExerciseUpdateRoute({ loadComponent: async () => UpdateComponent, pageTitle: 'title' });
        const course = updateRoute('import', 'importTitle', [PendingChangesGuard]);
        const exam = updateRoute('exam/new');
        expect(course.data?.pageTitle).toBe('importTitle');
        expect(course.canDeactivate).toEqual([PendingChangesGuard]);
        expect(exam.data?.pageTitle).toBe('title');
        expect(exam.canDeactivate).toBeUndefined();
        expect(exam.resolve).toBeUndefined();
        expect(exam.data).not.toBe(course.data);
        expect(exam.canActivate).not.toBe(course.canActivate);
    });
});
