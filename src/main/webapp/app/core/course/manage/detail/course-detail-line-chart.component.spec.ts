import { of } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseDetailLineChartComponent, SwitchTimeSpanDirection } from 'app/core/course/manage/detail/course-detail-line-chart.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';

class MockCourseManagementService {
    getStatisticsData = jest.fn().mockReturnValue(of([]));
    getStatisticsForLifetimeOverview = jest.fn().mockReturnValue(of([]));
}

describe('CourseDetailLineChartComponent', () => {
    let fixture: ComponentFixture<CourseDetailLineChartComponent>;
    let component: CourseDetailLineChartComponent;
    let service: MockCourseManagementService;

    const graphData: number[] = Array.from({ length: 17 }, (_, i) => 40 + 2 * i);
    const initialStats = [26, 46, 34, 12, 26, 46, 34, 12, 26, 46, 34, 12, 26, 46, 34, 12, 42];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseDetailLineChartComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseDetailLineChartComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(CourseManagementService) as unknown as MockCourseManagementService;

        component.course = { id: 1 } as any;
        component.numberOfStudentsInCourse = 50;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize (period overview) and react to switching spans', () => {
        const statsSpy = jest.spyOn(service, 'getStatisticsData').mockReturnValue(of(graphData.slice(0, 8)));

        component.ngOnChanges();

        expect(statsSpy).toHaveBeenCalledWith(1, 0, component.displayedNumberOfWeeks);
        expect(component.data[0].series).toHaveLength(component.displayedNumberOfWeeks);

        for (let i = 0; i < component.displayedNumberOfWeeks; i++) {
            expect(component.absoluteSeries[i]['absoluteValue']).toBe(graphData[i]);
        }

        component.switchTimeSpan(SwitchTimeSpanDirection.RIGHT);
        expect(statsSpy).toHaveBeenLastCalledWith(1, 1, component.displayedNumberOfWeeks);

        // with students = 0, values should be zeroed regardless of service data
        component.numberOfStudentsInCourse = 0;
        component.switchTimeSpan(SwitchTimeSpanDirection.RIGHT);
        expect(statsSpy).toHaveBeenLastCalledWith(1, 2, component.displayedNumberOfWeeks);
        for (let i = 0; i < component.displayedNumberOfWeeks; i++) {
            expect(component.data[0].series[i].value).toBe(0);
            expect(component.absoluteSeries[i]['absoluteValue']).toBe(0);
        }
    });

    it('should show only 2 weeks if start date is 1 week ago', () => {
        // 1 week ago -> chart covers 2 weeks (inclusive counting)
        component.course = { id: 1, startDate: dayjs().subtract(1, 'week') } as any;

        // Return 2 weeks of raw values (12, 42) => with 50 students, 24% and 84%
        jest.spyOn(service, 'getStatisticsData').mockReturnValue(of([12, 42]));

        component.ngOnChanges();

        expect(component.data[0].series).toHaveLength(2);
        expect(component.data[0].series[0].value).toBe(24);
        expect(component.data[0].series[1].value).toBe(84);

        // absolute values preserved for tooltip
        expect(component.absoluteSeries[0].absoluteValue).toBe(12);
        expect(component.absoluteSeries[1].absoluteValue).toBe(42);
    });

    it('should adapt labels if end date is passed (last label equals course end ISO week)', () => {
        const endDate = dayjs().subtract(1, 'week');
        component.course = { id: 1, endDate } as any;

        jest.spyOn(service, 'getStatisticsData').mockReturnValue(of(graphData.slice(0, 8)));

        component.ngOnChanges();

        const last = component.data[0].series[component.data[0].series.length - 1];
        expect(last.name).toBe(endDate.isoWeek().toString());
    });

    it('should adapt if course phase is smaller than 4 weeks', () => {
        const endDate = dayjs().subtract(1, 'week');
        const startDate = dayjs().subtract(2, 'weeks');
        component.course = { id: 1, startDate, endDate } as any;

        // Lifetime overview is shown by default only if startDate exists AND endDate is in the past relative to now;
        // here that’s true -> lifetime overview path
        jest.spyOn(service, 'getStatisticsForLifetimeOverview').mockReturnValue(of([12, 42]));

        component.numberOfStudentsInCourse = 50;
        component.ngOnChanges();

        expect(component.showLifetimeOverview).toBeTrue();
        expect(component.startDateDisplayed).toBeTrue();
        expect(component.showsCurrentWeek).toBeTrue();

        // 2 weeks span, values as percentages
        expect(component.data[0].series).toHaveLength(2);
        expect(component.data[0].series[0].value).toBe(24);
        expect(component.data[0].series[1].value).toBe(84);

        // last label corresponds to the end date ISO week
        expect(component.data[0].series[1].name).toBe(endDate.isoWeek().toString());
    });

    it('should limit the next view if start date is reached when paging back', () => {
        const startDate = dayjs().subtract(component.displayedNumberOfWeeks, 'weeks');
        component.course = { id: 42, startDate } as any;

        const getStatisticsDataMock = jest.spyOn(service, 'getStatisticsData').mockReturnValue(of(initialStats.slice(0, 8)));

        component.ngOnChanges();

        expect(component.data[0].series).toHaveLength(Math.min(8, component.displayedNumberOfWeeks));

        component.switchTimeSpan(SwitchTimeSpanDirection.LEFT);

        expect(component.data[0].series).toHaveLength(1);
        expect(component.data[0].series[0].name).toBe(startDate.isoWeek().toString());

        expect(getStatisticsDataMock).toHaveBeenCalledWith(42, -1, 8);
        expect(getStatisticsDataMock).toHaveBeenCalledTimes(2);
    });

    it('should create lifetime overview and cache data', () => {
        const startDate = dayjs().subtract(17, 'weeks');
        component.course = { id: 42, startDate } as any;

        const overviewSpy = jest.spyOn(service, 'getStatisticsForLifetimeOverview').mockReturnValue(of(initialStats));

        component.displayLifetimeOverview();

        expect(component.showLifetimeOverview).toBeTrue();
        expect(overviewSpy).toHaveBeenCalledWith(42);

        for (let i = 0; i < initialStats.length; i++) {
            expect(component.absoluteSeries[i]['absoluteValue']).toBe(initialStats[i]);
        }

        overviewSpy.mockClear();
        component.displayLifetimeOverview();
        expect(overviewSpy).not.toHaveBeenCalled();
    });

    it('should create an empty chart if no students are registered yet', () => {
        const startDate = dayjs().subtract(4, 'weeks');
        const endDate = startDate.add(32, 'weeks');
        component.course = { id: 42, startDate, endDate } as any;
        component.numberOfStudentsInCourse = 0;

        jest.spyOn(service, 'getStatisticsData').mockReturnValue(of(initialStats.slice(0, 5)));

        component.ngOnChanges();

        expect(component.data[0].series).toHaveLength(5);
        for (let week = 0; week < 5; week++) {
            expect(component.data[0].series[week].value).toBe(0);
        }
    });
});
