import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PendingChangesGuard } from 'app/foundation/guard/pending-changes.guard';
import { ComponentCanDeactivate } from 'app/foundation/guard/can-deactivate.model';

describe('PendingChangesGuard', () => {
    let guard: PendingChangesGuard;
    let confirmSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [PendingChangesGuard, { provide: TranslateService, useClass: MockTranslateService }] });
        guard = TestBed.inject(PendingChangesGuard);
        confirmSpy = vi.spyOn(window, 'confirm');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('allows leaving a component without pending changes without asking', () => {
        const component: ComponentCanDeactivate = { canDeactivate: () => true };

        expect(guard.canDeactivate(component)).toBe(true);
        expect(confirmSpy).not.toHaveBeenCalled();
    });

    it('asks with the warning of the component when there are pending changes', () => {
        confirmSpy.mockReturnValue(false);
        const component: ComponentCanDeactivate = { canDeactivate: () => false, canDeactivateWarning: 'Unsaved changes in this form' };

        expect(guard.canDeactivate(component)).toBe(false);
        expect(confirmSpy).toHaveBeenCalledWith('Unsaved changes in this form');
    });

    it('lets the user confirm leaving a component with pending changes', () => {
        confirmSpy.mockReturnValue(true);

        expect(guard.canDeactivate({ canDeactivate: () => false })).toBe(true);
    });

    it('allows leaving when the route has no active component', () => {
        expect(guard.canDeactivate(null)).toBe(true);
        expect(confirmSpy).not.toHaveBeenCalled();
    });
});
