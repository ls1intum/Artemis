import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { BuildJobStatisticsComponent } from 'app/buildagent/build-job-statistics/build-job-statistics.component';
import { BuildJobStatistics, SpanType } from 'app/buildagent/shared/entities/build-job.model';
import { BuildOverviewService } from 'app/buildagent/build-queue/build-overview.service';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { HttpErrorResponse } from '@angular/common/http';

describe('BuildJobStatisticsComponent', () => {
    setupTestBed({ zoneless: true });

    let component: BuildJobStatisticsComponent;
    let fixture: ComponentFixture<BuildJobStatisticsComponent>;
    const mockActivatedRoute: any = {};

    const mockBuildQueueService = {
        getBuildJobStatistics: vi.fn(),
        getBuildJobStatisticsForCourse: vi.fn(),
    };

    const mockBuildJobStatistics: BuildJobStatistics = {
        totalBuilds: 15,
        successfulBuilds: 5,
        failedBuilds: 3,
        cancelledBuilds: 2,
        timeOutBuilds: 3,
        missingBuilds: 2,
    };

    const testCourseId = 123;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BuildJobStatisticsComponent],
            providers: [
                { provide: BuildOverviewService, useValue: mockBuildQueueService },
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        mockActivatedRoute.paramMap = of(new Map([]));

        fixture = TestBed.createComponent(BuildJobStatisticsComponent);
        component = fixture.componentInstance;
    });

    beforeEach(() => {
        mockBuildQueueService.getBuildJobStatistics.mockClear();
        mockBuildQueueService.getBuildJobStatisticsForCourse.mockClear();
    });

    it('should get build job statistics when changing the span', () => {
        mockActivatedRoute.url = of([{ path: 'build-overview' }]);
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(mockBuildJobStatistics));

        component.ngOnInit();
        component.onTabChange(SpanType.DAY);

        expect(mockBuildQueueService.getBuildJobStatistics).toHaveBeenCalledTimes(2);
        expect(component.buildJobStatistics()).toEqual(mockBuildJobStatistics);
    });

    it('should not get build job statistics when span is the same', () => {
        mockActivatedRoute.url = of([{ path: 'build-overview' }]);
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(mockBuildJobStatistics));

        component.ngOnInit();
        component.onTabChange(SpanType.WEEK);

        expect(mockBuildQueueService.getBuildJobStatistics).toHaveBeenCalledOnce();
        expect(component.buildJobStatistics()).toEqual(mockBuildJobStatistics);
    });

    it('should get build job statistics for course when courseId is present', () => {
        mockActivatedRoute.url = of([{ path: 'build-overview' }]);
        mockActivatedRoute.paramMap = of(new Map([['courseId', testCourseId]]));
        mockBuildQueueService.getBuildJobStatisticsForCourse.mockReturnValue(of(mockBuildJobStatistics));

        component.ngOnInit();
        component.onTabChange(SpanType.WEEK);

        expect(mockBuildQueueService.getBuildJobStatisticsForCourse).toHaveBeenNthCalledWith(1, testCourseId, SpanType.WEEK);
        expect(component.buildJobStatistics()).toEqual(mockBuildJobStatistics);
    });

    it('should use stats from input', () => {
        mockActivatedRoute.url = of([{ path: 'build-agent' }]);
        mockBuildQueueService.getBuildJobStatisticsForCourse.mockReturnValue(of(mockBuildJobStatistics));
        fixture.componentRef.setInput('buildJobStatisticsInput', mockBuildJobStatistics);

        component.ngOnInit();

        expect(mockBuildQueueService.getBuildJobStatistics).toHaveBeenCalledTimes(0);
        expect(mockBuildQueueService.getBuildJobStatisticsForCourse).toHaveBeenCalledTimes(0);
        expect(component.buildJobStatistics()).toEqual(mockBuildJobStatistics);
        expect(component.displayMissingBuilds).toBeFalsy();
        expect(component.displaySpanSelector).toBeFalsy();
    });

    it('should handle error when getting build job statistics', () => {
        mockActivatedRoute.url = of([{ path: 'build-overview' }]);
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        component.ngOnInit();

        expect(mockBuildQueueService.getBuildJobStatistics).toHaveBeenCalledOnce();
    });

    it('should handle error when getting build job statistics for course', () => {
        mockActivatedRoute.url = of([{ path: 'build-overview' }]);
        mockActivatedRoute.paramMap = of(new Map([['courseId', testCourseId]]));
        mockBuildQueueService.getBuildJobStatisticsForCourse.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        component.ngOnInit();

        expect(mockBuildQueueService.getBuildJobStatisticsForCourse).toHaveBeenCalledOnce();
    });

    it('should display placeholder percentages when total builds is zero', () => {
        mockActivatedRoute.url = of([{ path: 'build-overview' }]);
        const emptyStatistics: BuildJobStatistics = {
            totalBuilds: 0,
            successfulBuilds: 0,
            failedBuilds: 0,
            cancelledBuilds: 0,
            timeOutBuilds: 0,
            missingBuilds: 0,
        };
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(emptyStatistics));

        component.ngOnInit();

        expect(component.successfulBuildsPercentage()).toBe('-%');
        expect(component.failedBuildsPercentage()).toBe('-%');
        expect(component.cancelledBuildsPercentage()).toBe('-%');
        expect(component.timeoutBuildsPercentage()).toBe('-%');
        expect(component.missingBuildsPercentage()).toBe('-%');
    });

    it('should calculate percentages correctly when total builds is non-zero', () => {
        mockActivatedRoute.url = of([{ path: 'build-overview' }]);
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(mockBuildJobStatistics));

        component.ngOnInit();

        expect(component.successfulBuildsPercentage()).toBe('33.33%');
        expect(component.failedBuildsPercentage()).toBe('20.00%');
        expect(component.cancelledBuildsPercentage()).toBe('13.33%');
        expect(component.timeoutBuildsPercentage()).toBe('20.00%');
        expect(component.missingBuildsPercentage()).toBe('13.33%');
    });

    it('should include missing builds in chart data when displayMissingBuilds is true', () => {
        mockActivatedRoute.url = of([{ path: 'build-overview' }]);
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(mockBuildJobStatistics));

        component.ngOnInit();

        expect(component.displayMissingBuilds).toBeTruthy();
        expect(component.pieChartData().length).toBe(5);
        expect(component.pieChartData()[4].name).toBe('Missing');
    });

    it('should not include missing builds in chart data when displayMissingBuilds is false', () => {
        mockActivatedRoute.url = of([{ path: 'build-agent' }]);
        fixture.componentRef.setInput('buildJobStatisticsInput', mockBuildJobStatistics);

        component.ngOnInit();

        expect(component.displayMissingBuilds).toBeFalsy();
        expect(component.pieChartData().length).toBe(4);
    });

    it('should increment statistics for SUCCESSFUL status', () => {
        mockActivatedRoute.url = of([{ path: 'build-overview' }]);
        const initialStats: BuildJobStatistics = {
            totalBuilds: 10,
            successfulBuilds: 5,
            failedBuilds: 2,
            cancelledBuilds: 1,
            timeOutBuilds: 1,
            missingBuilds: 1,
        };
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(initialStats));

        component.ngOnInit();
        component.incrementStatisticsByStatus('SUCCESSFUL');

        expect(component.buildJobStatistics().totalBuilds).toBe(11);
        expect(component.buildJobStatistics().successfulBuilds).toBe(6);
    });

    it('should increment statistics for FAILED status', () => {
        mockActivatedRoute.url = of([{ path: 'build-overview' }]);
        const initialStats: BuildJobStatistics = {
            totalBuilds: 10,
            successfulBuilds: 5,
            failedBuilds: 2,
            cancelledBuilds: 1,
            timeOutBuilds: 1,
            missingBuilds: 1,
        };
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(initialStats));

        component.ngOnInit();
        component.incrementStatisticsByStatus('FAILED');

        expect(component.buildJobStatistics().totalBuilds).toBe(11);
        expect(component.buildJobStatistics().failedBuilds).toBe(3);
    });

    it('should increment statistics for ERROR status', () => {
        mockActivatedRoute.url = of([{ path: 'build-overview' }]);
        const initialStats: BuildJobStatistics = {
            totalBuilds: 10,
            successfulBuilds: 5,
            failedBuilds: 2,
            cancelledBuilds: 1,
            timeOutBuilds: 1,
            missingBuilds: 1,
        };
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(initialStats));

        component.ngOnInit();
        component.incrementStatisticsByStatus('ERROR');

        expect(component.buildJobStatistics().totalBuilds).toBe(11);
        expect(component.buildJobStatistics().failedBuilds).toBe(3);
    });

    it('should increment statistics for CANCELLED status', () => {
        mockActivatedRoute.url = of([{ path: 'build-overview' }]);
        const initialStats: BuildJobStatistics = {
            totalBuilds: 10,
            successfulBuilds: 5,
            failedBuilds: 2,
            cancelledBuilds: 1,
            timeOutBuilds: 1,
            missingBuilds: 1,
        };
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(initialStats));

        component.ngOnInit();
        component.incrementStatisticsByStatus('CANCELLED');

        expect(component.buildJobStatistics().totalBuilds).toBe(11);
        expect(component.buildJobStatistics().cancelledBuilds).toBe(2);
    });

    it('should increment statistics for TIMEOUT status', () => {
        mockActivatedRoute.url = of([{ path: 'build-overview' }]);
        const initialStats: BuildJobStatistics = {
            totalBuilds: 10,
            successfulBuilds: 5,
            failedBuilds: 2,
            cancelledBuilds: 1,
            timeOutBuilds: 1,
            missingBuilds: 1,
        };
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(initialStats));

        component.ngOnInit();
        component.incrementStatisticsByStatus('TIMEOUT');

        expect(component.buildJobStatistics().totalBuilds).toBe(11);
        expect(component.buildJobStatistics().timeOutBuilds).toBe(2);
    });

    it('should increment statistics for MISSING status', () => {
        mockActivatedRoute.url = of([{ path: 'build-overview' }]);
        const initialStats: BuildJobStatistics = {
            totalBuilds: 10,
            successfulBuilds: 5,
            failedBuilds: 2,
            cancelledBuilds: 1,
            timeOutBuilds: 1,
            missingBuilds: 1,
        };
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(initialStats));

        component.ngOnInit();
        component.incrementStatisticsByStatus('MISSING');

        expect(component.buildJobStatistics().totalBuilds).toBe(11);
        expect(component.buildJobStatistics().missingBuilds).toBe(2);
    });

    it('should not increment total builds for unknown status', () => {
        mockActivatedRoute.url = of([{ path: 'build-overview' }]);
        const initialStats: BuildJobStatistics = {
            totalBuilds: 10,
            successfulBuilds: 5,
            failedBuilds: 2,
            cancelledBuilds: 1,
            timeOutBuilds: 1,
            missingBuilds: 1,
        };
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(initialStats));

        const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
        component.ngOnInit();
        component.incrementStatisticsByStatus('UNKNOWN_STATUS');

        expect(component.buildJobStatistics().totalBuilds).toBe(10);
        expect(consoleSpy).toHaveBeenCalledWith('Unknown build job status received: UNKNOWN_STATUS');
        consoleSpy.mockRestore();
    });

    it('should not increment statistics when status is undefined', () => {
        mockActivatedRoute.url = of([{ path: 'build-overview' }]);
        const initialStats: BuildJobStatistics = {
            totalBuilds: 10,
            successfulBuilds: 5,
            failedBuilds: 2,
            cancelledBuilds: 1,
            timeOutBuilds: 1,
            missingBuilds: 1,
        };
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(initialStats));

        component.ngOnInit();
        component.incrementStatisticsByStatus(undefined);

        expect(component.buildJobStatistics().totalBuilds).toBe(10);
    });
});
