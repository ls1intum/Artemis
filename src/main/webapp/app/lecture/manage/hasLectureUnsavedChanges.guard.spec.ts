import { ActivatedRouteSnapshot, GuardResult, MaybeAsync, RouterStateSnapshot } from '@angular/router';
import { LectureUnsavedChangesComponent, hasLectureUnsavedChangesGuard } from 'app/lecture/manage/hasLectureUnsavedChanges.guard';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Observable, firstValueFrom, of } from 'rxjs';

describe('hasLectureUnsavedChanges', () => {
    let component: LectureUnsavedChangesComponent;
    const currentRoute = {} as ActivatedRouteSnapshot;
    const currentState = {} as RouterStateSnapshot;
    const nextState = {} as RouterStateSnapshot;

    beforeEach(() => {
        component = {
            shouldDisplayDismissWarning: true,
            confirmLeave: vi.fn().mockReturnValue(of(true)),
        };
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should allow leaving without asking when the warning is turned off', async () => {
        component.shouldDisplayDismissWarning = false;

        await expect(runGuard()).resolves.toBe(true);
        expect(component.confirmLeave).not.toHaveBeenCalled();
    });

    it.each([true, false])('should follow the decision of the page (%s)', async (decision) => {
        component.confirmLeave = vi.fn().mockReturnValue(of(decision));

        await expect(runGuard()).resolves.toBe(decision);
        expect(component.confirmLeave).toHaveBeenCalledOnce();
    });

    function runGuard(): Promise<GuardResult> {
        const guardResult: MaybeAsync<GuardResult> = hasLectureUnsavedChangesGuard(component, currentRoute, currentState, nextState);
        return firstValueFrom(guardResult instanceof Observable ? guardResult : of(guardResult as GuardResult));
    }
});
