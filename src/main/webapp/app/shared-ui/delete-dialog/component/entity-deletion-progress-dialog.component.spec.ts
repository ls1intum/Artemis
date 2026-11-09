import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiDialogConfig, TumAetUiDialogRef } from '@tumaet/ui-angular';
import dayjs from 'dayjs/esm';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EntityDeletionProgressDialogComponent } from 'app/shared-ui/delete-dialog/component/entity-deletion-progress-dialog.component';
import { EntityDeletionProgress, EntityDeletionProgressDialogData, EntityDeletionStatus, EntityDeletionType } from 'app/shared-ui/delete-dialog/entity-deletion-progress.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('EntityDeletionProgressDialogComponent', () => {
    let fixture: ComponentFixture<EntityDeletionProgressDialogComponent>;
    let progress: ReturnType<typeof signal<EntityDeletionProgress | undefined>>;
    let close: ReturnType<typeof vi.fn>;
    let continueInBackground: ReturnType<typeof vi.fn>;

    const message = (overrides: Partial<EntityDeletionProgress> = {}): EntityDeletionProgress => ({
        entityType: EntityDeletionType.EXAM,
        entityId: 3,
        status: EntityDeletionStatus.IN_PROGRESS,
        stepsCompleted: 1,
        totalSteps: 5,
        itemsProcessed: 0,
        totalItems: 0,
        weightedProgressPercent: 30,
        ...overrides,
    });

    const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
    const query = (testId: string) => (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${testId}"]`);

    beforeEach(async () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date('2026-11-07T10:01:00Z'));
        progress = signal<EntityDeletionProgress | undefined>(undefined);
        close = vi.fn();
        continueInBackground = vi.fn();
        const data: EntityDeletionProgressDialogData = {
            target: { entityType: EntityDeletionType.EXAM, entityId: 3, courseId: 1, examId: 3, title: 'Final exam', successAlertKey: 'artemisApp.exam.deleted' },
            progress,
            continueInBackground: continueInBackground as () => void,
        };
        await TestBed.configureTestingModule({
            imports: [EntityDeletionProgressDialogComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: TumAetUiDialogRef, useValue: { close } },
                { provide: TumAetUiDialogConfig, useValue: { data } },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(EntityDeletionProgressDialogComponent);
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('prepares the deletion until the first message arrives and only offers to continue in the background', () => {
        fixture.detectChanges();

        expect(text()).toContain('artemisApp.deletionProgress.preparing');
        expect(text()).toContain('artemisApp.deletionProgress.title.inProgress');
        expect(query('entity-deletion-dismiss')).toBeNull();
        expect(query('entity-deletion-background')).not.toBeNull();
    });

    it('closes the dialog and tells the service when the user continues in the background', () => {
        progress.set(message());
        fixture.detectChanges();

        (query('entity-deletion-background') as HTMLButtonElement).click();

        expect(continueInBackground).toHaveBeenCalledOnce();
        expect(close).toHaveBeenCalledOnce();
    });

    it('offers no background button once the deletion ended', () => {
        progress.set(message({ status: EntityDeletionStatus.COMPLETED, weightedProgressPercent: 100 }));
        fixture.detectChanges();

        expect(query('entity-deletion-background')).toBeNull();
        expect(query('entity-deletion-dismiss')).not.toBeNull();
    });

    it('names the current step and counts the steps', () => {
        progress.set(message({ currentStep: 'testRuns', stepsCompleted: 1, totalSteps: 5 }));
        fixture.detectChanges();

        expect(query('entity-deletion-step')?.textContent).toContain('artemisApp.deletionProgress.step.testRuns');
        expect(query('entity-deletion-steps')).not.toBeNull();
        expect(fixture.componentInstance['stepNumber']()).toBe(2);
        expect(fixture.componentInstance['progressPercentage']()).toBe(30);
    });

    it.each([
        [2, 0, 1],
        [2, 1, 2],
        [7, 3, 4],
        [7, 7, 7],
    ])('counts steps for a run of %i steps with %i completed as step %i, so the total is never assumed', (totalSteps, stepsCompleted, expectedStep) => {
        progress.set(message({ currentStep: 'participations', stepsCompleted, totalSteps }));
        fixture.detectChanges();

        expect(fixture.componentInstance['stepNumber']()).toBe(expectedStep);
        expect(query('entity-deletion-steps')?.textContent).toContain('artemisApp.deletionProgress.steps');
    });

    it('shows how many items are done and which one is deleted right now', () => {
        progress.set(message({ currentStep: 'exercises', itemsProcessed: 2, totalItems: 6, currentItemTitle: 'Sorting' }));
        fixture.detectChanges();

        expect(query('entity-deletion-items')).not.toBeNull();
        expect(text()).toContain('artemisApp.deletionProgress.currentItem');
    });

    it('hides the item line when the step has no items', () => {
        progress.set(message({ currentStep: 'exam' }));
        fixture.detectChanges();

        expect(query('entity-deletion-items')).toBeNull();
    });

    it('estimates the remaining time from the weighted progress', () => {
        progress.set(message({ weightedProgressPercent: 25, startedAt: dayjs('2026-11-07T10:00:00Z') }));
        fixture.detectChanges();

        // 60 s for 25 % leave 180 s
        expect(fixture.componentInstance['remainingTime']()).toBe('3m 0s');
        expect(query('entity-deletion-eta')).not.toBeNull();
    });

    it.each([
        [0, undefined],
        [100, undefined],
    ])('shows no estimate at %s percent', (percent, expected) => {
        progress.set(message({ weightedProgressPercent: percent, startedAt: dayjs('2026-11-07T10:00:00Z') }));

        expect(fixture.componentInstance['remainingTime']()).toBe(expected);
    });

    it('shows no estimate without a start time', () => {
        progress.set(message({ weightedProgressPercent: 50 }));

        expect(fixture.componentInstance['remainingTime']()).toBeUndefined();
    });

    it.each([
        [undefined, 'PREPARING'],
        [EntityDeletionStatus.IN_PROGRESS, 'IN_PROGRESS'],
        [EntityDeletionStatus.COMPLETED, 'COMPLETED'],
        [EntityDeletionStatus.FAILED, 'FAILED'],
    ])('exposes the state %s as data-status=%s, which the E2E tests use to tell success from failure without reading translated text', (status, expected) => {
        progress.set(status ? message({ status }) : undefined);
        fixture.detectChanges();

        expect(query('entity-deletion-progress')?.getAttribute('data-status')).toBe(expected);
    });

    it('reports a completed deletion and lets the user dismiss it', () => {
        progress.set(message({ status: EntityDeletionStatus.COMPLETED, weightedProgressPercent: 100 }));
        fixture.detectChanges();

        expect(text()).toContain('artemisApp.deletionProgress.title.completed');
        expect(query('entity-deletion-step')).toBeNull();

        (query('entity-deletion-dismiss') as HTMLButtonElement).click();

        expect(close).toHaveBeenCalledOnce();
    });

    it('explains a failed deletion with the error of the server and lets the user dismiss it', () => {
        progress.set(message({ status: EntityDeletionStatus.FAILED, errorMessage: 'Repository is locked' }));
        fixture.detectChanges();

        expect(text()).toContain('artemisApp.deletionProgress.title.failed');
        expect(text()).toContain('artemisApp.deletionProgress.failedHint');
        expect(query('entity-deletion-error')?.textContent).toContain('Repository is locked');

        (query('entity-deletion-dismiss') as HTMLButtonElement).click();

        expect(close).toHaveBeenCalledOnce();
    });

    it('omits the error box when the server sent no message', () => {
        progress.set(message({ status: EntityDeletionStatus.FAILED }));
        fixture.detectChanges();

        expect(query('entity-deletion-error')).toBeNull();
    });
});
