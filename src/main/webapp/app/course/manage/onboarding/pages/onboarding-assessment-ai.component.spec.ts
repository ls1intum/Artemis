import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { MockDirective } from 'ng-mocks';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { RouterModule } from '@angular/router';

import { OnboardingAssessmentAiComponent } from './onboarding-assessment-ai.component';
import { Course } from 'app/course/shared/entities/course.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { typeIntoInputNumber } from 'src/test/javascript/spec/helpers/input-number-test-helpers';
import { MAX_GRADING_POINTS } from 'app/foundation/constants/input.constants';

describe('OnboardingAssessmentAiComponent', () => {
    let comp: OnboardingAssessmentAiComponent;
    let fixture: ComponentFixture<OnboardingAssessmentAiComponent>;
    let course: Course;

    beforeEach(async () => {
        course = new Course();
        course.id = 1;
        course.title = 'Test Course';
        course.maxComplaints = 0;
        course.maxTeamComplaints = 0;
        course.maxComplaintTimeDays = 0;
        course.maxComplaintTextLimit = 0;
        course.maxComplaintResponseTextLimit = 0;
        course.maxRequestMoreFeedbackTimeDays = 0;

        await TestBed.configureTestingModule({
            imports: [OnboardingAssessmentAiComponent, FormsModule, RouterModule.forRoot([])],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        })
            .overrideComponent(OnboardingAssessmentAiComponent, {
                remove: { imports: [TranslateDirective] },
                add: { imports: [MockDirective(TranslateDirective)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(OnboardingAssessmentAiComponent);
        fixture.componentRef.setInput('course', course);
        comp = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize with the provided course', () => {
        expect(comp).toBeTruthy();
        expect(comp.course()).toEqual(course);
    });

    describe('complaintsToggled', () => {
        it('should initialize to false when maxComplaintTimeDays is 0', () => {
            expect(comp.complaintsToggled()).toBe(false);
        });

        it('should not reset when course input changes after initialization', () => {
            comp.toggleComplaints();
            expect(comp.complaintsToggled()).toBe(true);

            // Simulate parent updating course after field value change (the A1 bug scenario)
            const updatedCourse = { ...course, maxComplaintTimeDays: 0 } as Course;
            fixture.componentRef.setInput('course', updatedCourse);
            fixture.detectChanges();

            // Toggle should remain true despite field value being 0
            expect(comp.complaintsToggled()).toBe(true);
        });
    });

    describe('requestMoreFeedbackToggled', () => {
        it('should initialize to false when maxRequestMoreFeedbackTimeDays is 0', () => {
            expect(comp.requestMoreFeedbackToggled()).toBe(false);
        });

        it('should not reset when course input changes after initialization', () => {
            comp.toggleRequestMoreFeedback();
            expect(comp.requestMoreFeedbackToggled()).toBe(true);

            const updatedCourse = { ...course, maxRequestMoreFeedbackTimeDays: 0 } as Course;
            fixture.componentRef.setInput('course', updatedCourse);
            fixture.detectChanges();

            expect(comp.requestMoreFeedbackToggled()).toBe(true);
        });
    });

    describe('toggleComplaints', () => {
        it('should enable complaints with default values', () => {
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.toggleComplaints();

            expect(comp.complaintsToggled()).toBe(true);
            expect(emitSpy).toHaveBeenCalled();
            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.maxComplaints).toBe(3);
            expect(emitted.maxTeamComplaints).toBe(3);
            expect(emitted.maxComplaintTimeDays).toBe(7);
            expect(emitted.maxComplaintTextLimit).toBe(2000);
            expect(emitted.maxComplaintResponseTextLimit).toBe(2000);
        });

        it('should disable complaints by resetting values to 0', () => {
            // First enable
            comp.toggleComplaints();
            expect(comp.complaintsToggled()).toBe(true);

            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');
            // Then disable
            comp.toggleComplaints();

            expect(comp.complaintsToggled()).toBe(false);
            expect(emitSpy).toHaveBeenCalled();
            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.maxComplaints).toBe(0);
            expect(emitted.maxTeamComplaints).toBe(0);
            expect(emitted.maxComplaintTimeDays).toBe(0);
        });
    });

    describe('toggleRequestMoreFeedback', () => {
        it('should enable request more feedback with default value', () => {
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.toggleRequestMoreFeedback();

            expect(comp.requestMoreFeedbackToggled()).toBe(true);
            expect(emitSpy).toHaveBeenCalled();
            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.maxRequestMoreFeedbackTimeDays).toBe(7);
        });

        it('should disable request more feedback by resetting to 0', () => {
            // First enable
            comp.toggleRequestMoreFeedback();
            expect(comp.requestMoreFeedbackToggled()).toBe(true);

            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');
            // Then disable
            comp.toggleRequestMoreFeedback();

            expect(comp.requestMoreFeedbackToggled()).toBe(false);
            expect(emitSpy).toHaveBeenCalled();
            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.maxRequestMoreFeedbackTimeDays).toBe(0);
        });
    });

    describe('max points input', () => {
        const maxPointsInput = () => fixture.nativeElement.querySelector('#onboarding-maxPoints') as HTMLInputElement;
        const errorTranslationKeys = () => Array.from(fixture.nativeElement.querySelectorAll('.alert-danger')).map((alert) => (alert as HTMLElement).getAttribute('jhiTranslate'));
        // the error messages are shown while the typed value violates a bound, before the field is left and clamps it
        const typeWithoutLeaving = (text: string) => {
            const input = maxPointsInput();
            input.focus();
            input.value = text;
            input.dispatchEvent(new Event('input', { bubbles: true }));
            fixture.detectChanges();
        };

        it('should emit the typed max points without an error message', () => {
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            typeIntoInputNumber(maxPointsInput(), '120');
            fixture.detectChanges();

            expect(emitSpy.mock.lastCall![0].maxPoints).toBe(120);
            expect(errorTranslationKeys()).toEqual([]);
        });

        it('should show the minimum error message while the value is below 1', () => {
            typeWithoutLeaving('0');

            expect(errorTranslationKeys()).toEqual(['artemisApp.course.maxPoints.min']);
        });

        it('should show the maximum error message while the value exceeds the limit', () => {
            typeWithoutLeaving(String(MAX_GRADING_POINTS + 1));

            expect(errorTranslationKeys()).toEqual(['artemisApp.course.maxPoints.max']);
        });

        it('should show the whole number error message for a fractional value', () => {
            typeIntoInputNumber(maxPointsInput(), '10.5');
            fixture.detectChanges();

            expect(errorTranslationKeys()).toEqual(['artemisApp.course.maxPoints.wholeNumber']);
        });
    });

    describe('updateField', () => {
        it('should emit courseUpdated', () => {
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.updateField('maxComplaints', 5);

            expect(emitSpy).toHaveBeenCalled();
            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.maxComplaints).toBe(5);
        });
    });
});
