import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { CourseManagementOverviewStatisticsComponent } from 'app/course/manage/overview/course-management-overview-statistics.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LineChartModule } from '@swimlane/ngx-charts';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ArtemisTestModule } from '../../test.module';

describe('CourseManagementOverviewStatisticsComponent', () => {
    let fixture: ComponentFixture<CourseManagementOverviewStatisticsComponent>;
    let component: CourseManagementOverviewStatisticsComponent;

    const amountOfStudentsInCourse = 25;
    const initialStats = [0, 11, 9, 23];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(LineChartModule), ArtemisTestModule],
            declarations: [
                CourseManagementOverviewStatisticsComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockDirective(TranslateDirective),
                MockComponent(HelpIconComponent),
            ],
            providers: [MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseManagementOverviewStatisticsComponent);
                component = fixture.componentInstance;
            });
    });

    it('should initialize component and load values', () => {
        // Provide the @Input data
        component.amountOfStudentsInCourse = amountOfStudentsInCourse;
        component.initialStats = initialStats;

        component.ngOnInit();
        component.ngOnChanges();

        expect(component.ngxData).toHaveLength(1);
        expect(component.ngxData[0].name).toBe('active students');
        expect(component.ngxData[0].series[0].value).toBe(0);
        expect(component.ngxData[0].series[1].value).toBe(44);
        expect(component.ngxData[0].series[2].value).toBe(36);
        expect(component.ngxData[0].series[3].value).toBe(92);
    });

    it('should react to changes', () => {
        component.ngOnInit();

        component.initialStats = [];
        component.amountOfStudentsInCourse = 0;

        component.ngOnChanges();

        expect(component.loading).toBe(false);
        expect(component.ngxData[0].series[0].value).toBe(0);
        expect(component.ngxData[0].series[1].value).toBe(0);
        expect(component.ngxData[0].series[2].value).toBe(0);
        expect(component.ngxData[0].series[3].value).toBe(0);
    });
});
