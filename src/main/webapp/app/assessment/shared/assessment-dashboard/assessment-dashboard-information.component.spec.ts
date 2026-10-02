import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/course/shared/entities/course.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideRouter } from '@angular/router';
import {
    AssessmentDashboardInformationComponent,
    AssessmentDashboardInformationEntry,
} from 'app/assessment/shared/assessment-dashboard/assessment-dashboard-information.component';

describe('AssessmentDashboardInformationComponent', () => {
    let component: AssessmentDashboardInformationComponent;
    let fixture: ComponentFixture<AssessmentDashboardInformationComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideRouter([])],
        }).compileComponents();

        fixture = TestBed.createComponent(AssessmentDashboardInformationComponent);
        component = fixture.componentInstance;

        // Set all required inputs
        fixture.componentRef.setInput('course', { id: 10 } as Course);
        fixture.componentRef.setInput('isExamMode', false);
        fixture.componentRef.setInput('tutorId', 1);
        fixture.componentRef.setInput('complaintsEnabled', true);
        fixture.componentRef.setInput('feedbackRequestEnabled', true);
        fixture.componentRef.setInput('numberOfCorrectionRounds', 1);
        fixture.componentRef.setInput('numberOfAssessmentsOfCorrectionRounds', [new DueDateStat()]);
        fixture.componentRef.setInput('totalNumberOfAssessments', 0);
        fixture.componentRef.setInput('numberOfSubmissions', new DueDateStat());
        fixture.componentRef.setInput('numberOfTutorAssessments', 0);
        fixture.componentRef.setInput('complaints', new AssessmentDashboardInformationEntry(0, 0, undefined));
        fixture.componentRef.setInput('moreFeedbackRequests', new AssessmentDashboardInformationEntry(0, 0, undefined));
        fixture.componentRef.setInput('assessmentLocks', new AssessmentDashboardInformationEntry(0, 0, undefined));
        fixture.componentRef.setInput('ratings', new AssessmentDashboardInformationEntry(0, 0, undefined));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should calculate the summary progress correctly', () => {
        const submissions = new DueDateStat();
        submissions.inTime = 400;
        submissions.late = 350;

        fixture.componentRef.setInput('totalNumberOfAssessments', 150);
        fixture.componentRef.setInput('numberOfSubmissions', submissions);
        fixture.componentRef.setInput('numberOfCorrectionRounds', 1);
        fixture.componentRef.setInput('assessmentLocks', new AssessmentDashboardInformationEntry(50, 10, undefined));

        expect(component.assessedSubmissions()).toBe(150);
        expect(component.inProgressSubmissions()).toBe(50);
        expect(component.openSubmissions()).toBe(550);
    });

    it('should calculate exam progress from all correction rounds', () => {
        const submissions = new DueDateStat();
        submissions.inTime = 4;
        const firstCorrectionRound = new DueDateStat();
        firstCorrectionRound.inTime = 3;
        const secondCorrectionRound = new DueDateStat();
        secondCorrectionRound.inTime = 2;

        fixture.componentRef.setInput('isExamMode', true);
        fixture.componentRef.setInput('numberOfCorrectionRounds', 2);
        fixture.componentRef.setInput('numberOfAssessmentsOfCorrectionRounds', [firstCorrectionRound, secondCorrectionRound]);
        fixture.componentRef.setInput('totalNumberOfAssessments', 5);
        fixture.componentRef.setInput('numberOfSubmissions', submissions);
        fixture.componentRef.setInput('assessmentLocks', new AssessmentDashboardInformationEntry(1, 0, undefined));

        expect(component.totalProgressItems()).toBe(8);
        expect(component.assessedSubmissions()).toBe(5);
        expect(component.inProgressSubmissions()).toBe(1);
        expect(component.openSubmissions()).toBe(2);
        expect(component.assessedPercentage()).toBe(62);
        expect(component.inProgressPercentage()).toBe(12);
    });

    it('should floor progress percentages to whole numbers', () => {
        const submissions = new DueDateStat();
        submissions.inTime = 30;

        fixture.componentRef.setInput('totalNumberOfAssessments', 11);
        fixture.componentRef.setInput('numberOfSubmissions', submissions);

        expect(component.assessedPercentage()).toBe(36);
    });

    it('should not render correction-round statistics when no correction rounds exist', () => {
        fixture.componentRef.setInput('isExamMode', true);
        fixture.componentRef.setInput('numberOfCorrectionRounds', 0);
        fixture.componentRef.setInput('numberOfAssessmentsOfCorrectionRounds', []);

        expect(() => fixture.detectChanges()).not.toThrow();
        expect(fixture.nativeElement.textContent).not.toContain('Assessments for correction rounds');
    });

    it('should set up links correctly', () => {
        fixture.componentRef.setInput('isExamMode', false);
        fixture.componentRef.setInput('examId', 42);

        expect(component.complaintsLink()).toEqual(['/course-management', 10, 'complaints']);
        expect(component.moreFeedbackRequestsLink()).toEqual(['/course-management', 10, 'more-feedback-requests']);
        expect(component.assessmentLocksLink()).toEqual(['/course-management', 10, 'assessment-locks']);
        expect(component.ratingsLink()).toEqual(['/course-management', 10, 'ratings']);

        fixture.componentRef.setInput('isExamMode', true);

        expect(component.complaintsLink()).toEqual(['/course-management', 10, 'exams', 42, 'complaints']);
        expect(component.moreFeedbackRequestsLink()).toEqual(['/course-management', 10, 'exams', 42, 'more-feedback-requests']);
        expect(component.assessmentLocksLink()).toEqual(['/course-management', 10, 'exams', 42, 'assessment-locks']);
    });

    it('should compute the right total/missing ratio', () => {
        const complaints = new AssessmentDashboardInformationEntry(0, 10, undefined);

        expect(complaints.doneToTotalPercentage).toBe('');

        complaints.done = 2;

        expect(complaints.doneToTotalPercentage).toBe('100%');

        complaints.total = 3;

        expect(complaints.doneToTotalPercentage).toBe('67%');
    });
});
