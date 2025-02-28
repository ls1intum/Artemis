import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BuildJobStatisticsComponent } from '../../../../../../../main/webapp/app/localci/build-queue/build-job-statistics/build-job-statistics.component';
import { BuildJobStatistics, SpanType } from '../../../../../../../main/webapp/app/entities/programming/build-job.model';
import { BuildQueueService } from '../../../../../../../main/webapp/app/localci/build-queue/build-queue.service';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
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
                { provide: BuildQueueService, useValue: mockBuildQueueService },
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
    });

    it('should get build job statistics when changing the span', () => {
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(mockBuildJobStatistics));

        component.ngOnInit();
        component.onTabChange(SpanType.DAY);

        expect(mockBuildQueueService.getBuildJobStatistics).toHaveBeenCalledTimes(2);
        expect(component.buildJobStatistics).toEqual(mockBuildJobStatistics);
    });

    it('should not get build job statistics when span is the same', () => {
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(mockBuildJobStatistics));

        component.ngOnInit();
        component.onTabChange(SpanType.WEEK);

        expect(mockBuildQueueService.getBuildJobStatistics).toHaveBeenCalledOnce();
        expect(component.buildJobStatistics).toEqual(mockBuildJobStatistics);
    });

    it('should get build job statistics for course when courseId is present', () => {
        mockActivatedRoute.paramMap = of(new Map([['courseId', testCourseId]]));
        mockBuildQueueService.getBuildJobStatisticsForCourse.mockReturnValue(of(mockBuildJobStatistics));

        component.ngOnInit();
        component.onTabChange(SpanType.WEEK);

        expect(mockBuildQueueService.getBuildJobStatisticsForCourse).toHaveBeenNthCalledWith(1, testCourseId, SpanType.WEEK);
        expect(component.buildJobStatistics).toEqual(mockBuildJobStatistics);
    });
});
