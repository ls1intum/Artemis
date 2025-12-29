import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { AssessmentHeaderComponent } from 'app/assessment/manage/assessment-header/assessment-header.component';
import { MockComponent, MockDirective, MockModule, MockProvider } from 'ng-mocks';
import { AssessmentWarningComponent } from 'app/assessment/manage/assessment-warning/assessment-warning.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockQueryParamsDirective, MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { TextAssessmentAnalytics } from 'app/text/manage/assess/analytics/text-assessment-analytics.service';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Result } from '../../../exercise/shared/entities/result/result.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('AssessmentHeaderComponent', () => {
    setupTestBed({ zoneless: true });
    let component: AssessmentHeaderComponent;
    let fixture: ComponentFixture<AssessmentHeaderComponent>;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [
                MockModule(NgbTooltipModule),
                AssessmentHeaderComponent,
                MockComponent(AssessmentWarningComponent),
                MockDirective(TranslateDirective),
                MockRouterLinkDirective,
                MockQueryParamsDirective,
                FaIconComponent,
            ],
            providers: [
                MockProvider(TextAssessmentAnalytics),
                MockProvider(ArtemisTranslatePipe),
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AssessmentHeaderComponent);
                component = fixture.componentInstance;
                // Set required inputs
                fixture.componentRef.setInput('isLoading', false);
                fixture.componentRef.setInput('saveBusy', false);
                fixture.componentRef.setInput('submitBusy', false);
                fixture.componentRef.setInput('cancelBusy', false);
                fixture.componentRef.setInput('nextSubmissionBusy', false);
                fixture.componentRef.setInput('isTeamMode', false);
                fixture.componentRef.setInput('isAssessor', true);
                fixture.componentRef.setInput('exerciseDashboardLink', []);
                fixture.componentRef.setInput('canOverride', false);
                fixture.componentRef.setInput('assessmentsAreValid', true);
                fixture.componentRef.setInput('hasAssessmentDueDatePassed', true);
                fixture.detectChanges();
            });
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should have correct overrideVisible when result has completionDate and canOverride is true', () => {
        const mockResult = new Result();
        mockResult.completionDate = new Date().toISOString() as any;
        fixture.componentRef.setInput('result', mockResult);
        fixture.componentRef.setInput('canOverride', true);
        fixture.detectChanges();

        expect(component.overrideVisible).toBe(true);
    });

    it('should have overrideVisible false when result has no completionDate', () => {
        const mockResult = new Result();
        fixture.componentRef.setInput('result', mockResult);
        fixture.componentRef.setInput('canOverride', true);
        fixture.detectChanges();

        expect(component.overrideVisible).toBeFalsy();
    });

    it('should have assessNextVisible true under correct conditions', () => {
        const mockResult = new Result();
        mockResult.completionDate = new Date().toISOString() as any;
        fixture.componentRef.setInput('result', mockResult);
        fixture.componentRef.setInput('isAssessor', true);
        fixture.componentRef.setInput('hasComplaint', false);
        fixture.componentRef.setInput('isTeamMode', false);
        fixture.componentRef.setInput('isTestRun', false);
        fixture.detectChanges();

        expect(component.assessNextVisible).toBe(true);
    });

    it('should emit save event on Ctrl+S', () => {
        const saveSpy = vi.spyOn(component.save, 'emit');
        fixture.componentRef.setInput('isAssessor', true);
        fixture.componentRef.setInput('assessmentsAreValid', true);
        fixture.detectChanges();

        const event = new KeyboardEvent('keydown', { ctrlKey: true, key: 's' });
        Object.defineProperty(event, 'preventDefault', { value: vi.fn() });
        component.saveOnControlAndS(event);

        expect(saveSpy).toHaveBeenCalled();
    });

    it('should toggle highlightDifferences', () => {
        expect(component.highlightDifferences()).toBe(false);
        component.toggleHighlightDifferences();
        expect(component.highlightDifferences()).toBe(true);
        component.toggleHighlightDifferences();
        expect(component.highlightDifferences()).toBe(false);
    });

    describe('saveDisabled', () => {
        it('should return true when result has completionDate', () => {
            const mockResult = new Result();
            mockResult.completionDate = new Date().toISOString() as any;
            fixture.componentRef.setInput('result', mockResult);
            fixture.detectChanges();

            expect(component.saveDisabled).toBe(true);
        });

        it('should use saveDisabledWithAssessmentNotePresent when result has assessment note', () => {
            const mockResult = new Result();
            mockResult.assessmentNote = { note: 'test note' } as any;
            fixture.componentRef.setInput('result', mockResult);
            fixture.componentRef.setInput('isAssessor', true);
            fixture.detectChanges();

            expect(component.saveDisabled).toBe(false);
        });

        it('should use saveDisabledWithoutAssessmentNotePresent when no assessment note', () => {
            fixture.componentRef.setInput('assessmentsAreValid', false);
            fixture.detectChanges();

            expect(component.saveDisabled).toBe(true);
        });
    });

    describe('submitDisabled', () => {
        it('should return true when assessments are not valid', () => {
            fixture.componentRef.setInput('assessmentsAreValid', false);
            fixture.detectChanges();

            expect(component.submitDisabled).toBe(true);
        });

        it('should return true when not assessor', () => {
            fixture.componentRef.setInput('isAssessor', false);
            fixture.detectChanges();

            expect(component.submitDisabled).toBe(true);
        });

        it('should return false when all conditions are met', () => {
            fixture.componentRef.setInput('assessmentsAreValid', true);
            fixture.componentRef.setInput('isAssessor', true);
            fixture.componentRef.setInput('saveBusy', false);
            fixture.componentRef.setInput('submitBusy', false);
            fixture.componentRef.setInput('cancelBusy', false);
            fixture.detectChanges();

            expect(component.submitDisabled).toBe(false);
        });
    });

    describe('overrideDisabled', () => {
        it('should return true when overrideVisible is false', () => {
            fixture.componentRef.setInput('canOverride', false);
            fixture.detectChanges();

            expect(component.overrideDisabled).toBe(true);
        });

        it('should return false when overrideVisible and assessments are valid', () => {
            const mockResult = new Result();
            mockResult.completionDate = new Date().toISOString() as any;
            fixture.componentRef.setInput('result', mockResult);
            fixture.componentRef.setInput('canOverride', true);
            fixture.componentRef.setInput('assessmentsAreValid', true);
            fixture.componentRef.setInput('submitBusy', false);
            fixture.detectChanges();

            expect(component.overrideDisabled).toBe(false);
        });
    });

    describe('assessNextDisabled', () => {
        it('should return true when assessNextVisible is false', () => {
            fixture.componentRef.setInput('isTestRun', true);
            fixture.detectChanges();

            expect(component.assessNextDisabled).toBe(true);
        });

        it('should return false when assessNextVisible and not busy', () => {
            const mockResult = new Result();
            mockResult.completionDate = new Date().toISOString() as any;
            fixture.componentRef.setInput('result', mockResult);
            fixture.componentRef.setInput('isAssessor', true);
            fixture.componentRef.setInput('hasComplaint', false);
            fixture.componentRef.setInput('isTeamMode', false);
            fixture.componentRef.setInput('isTestRun', false);
            fixture.componentRef.setInput('nextSubmissionBusy', false);
            fixture.componentRef.setInput('submitBusy', false);
            fixture.detectChanges();

            expect(component.assessNextDisabled).toBe(false);
        });
    });

    describe('submitOnControlAndEnter', () => {
        it('should emit onSubmit when override is not disabled', () => {
            const submitSpy = vi.spyOn(component.onSubmit, 'emit');
            const mockResult = new Result();
            mockResult.completionDate = new Date().toISOString() as any;
            fixture.componentRef.setInput('result', mockResult);
            fixture.componentRef.setInput('canOverride', true);
            fixture.componentRef.setInput('assessmentsAreValid', true);
            fixture.componentRef.setInput('submitBusy', false);
            fixture.detectChanges();

            const event = new KeyboardEvent('keydown', { ctrlKey: true, key: 'Enter' });
            Object.defineProperty(event, 'preventDefault', { value: vi.fn() });
            component.submitOnControlAndEnter(event);

            expect(submitSpy).toHaveBeenCalled();
        });

        it('should emit onSubmit and send analytics when submit is not disabled', () => {
            const submitSpy = vi.spyOn(component.onSubmit, 'emit');
            const analyticsSpy = vi.spyOn(component, 'sendSubmitAssessmentEventToAnalytics');
            fixture.componentRef.setInput('assessmentsAreValid', true);
            fixture.componentRef.setInput('isAssessor', true);
            fixture.detectChanges();

            const event = new KeyboardEvent('keydown', { ctrlKey: true, key: 'Enter' });
            Object.defineProperty(event, 'preventDefault', { value: vi.fn() });
            component.submitOnControlAndEnter(event);

            expect(submitSpy).toHaveBeenCalled();
            expect(analyticsSpy).toHaveBeenCalled();
        });
    });

    describe('assessNextOnControlShiftAndArrowRight', () => {
        it('should emit nextSubmission when assessNextDisabled is false', () => {
            const nextSpy = vi.spyOn(component.nextSubmission, 'emit');
            const analyticsSpy = vi.spyOn(component, 'sendAssessNextEventToAnalytics');
            const mockResult = new Result();
            mockResult.completionDate = new Date().toISOString() as any;
            fixture.componentRef.setInput('result', mockResult);
            fixture.componentRef.setInput('isAssessor', true);
            fixture.componentRef.setInput('hasComplaint', false);
            fixture.componentRef.setInput('isTeamMode', false);
            fixture.componentRef.setInput('isTestRun', false);
            fixture.componentRef.setInput('nextSubmissionBusy', false);
            fixture.componentRef.setInput('submitBusy', false);
            fixture.detectChanges();

            const event = new KeyboardEvent('keydown', { ctrlKey: true, shiftKey: true, key: 'ArrowRight' });
            Object.defineProperty(event, 'preventDefault', { value: vi.fn() });
            component.assessNextOnControlShiftAndArrowRight(event);

            expect(nextSpy).toHaveBeenCalled();
            expect(analyticsSpy).toHaveBeenCalled();
        });

        it('should not emit when assessNextDisabled is true', () => {
            const nextSpy = vi.spyOn(component.nextSubmission, 'emit');
            fixture.componentRef.setInput('isTestRun', true);
            fixture.detectChanges();

            const event = new KeyboardEvent('keydown', { ctrlKey: true, shiftKey: true, key: 'ArrowRight' });
            Object.defineProperty(event, 'preventDefault', { value: vi.fn() });
            component.assessNextOnControlShiftAndArrowRight(event);

            expect(nextSpy).not.toHaveBeenCalled();
        });
    });

    describe('onUseAsExampleSolutionClicked', () => {
        it('should emit useAsExampleSubmission when user confirms', () => {
            const useAsExampleSpy = vi.spyOn(component.useAsExampleSubmission, 'emit');
            vi.spyOn(window, 'confirm').mockReturnValue(true);

            component.onUseAsExampleSolutionClicked();

            expect(useAsExampleSpy).toHaveBeenCalled();
        });

        it('should not emit when user cancels', () => {
            const useAsExampleSpy = vi.spyOn(component.useAsExampleSubmission, 'emit');
            vi.spyOn(window, 'confirm').mockReturnValue(false);

            component.onUseAsExampleSolutionClicked();

            expect(useAsExampleSpy).not.toHaveBeenCalled();
        });
    });
});
