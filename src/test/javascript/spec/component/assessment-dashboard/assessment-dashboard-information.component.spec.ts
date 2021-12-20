import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { AssessmentDashboardInformationComponent } from 'app/course/dashboards/assessment-dashboard/assessment-dashboard-information.component';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import { PieChartModule } from '@swimlane/ngx-charts';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';

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
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should display open and closed assessments correctly', () => {
        const totalAssessments = {
            inTime: 150,
            late: 460,
        } as DueDateStat;

        const submissions = {
            inTime: 400,
            late: 350,
        } as DueDateStat;

        component.totalNumberOfAssessments = totalAssessments;
        component.numberOfSubmissions = submissions;
        const setupSpy = jest.spyOn(component, 'setup');
        const setupLinksSpy = jest.spyOn(component, 'setupLinks');

        component.ngOnChanges();

        expect(setupSpy).toHaveBeenCalledTimes(1);
        expect(setupLinksSpy).toHaveBeenCalledTimes(1);

        console.log(component.assessments);
        console.log(component.totalNumberOfAssessments);
        console.log(component.numberOfSubmissions.total);
        expect(component.assessments[0].value).toBe(140);
        expect(component.assessments[1].value).toBe(610);
    });
});
