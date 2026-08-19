import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideTranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { MockProvider } from 'ng-mocks';
import { describe, expect, it } from 'vitest';

import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from 'test/helpers/mocks/service/mock-feature-toggle.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { CompetencyOrchestrationApiService } from 'app/atlas/shared/services/competency-orchestration-api.service';
import { AppliedActionType, CompetencyOrchestrationStatus } from 'app/atlas/shared/dto/competency-orchestration-dto';
import { OrchestrationResultDialogComponent } from 'app/atlas/shared/orchestration-result-dialog/orchestration-result-dialog.component';
import { AtlasOrchestrationTriggerComponent } from 'app/atlas/manage/orchestration-trigger/atlas-orchestration-trigger.component';

describe('AtlasOrchestrationTriggerComponent', () => {
    let comp: AtlasOrchestrationTriggerComponent;
    let fixture: ComponentFixture<AtlasOrchestrationTriggerComponent>;
    let alertService: AlertService;
    let apiService: CompetencyOrchestrationApiService;

    const exercise = { id: 123 } as Exercise;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [],
            providers: [
                MockProvider(AlertService),
                // The component self-hides unless the Atlas module is active; enable it so the button and dialog render.
                MockProvider(ProfileService, { isModuleFeatureActive: () => true }),
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                provideHttpClient(),
                provideHttpClientTesting(),
                provideTranslateService(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(AtlasOrchestrationTriggerComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', exercise);
        alertService = TestBed.inject(AlertService);
        apiService = TestBed.inject(CompetencyOrchestrationApiService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should open the orchestration result dialog with applied actions when the run succeeds', async () => {
        vi.spyOn(apiService, 'runForExercise').mockResolvedValue({
            status: CompetencyOrchestrationStatus.Success,
            summary: 'Assigned this exercise to Recursion.',
            appliedActions: [
                {
                    type: AppliedActionType.Assign,
                    competencyId: 42,
                    competencyTitle: 'Recursion',
                    exerciseId: 123,
                    weight: 1.0,
                    detail: 'Linked exercise to Recursion (weight 1.00).',
                    justification: 'Exercise tests recursion patterns.',
                },
            ],
        });

        await comp.triggerAtlasOrchestrator();
        fixture.detectChanges();

        const dialog = fixture.debugElement.query(By.directive(OrchestrationResultDialogComponent)).componentInstance as OrchestrationResultDialogComponent;
        expect(dialog.visible()).toBe(true);
        expect(dialog.summaryMessage()).toBe('Assigned this exercise to Recursion.');
        expect(dialog.appliedActions()).toHaveLength(1);
        expect(dialog.appliedActions()[0].type).toBe(AppliedActionType.Assign);
    });

    it('should show warning toast and dialog when orchestrator returns PARTIAL', async () => {
        const addAlertSpy = vi.spyOn(alertService, 'addAlert');
        vi.spyOn(apiService, 'runForExercise').mockResolvedValue({
            status: CompetencyOrchestrationStatus.Partial,
            summary: 'Orchestrator failed after applying 1 action(s).',
            appliedActions: [
                {
                    type: AppliedActionType.Create,
                    competencyId: 7,
                    competencyTitle: 'Loops',
                    detail: 'Created competency Loops',
                    justification: 'Exercise teaches loops',
                },
            ],
        });

        await comp.triggerAtlasOrchestrator();
        fixture.detectChanges();

        expect(addAlertSpy).toHaveBeenCalledWith({
            type: AlertType.WARNING,
            message: 'Orchestrator failed after applying 1 action(s).',
            disableTranslation: true,
        });
        const dialog = fixture.debugElement.query(By.directive(OrchestrationResultDialogComponent)).componentInstance as OrchestrationResultDialogComponent;
        expect(dialog.visible()).toBe(true);
        expect(dialog.appliedActions()).toHaveLength(1);
        expect(dialog.appliedActions()[0].type).toBe(AppliedActionType.Create);
    });

    it('should error when Atlas orchestrator returns FAILED', async () => {
        const addAlertSpy = vi.spyOn(alertService, 'addAlert');
        vi.spyOn(apiService, 'runForExercise').mockRejectedValue(
            new HttpErrorResponse({
                status: 503,
                error: { status: CompetencyOrchestrationStatus.Failed, summary: 'model not configured' },
            }),
        );

        await comp.triggerAtlasOrchestrator();
        fixture.detectChanges();

        expect(addAlertSpy).toHaveBeenCalledWith({ type: AlertType.DANGER, message: 'model not configured', disableTranslation: true });
        const dialog = fixture.debugElement.query(By.directive(OrchestrationResultDialogComponent)).componentInstance as OrchestrationResultDialogComponent;
        expect(dialog.visible()).toBe(false);
    });

    it('should surface the summary when Atlas orchestrator returns INTERNAL_ERROR (500)', async () => {
        const addAlertSpy = vi.spyOn(alertService, 'addAlert');
        vi.spyOn(apiService, 'runForExercise').mockRejectedValue(
            new HttpErrorResponse({
                status: 500,
                error: { status: CompetencyOrchestrationStatus.Failed, summary: 'Atlas orchestrator run failed.' },
            }),
        );

        await comp.triggerAtlasOrchestrator();
        fixture.detectChanges();

        // A 500 must not fall through to the silent onError path — the returned summary is shown.
        expect(addAlertSpy).toHaveBeenCalledWith({ type: AlertType.DANGER, message: 'Atlas orchestrator run failed.', disableTranslation: true });
    });

    it('should error when Atlas orchestrator request throws', async () => {
        const addAlertSpy = vi.spyOn(alertService, 'addAlert');
        vi.spyOn(apiService, 'runForExercise').mockRejectedValue(new Error('boom'));

        await comp.triggerAtlasOrchestrator();

        // The catch path uses onError(), which addAlerts the underlying error message.
        expect(addAlertSpy).toHaveBeenCalledWith({ type: AlertType.DANGER, message: 'boom', disableTranslation: true });
    });

    it('should not call the API when the exercise has no id', async () => {
        const runSpy = vi.spyOn(apiService, 'runForExercise');
        fixture.componentRef.setInput('exercise', {} as Exercise);

        await comp.triggerAtlasOrchestrator();

        expect(runSpy).not.toHaveBeenCalled();
    });

    it('should apply the provided buttonClass to the trigger button', () => {
        fixture.componentRef.setInput('buttonClass', 'btn btn-outline-primary btn-sm atlas-trigger-marker');
        fixture.detectChanges();

        const button = fixture.debugElement.query(By.css('button'));
        expect(button.nativeElement.className).toContain('atlas-trigger-marker');
    });

    it('should hide the trigger entirely when the Atlas module is inactive', () => {
        TestBed.resetTestingModule();
        return TestBed.configureTestingModule({
            imports: [],
            providers: [
                MockProvider(AlertService),
                MockProvider(ProfileService, { isModuleFeatureActive: () => false }),
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                provideHttpClient(),
                provideHttpClientTesting(),
                provideTranslateService(),
            ],
        })
            .compileComponents()
            .then(() => {
                const localFixture = TestBed.createComponent(AtlasOrchestrationTriggerComponent);
                localFixture.componentRef.setInput('exercise', exercise);
                localFixture.detectChanges();

                // Module off: neither the button nor the result dialog is rendered — hosts need no Atlas-specific guard.
                expect(localFixture.debugElement.query(By.css('button'))).toBeNull();
                expect(localFixture.debugElement.query(By.directive(OrchestrationResultDialogComponent))).toBeNull();
            });
    });
});
