import { ActivatedRouteSnapshot, GuardResult, MaybeAsync, RouterStateSnapshot } from '@angular/router';
import { LectureUnsavedChangesComponent, hasLectureUnsavedChangesGuard } from 'app/lecture/manage/hasLectureUnsavedChanges.guard';
import { signal } from '@angular/core';
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
            hasUnsavedChanges: signal(true),
            confirmDiscardChanges: vi.fn().mockReturnValue(of(true)),
        };
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should allow leaving without asking when nothing changed', async () => {
        component.hasUnsavedChanges = signal(false);

        await expect(runGuard()).resolves.toBe(true);
        expect(component.confirmDiscardChanges).not.toHaveBeenCalled();
    });

    it('should allow leaving without asking when the warning is turned off', async () => {
        component.shouldDisplayDismissWarning = false;

        await expect(runGuard()).resolves.toBe(true);
        expect(component.confirmDiscardChanges).not.toHaveBeenCalled();
    });

    it.each([true, false])('should follow the decision of the user (%s) when there are unsaved changes', async (decision) => {
        component.confirmDiscardChanges = vi.fn().mockReturnValue(of(decision));

        await expect(runGuard()).resolves.toBe(decision);
        expect(component.confirmDiscardChanges).toHaveBeenCalledOnce();
    });

    function runGuard(): Promise<GuardResult> {
        const guardResult: MaybeAsync<GuardResult> = hasLectureUnsavedChangesGuard(component, currentRoute, currentState, nextState);
        return firstValueFrom(guardResult instanceof Observable ? guardResult : of(guardResult as GuardResult));
    }
});
