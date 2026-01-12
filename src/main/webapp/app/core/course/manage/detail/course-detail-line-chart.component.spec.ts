import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseDetailLineChartComponent, SwitchTimeSpanDirection } from 'app/core/course/manage/detail/course-detail-line-chart.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ComponentRef } from '@angular/core';
import { provideNoopAnimationsForTests } from 'test/helpers/animations';

class MockCourseManagementService {
    getStatisticsData = vi.fn().mockReturnValue(of([]));
    getStatisticsForLifetimeOverview = vi.fn().mockReturnValue(of([]));
}

describe('CourseDetailLineChartComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CourseDetailLineChartComponent>;
    let component: CourseDetailLineChartComponent;
    let componentRef: ComponentRef<CourseDetailLineChartComponent>;
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
                provideNoopAnimationsForTests(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseDetailLineChartComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
        service = TestBed.inject(CourseManagementService) as unknown as MockCourseManagementService;

        componentRef.setInput('course', { id: 1 });
        componentRef.setInput('numberOfStudentsInCourse', 50);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize (period overview) and react to switching spans', () => {
        const statsSpy = vi.spyOn(service, 'getStatisticsData').mockReturnValue(of(graphData.slice(0, 8)));

        fixture.detectChanges();

        expect(statsSpy).toHaveBeenCalledWith(1, 0, component.displayedNumberOfWeeks());
        expect(component.data()[0].series).toHaveLength(component.displayedNumberOfWeeks());

        for (let i = 0; i < component.displayedNumberOfWeeks(); i++) {
            expect(component.absoluteSeries[i]['absoluteValue']).toBe(graphData[i]);
        }

        component.switchTimeSpan(SwitchTimeSpanDirection.RIGHT);
        expect(statsSpy).toHaveBeenLastCalledWith(1, 1, component.displayedNumberOfWeeks());

        // with students = 0, values should be zeroed regardless of service data
        // Note: changing numberOfStudentsInCourse triggers the component's effect which resets currentPeriod to 0,
        // so the next switchTimeSpan(RIGHT) will result in currentPeriod = 1, not 2
        componentRef.setInput('numberOfStudentsInCourse', 0);
        fixture.detectChanges();
        component.switchTimeSpan(SwitchTimeSpanDirection.RIGHT);
        expect(statsSpy).toHaveBeenLastCalledWith(1, 1, component.displayedNumberOfWeeks());
        for (let i = 0; i < component.displayedNumberOfWeeks(); i++) {
            expect(component.data()[0].series[i].value).toBe(0);
            expect(component.absoluteSeries[i]['absoluteValue']).toBe(0);
        }
    });

    it('should show only 2 weeks if start date is 1 week ago', () => {
        // 1 week ago -> chart covers 2 weeks (inclusive counting)
        componentRef.setInput('course', { id: 1, startDate: dayjs().subtract(1, 'week') });

        // Return 2 weeks of raw values (12, 42) => with 50 students, 24% and 84%
        vi.spyOn(service, 'getStatisticsData').mockReturnValue(of([12, 42]));

        fixture.detectChanges();

        expect(component.data()[0].series).toHaveLength(2);
        expect(component.data()[0].series[0].value).toBe(24);
        expect(component.data()[0].series[1].value).toBe(84);

        // absolute values preserved for tooltip
        expect(component.absoluteSeries[0].absoluteValue).toBe(12);
        expect(component.absoluteSeries[1].absoluteValue).toBe(42);
    });

    it('should adapt labels if end date is passed (last label equals course end ISO week)', () => {
        const endDate = dayjs().subtract(1, 'week');
        componentRef.setInput('course', { id: 1, endDate });

        vi.spyOn(service, 'getStatisticsData').mockReturnValue(of(graphData.slice(0, 8)));

        fixture.detectChanges();

        const last = component.data()[0].series[component.data()[0].series.length - 1];
        expect(last.name).toBe(endDate.isoWeek().toString());
    });

    it('should adapt if course phase is smaller than 4 weeks', () => {
        const endDate = dayjs().subtract(1, 'week');
        const startDate = dayjs().subtract(2, 'weeks');
        componentRef.setInput('course', { id: 1, startDate, endDate });

        // Lifetime overview is shown by default only if startDate exists AND endDate is in the past relative to now;
        // here that's true -> lifetime overview path
        vi.spyOn(service, 'getStatisticsForLifetimeOverview').mockReturnValue(of([12, 42]));

        componentRef.setInput('numberOfStudentsInCourse', 50);
        fixture.detectChanges();

        expect(component.showLifetimeOverview()).toBe(true);
        expect(component.startDateDisplayed()).toBe(true);
        expect(component.showsCurrentWeek()).toBe(true);

        // 2 weeks span, values as percentages
        expect(component.data()[0].series).toHaveLength(2);
        expect(component.data()[0].series[0].value).toBe(24);
        expect(component.data()[0].series[1].value).toBe(84);

        // last label corresponds to the end date ISO week
        expect(component.data()[0].series[1].name).toBe(endDate.isoWeek().toString());
    });

    it('should limit the next view if start date is reached when paging back', () => {
        const startDate = dayjs().subtract(component.displayedNumberOfWeeks(), 'weeks');
        componentRef.setInput('course', { id: 42, startDate });

        const getStatisticsDataMock = vi.spyOn(service, 'getStatisticsData').mockReturnValue(of(initialStats.slice(0, 8)));

        fixture.detectChanges();

        expect(component.data()[0].series).toHaveLength(Math.min(8, component.displayedNumberOfWeeks()));

        component.switchTimeSpan(SwitchTimeSpanDirection.LEFT);

        expect(component.data()[0].series).toHaveLength(1);
        expect(component.data()[0].series[0].name).toBe(startDate.isoWeek().toString());

        expect(getStatisticsDataMock).toHaveBeenCalledWith(42, -1, 8);
        expect(getStatisticsDataMock).toHaveBeenCalledTimes(2);
    });

    it('should create lifetime overview and cache data', () => {
        const startDate = dayjs().subtract(17, 'weeks');
        componentRef.setInput('course', { id: 42, startDate });

        const overviewSpy = vi.spyOn(service, 'getStatisticsForLifetimeOverview').mockReturnValue(of(initialStats));

        fixture.detectChanges();
        component.displayLifetimeOverview();

        expect(component.showLifetimeOverview()).toBe(true);
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
        componentRef.setInput('course', { id: 42, startDate, endDate });
        componentRef.setInput('numberOfStudentsInCourse', 0);

        vi.spyOn(service, 'getStatisticsData').mockReturnValue(of(initialStats.slice(0, 5)));

        fixture.detectChanges();

        expect(component.data()[0].series).toHaveLength(5);
        for (let week = 0; week < 5; week++) {
            expect(component.data()[0].series[week].value).toBe(0);
        }
    });
});
