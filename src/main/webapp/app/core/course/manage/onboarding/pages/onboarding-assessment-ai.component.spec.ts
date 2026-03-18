import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { MockDirective } from 'ng-mocks';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { RouterModule } from '@angular/router';

import { OnboardingAssessmentAiComponent } from './onboarding-assessment-ai.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('OnboardingAssessmentAiComponent', () => {
    setupTestBed({ zoneless: true });

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
