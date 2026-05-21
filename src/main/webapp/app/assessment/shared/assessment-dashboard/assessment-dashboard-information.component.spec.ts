import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import {
    AssessmentDashboardInformationComponent,
    AssessmentDashboardInformationEntry,
} from 'app/assessment/shared/assessment-dashboard/assessment-dashboard-information.component';

describe('AssessmentDashboardInformationComponent', () => {
    setupTestBed({ zoneless: true });
    let component: AssessmentDashboardInformationComponent;
    let fixture: ComponentFixture<AssessmentDashboardInformationComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
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
        fixture.componentRef.setInput('totalAssessmentPercentage', 0);
        fixture.componentRef.setInput('complaints', new AssessmentDashboardInformationEntry(0, 0, undefined));
        fixture.componentRef.setInput('moreFeedbackRequests', new AssessmentDashboardInformationEntry(0, 0, undefined));
        fixture.componentRef.setInput('assessmentLocks', new AssessmentDashboardInformationEntry(0, 0, undefined));
        fixture.componentRef.setInput('ratings', new AssessmentDashboardInformationEntry(0, 0, undefined));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should display open and closed assessments correctly', () => {
        const submissions = new DueDateStat();
        submissions.inTime = 400;
        submissions.late = 350;

        fixture.componentRef.setInput('totalNumberOfAssessments', 150);
        fixture.componentRef.setInput('numberOfSubmissions', submissions);
        fixture.componentRef.setInput('numberOfCorrectionRounds', 1);
        const setupSpy = vi.spyOn(component, 'setup');
        const setupLinksSpy = vi.spyOn(component, 'setupLinks');
        const setupGraphSpy = vi.spyOn(component, 'setupGraph');

        component.ngOnInit();

        expect(setupSpy).toHaveBeenCalledTimes(1);
        expect(setupLinksSpy).toHaveBeenCalledTimes(1);
        expect(setupGraphSpy).toHaveBeenCalledTimes(1);

        expect(component.customColors[0].name).toBe('artemisApp.exerciseAssessmentDashboard.openAssessments');
        expect(component.customColors[1].name).toBe('artemisApp.exerciseAssessmentDashboard.closedAssessments');
        expect(component.assessments[0].value).toBe(600);
        expect(component.assessments[1].value).toBe(150);
    });

    it('should set up links correctly', () => {
        fixture.componentRef.setInput('isExamMode', false);
        fixture.componentRef.setInput('examId', 42);

        vi.spyOn(component, 'setupGraph').mockImplementation(() => {});

        component.ngOnInit();

        expect(component.complaintsLink).toEqual(['/course-management', 10, 'complaints']);
        expect(component.moreFeedbackRequestsLink).toEqual(['/course-management', 10, 'more-feedback-requests']);
        expect(component.assessmentLocksLink).toEqual(['/course-management', 10, 'assessment-locks']);
        expect(component.ratingsLink).toEqual(['/course-management', 10, 'ratings']);

        fixture.componentRef.setInput('isExamMode', true);

        component.ngOnChanges();

        expect(component.complaintsLink).toEqual(['/course-management', 10, 'exams', 42, 'complaints']);
        expect(component.moreFeedbackRequestsLink).toEqual(['/course-management', 10, 'exams', 42, 'more-feedback-requests']);
        expect(component.assessmentLocksLink).toEqual(['/course-management', 10, 'exams', 42, 'assessment-locks']);
    });

    it('should handle language changes', () => {
        const translateService = TestBed.inject(TranslateService);
        const setupGraphStub = vi.spyOn(component, 'setupGraph').mockImplementation(() => {});
        component.ngOnInit();
        translateService.use('de');

        expect(setupGraphStub).toHaveBeenCalledTimes(2);
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
