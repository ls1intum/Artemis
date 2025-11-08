import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import {
    AssessmentDashboardInformationComponent,
    AssessmentDashboardInformationEntry,
} from 'app/assessment/shared/assessment-dashboard/assessment-dashboard-information.component';

describe('AssessmentDashboardInformationComponent', () => {
    let component: AssessmentDashboardInformationComponent;
    let fixture: ComponentFixture<AssessmentDashboardInformationComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(AssessmentDashboardInformationComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('course', { id: 10 } as Course);
        fixture.componentRef.setInput('isExamMode', false);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should display open and closed assessments correctly', () => {
        const submissions = new DueDateStat();
        submissions.inTime = 400;
        submissions.late = 350;

        fixture.componentRef.setInput('totalNumberOfAssessments', 150);
        fixture.componentRef.setInput('numberOfSubmissions', submissions);
        fixture.componentRef.setInput('numberOfCorrectionRounds', 1);
        const setupSpy = jest.spyOn(component, 'setup');
        const setupLinksSpy = jest.spyOn(component, 'setupLinks');
        const setupGraphSpy = jest.spyOn(component, 'setupGraph');

        component.ngOnInit();

        expect(setupSpy).toHaveBeenCalledOnce();
        expect(setupLinksSpy).toHaveBeenCalledOnce();
        expect(setupGraphSpy).toHaveBeenCalledOnce();

        expect(component.customColors[0].name).toBe('artemisApp.exerciseAssessmentDashboard.openAssessments');
        expect(component.customColors[1].name).toBe('artemisApp.exerciseAssessmentDashboard.closedAssessments');
        expect(component.assessments[0].value).toBe(600);
        expect(component.assessments[1].value).toBe(150);
    });

    it('should set up links correctly', () => {
        fixture.componentRef.setInput('isExamMode', false);
        fixture.componentRef.setInput('examId', 42);

        jest.spyOn(component, 'setupGraph').mockImplementation();

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
        const setupGraphStub = jest.spyOn(component, 'setupGraph').mockImplementation();
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
