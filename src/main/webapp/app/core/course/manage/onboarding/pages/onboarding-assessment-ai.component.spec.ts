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

    describe('complaintsEnabled', () => {
        it('should return false when maxComplaintTimeDays is 0', () => {
            course.maxComplaintTimeDays = 0;
            expect(comp.complaintsEnabled).toBe(false);
        });

        it('should return true when maxComplaintTimeDays is greater than 0', () => {
            course.maxComplaintTimeDays = 7;
            expect(comp.complaintsEnabled).toBe(true);
        });

        it('should return false when maxComplaintTimeDays is undefined', () => {
            course.maxComplaintTimeDays = undefined;
            expect(comp.complaintsEnabled).toBe(false);
        });
    });

    describe('requestMoreFeedbackEnabled', () => {
        it('should return false when maxRequestMoreFeedbackTimeDays is 0', () => {
            course.maxRequestMoreFeedbackTimeDays = 0;
            expect(comp.requestMoreFeedbackEnabled).toBe(false);
        });

        it('should return true when maxRequestMoreFeedbackTimeDays is greater than 0', () => {
            course.maxRequestMoreFeedbackTimeDays = 7;
            expect(comp.requestMoreFeedbackEnabled).toBe(true);
        });

        it('should return false when maxRequestMoreFeedbackTimeDays is undefined', () => {
            course.maxRequestMoreFeedbackTimeDays = undefined;
            expect(comp.requestMoreFeedbackEnabled).toBe(false);
        });
    });

    describe('toggleComplaints', () => {
        it('should enable complaints with default values', () => {
            course.maxComplaintTimeDays = 0;
            fixture.componentRef.setInput('course', { ...course });
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.toggleComplaints();

            expect(emitSpy).toHaveBeenCalled();
            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.maxComplaints).toBe(3);
            expect(emitted.maxTeamComplaints).toBe(3);
            expect(emitted.maxComplaintTimeDays).toBe(7);
            expect(emitted.maxComplaintTextLimit).toBe(2000);
            expect(emitted.maxComplaintResponseTextLimit).toBe(2000);
        });

        it('should disable complaints by resetting values to 0', () => {
            course.maxComplaints = 3;
            course.maxTeamComplaints = 3;
            course.maxComplaintTimeDays = 7;
            fixture.componentRef.setInput('course', { ...course });
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.toggleComplaints();

            expect(emitSpy).toHaveBeenCalled();
            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.maxComplaints).toBe(0);
            expect(emitted.maxTeamComplaints).toBe(0);
            expect(emitted.maxComplaintTimeDays).toBe(0);
        });
    });

    describe('toggleRequestMoreFeedback', () => {
        it('should enable request more feedback with default value', () => {
            course.maxRequestMoreFeedbackTimeDays = 0;
            fixture.componentRef.setInput('course', { ...course });
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.toggleRequestMoreFeedback();

            expect(emitSpy).toHaveBeenCalled();
            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.maxRequestMoreFeedbackTimeDays).toBe(7);
        });

        it('should disable request more feedback by resetting to 0', () => {
            course.maxRequestMoreFeedbackTimeDays = 7;
            fixture.componentRef.setInput('course', { ...course });
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.toggleRequestMoreFeedback();

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
