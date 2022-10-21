import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { PieChartModule } from '@swimlane/ngx-charts';
import {
    AssessmentDashboardInformationComponent,
    AssessmentDashboardInformationEntry,
} from 'app/course/dashboards/assessment-dashboard/assessment-dashboard-information.component';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import { Course } from 'app/entities/course.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';

describe('AssessmentDashboardInformationComponent', () => {
    let component: AssessmentDashboardInformationComponent;
    let fixture: ComponentFixture<AssessmentDashboardInformationComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(PieChartModule)],
            declarations: [AssessmentDashboardInformationComponent, MockPipe(ArtemisTranslatePipe), MockComponent(SidePanelComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(AssessmentDashboardInformationComponent);
        component = fixture.componentInstance;
        component.course = { id: 10 } as Course;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should display open and closed assessments correctly', () => {
        const totalAssessments = new DueDateStat();
        totalAssessments.inTime = 150;
        totalAssessments.late = 460;

        const submissions = new DueDateStat();
        submissions.inTime = 400;
        submissions.late = 350;

        component.totalNumberOfAssessments = totalAssessments;
        component.numberOfSubmissions = submissions;
        component.numberOfCorrectionRounds = 1;
        const setupSpy = jest.spyOn(component, 'setup');
        const setupLinksSpy = jest.spyOn(component, 'setupLinks');
        const setupGraphSpy = jest.spyOn(component, 'setupGraph');

        component.ngOnInit();

        expect(setupSpy).toHaveBeenCalledOnce();
        expect(setupLinksSpy).toHaveBeenCalledOnce();
        expect(setupGraphSpy).toHaveBeenCalledOnce();

        expect(component.customColors[0].name).toBe('artemisApp.exerciseAssessmentDashboard.openAssessments');
        expect(component.customColors[1].name).toBe('artemisApp.exerciseAssessmentDashboard.closedAssessments');
        expect(component.assessments[0].value).toBe(140);
        expect(component.assessments[1].value).toBe(610);
    });

    it('should set up links correctly', () => {
        component.isExamMode = false;
        component.examId = 42;

        jest.spyOn(component, 'setupGraph').mockImplementation();

        component.ngOnInit();

        expect(component.complaintsLink).toEqual(['/course-management', 10, 'complaints']);
        expect(component.moreFeedbackRequestsLink).toEqual(['/course-management', 10, 'more-feedback-requests']);
        expect(component.assessmentLocksLink).toEqual(['/course-management', 10, 'assessment-locks']);
        expect(component.ratingsLink).toEqual(['/course-management', 10, 'ratings']);

        component.isExamMode = true;

        component.ngOnChanges();

        expect(component.complaintsLink).toEqual(['/course-management', 10, 'exams', 42, 'complaints']);
        expect(component.moreFeedbackRequestsLink).toEqual(['/course-management', 10, 'exams', 42, 'more-feedback-requests']);
        expect(component.assessmentLocksLink).toEqual(['/course-management', 10, 'exams', 42, 'assessment-locks']);
    });

    it('should handle language changes', () => {
        const translateService = fixture.debugElement.injector.get(TranslateService);
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
