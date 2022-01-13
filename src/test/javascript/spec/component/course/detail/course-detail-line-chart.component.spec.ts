import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseDetailLineChartComponent } from 'app/course/manage/detail/course-detail-line-chart.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { NgxChartsModule } from '@swimlane/ngx-charts';
import { MockCourseManagementService } from '../../../helpers/mocks/service/mock-course-management.service';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';

describe('CourseDetailLineChartComponent', () => {
    let fixture: ComponentFixture<CourseDetailLineChartComponent>;
    let component: CourseDetailLineChartComponent;
    let service: CourseManagementService;

    const initialStats = [26, 46, 34, 12, 26, 46, 34, 12, 26, 46, 34, 12, 26, 46, 34, 12, 42];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(NgxChartsModule)],
            declarations: [CourseDetailLineChartComponent, MockPipe(ArtemisTranslatePipe), MockComponent(HelpIconComponent)],
            providers: [MockCourseManagementService, { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseDetailLineChartComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(CourseManagementService);
            });
    });

    beforeEach(() => {
        component.course = { id: 1 };
        component.numberOfStudentsInCourse = 50;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        const graphData: number[] = [];
        const spy = jest.spyOn(service, 'getStatisticsData');
        for (let i = 0; i < 17; i++) {
            graphData[i] = 40 + 2 * i;
        }
        spy.mockReturnValue(of(graphData));

        component.initialStats = initialStats;

        component.ngOnChanges();

        expect(component.absoluteSeries).toHaveLength(initialStats.length);
        for (let i = 0; i < 17; i++) {
            expect(component.absoluteSeries[i]['absoluteValue']).toBe(initialStats[i]);
        }

        component.switchTimeSpan(true);

        expect(component.data[0].series).toHaveLength(initialStats.length);
        for (let i = 0; i < 17; i++) {
            expect(component.absoluteSeries[i]['absoluteValue']).toBe(graphData[i]);
        }

        component.numberOfStudentsInCourse = 0;
        component.switchTimeSpan(true);

        expect(component.data[0].series).toHaveLength(initialStats.length);
        for (let i = 0; i < 17; i++) {
            expect(component.absoluteSeries[i]['absoluteValue']).toBe(0);
        }
    });
});
