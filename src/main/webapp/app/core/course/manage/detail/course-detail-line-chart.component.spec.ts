import { of } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseDetailLineChartComponent, SwitchTimeSpanDirection } from 'app/core/course/manage/detail/course-detail-line-chart.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClient } from '@angular/common/http';

describe('CourseDetailLineChartComponent', () => {
    let fixture: ComponentFixture<CourseDetailLineChartComponent>;
    let component: CourseDetailLineChartComponent;
    let service: CourseManagementService;

    const initialStats = [26, 46, 34, 12, 26, 46, 34, 12, 26, 46, 34, 12, 26, 46, 34, 12, 42];

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                provideHttpClient(),
            ],
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

        expect(component.absoluteSeries).toHaveLength(Math.min(component.initialStats.length, component.displayedNumberOfWeeks));
        for (let i = 0; i < Math.min(component.initialStats.length, component.displayedNumberOfWeeks); i++) {
            expect(component.absoluteSeries[i]['absoluteValue']).toBe(initialStats[initialStats.length - component.absoluteSeries.length + i]);
        }

        component.switchTimeSpan(SwitchTimeSpanDirection.RIGHT);

        expect(component.data[0].series).toHaveLength(Math.min(component.initialStats.length, component.displayedNumberOfWeeks));
        for (let i = 0; i < Math.min(component.initialStats.length, component.displayedNumberOfWeeks); i++) {
            expect(component.absoluteSeries[i]['absoluteValue']).toBe(graphData[i]);
        }

        component.numberOfStudentsInCourse = 0;
        component.switchTimeSpan(SwitchTimeSpanDirection.RIGHT);

        expect(component.data[0].series).toHaveLength(Math.min(component.initialStats.length, component.displayedNumberOfWeeks));
        for (let i = 0; i < Math.min(component.initialStats.length, component.displayedNumberOfWeeks); i++) {
            expect(component.absoluteSeries[i]['absoluteValue']).toBe(0);
        }
    });

    it('should show lettering if course did not start yet', () => {
        component.course = { startDate: dayjs().add(1, 'week') };
        component.initialStats = [];

        component.ngOnChanges();

        expect(component.startDateAlreadyPassed).toBeFalse();
    });

    it('should show only 2 weeks if start date is 1 week ago', () => {
        component.course = { startDate: dayjs().subtract(1, 'week') };
        component.initialStats = initialStats.slice(15);

        component.ngOnChanges();

        expect(component.data[0].series).toHaveLength(2);
        expect(component.data[0].series[0].value).toBe(24);
        expect(component.data[0].series[1].value).toBe(84);
    });

    it('should adapt labels if end date is passed', () => {
        const endDate = dayjs().subtract(1, 'week');
        component.course = { endDate };
        component.initialStats = initialStats;

        component.ngOnChanges();

        expect(component.data[0].series[Math.min(component.initialStats.length, component.displayedNumberOfWeeks) - 1].name).toBe(endDate.isoWeek().toString());
    });

    it('should adapt if course phase is smaller than 4 weeks', () => {
        const endDate = dayjs().subtract(1, 'weeks');
        component.course = { startDate: dayjs().subtract(2, 'weeks'), endDate };
        component.overviewStats = initialStats.slice(15);

        component.ngOnChanges();

        expect(component.showLifetimeOverview).toBeTrue();
        expect(component.startDateDisplayed).toBeTrue();
        expect(component.showsCurrentWeek).toBeTrue();

        expect(component.data[0].series).toHaveLength(2);
        expect(component.data[0].series[0].value).toBe(24);
        expect(component.data[0].series[1].value).toBe(84);
        expect(component.data[0].series[1].name).toBe(endDate.isoWeek().toString());
    });

    it('should limit the next view if start date is reached', () => {
        const getStatisticsDataMock = jest.spyOn(service, 'getStatisticsData').mockReturnValue(of(initialStats.slice(16)));
        const startDate = dayjs().subtract(component.displayedNumberOfWeeks, 'weeks');
        component.course = { id: 42, startDate };
        component.initialStats = initialStats;

        component.ngOnChanges();

        expect(component.data[0].series).toHaveLength(Math.min(component.initialStats.length, component.displayedNumberOfWeeks));

        // we switch back in time
        component.switchTimeSpan(SwitchTimeSpanDirection.LEFT);

        expect(component.data[0].series).toHaveLength(1);
        expect(component.data[0].series[0].value).toBe(84);
        expect(component.data[0].series[0].name).toBe(startDate.isoWeek().toString());
        expect(getStatisticsDataMock).toHaveBeenCalledOnce();
        expect(getStatisticsDataMock).toHaveBeenCalledWith(42, -1, 8);
    });

    it('should create lifetime overview', () => {
        const getOverviewDataMock = jest.spyOn(service, 'getStatisticsForLifetimeOverview').mockReturnValue(of(initialStats));
        const startDate = dayjs().subtract(17, 'weeks');
        component.course = { id: 42, startDate };

        component.displayLifetimeOverview();

        expect(component.showLifetimeOverview).toBeTrue();
        expect(getOverviewDataMock).toHaveBeenCalledOnce();
        expect(getOverviewDataMock).toHaveBeenCalledWith(42);
        for (let i = 0; i < 17; i++) {
            expect(component.absoluteSeries[i]['absoluteValue']).toBe(initialStats[i]);
        }
    });

    it('should create an empty chart if no students are registered yet', () => {
        const startDate = dayjs().subtract(4, 'weeks');
        const endDate = startDate.add(32, 'weeks');
        component.course = { id: 42, startDate, endDate };
        component.numberOfStudentsInCourse = 0;
        component.initialStats = initialStats;

        component.ngOnChanges();

        expect(component.data[0].series).toHaveLength(5);
        for (let week = 0; week < 5; week++) {
            expect(component.data[0].series[week].value).toBe(0);
        }
    });
});
