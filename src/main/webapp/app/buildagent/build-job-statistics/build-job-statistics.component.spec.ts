import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BuildJobStatisticsComponent } from 'app/buildagent/build-job-statistics/build-job-statistics.component';
import { BuildJobStatistics, SpanType } from 'app/buildagent/shared/entities/build-job.model';
import { BuildOverviewService } from 'app/buildagent/build-queue/build-overview.service';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';

describe('BuildJobStatisticsComponent', () => {
    let component: BuildJobStatisticsComponent;
    let fixture: ComponentFixture<BuildJobStatisticsComponent>;
    const mockActivatedRoute: any = {};

    const mockBuildQueueService = {
        getBuildJobStatistics: jest.fn(),
        getBuildJobStatisticsForCourse: jest.fn(),
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
        mockActivatedRoute.url = of([{ path: 'build-queue' }]);
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(mockBuildJobStatistics));

        component.ngOnInit();
        component.onTabChange(SpanType.DAY);

        expect(mockBuildQueueService.getBuildJobStatistics).toHaveBeenCalledTimes(2);
        expect(component.buildJobStatistics).toEqual(mockBuildJobStatistics);
    });

    it('should not get build job statistics when span is the same', () => {
        mockActivatedRoute.url = of([{ path: 'build-queue' }]);
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(mockBuildJobStatistics));

        component.ngOnInit();
        component.onTabChange(SpanType.WEEK);

        expect(mockBuildQueueService.getBuildJobStatistics).toHaveBeenCalledOnce();
        expect(component.buildJobStatistics).toEqual(mockBuildJobStatistics);
    });

    it('should get build job statistics for course when courseId is present', () => {
        mockActivatedRoute.url = of([{ path: 'build-queue' }]);
        mockActivatedRoute.paramMap = of(new Map([['courseId', testCourseId]]));
        mockBuildQueueService.getBuildJobStatisticsForCourse.mockReturnValue(of(mockBuildJobStatistics));

        component.ngOnInit();
        component.onTabChange(SpanType.WEEK);

        expect(mockBuildQueueService.getBuildJobStatisticsForCourse).toHaveBeenNthCalledWith(1, testCourseId, SpanType.WEEK);
        expect(component.buildJobStatistics).toEqual(mockBuildJobStatistics);
    });

    it('should use stats from input', () => {
        mockActivatedRoute.url = of([{ path: 'build-agent' }]);
        mockBuildQueueService.getBuildJobStatisticsForCourse.mockReturnValue(of(mockBuildJobStatistics));
        fixture.componentRef.setInput('buildJobStatisticsInput', mockBuildJobStatistics);

        component.ngOnInit();

        expect(mockBuildQueueService.getBuildJobStatistics).toHaveBeenCalledTimes(0);
        expect(mockBuildQueueService.getBuildJobStatisticsForCourse).toHaveBeenCalledTimes(0);
        expect(component.buildJobStatistics).toEqual(mockBuildJobStatistics);
        expect(component.displayMissingBuilds).toBeFalse();
        expect(component.displaySpanSelector).toBeFalse();
    });
});
