import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CourseStatisticsPerformanceComponent } from './course-statistics-performance.component';

describe('CourseStatisticsPerformanceComponent', () => {
    beforeEach(() =>
        TestBed.configureTestingModule({
            imports: [CourseStatisticsPerformanceComponent],
            providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useClass: MockTranslateService }],
        }),
    );
    afterEach(() => TestBed.inject(HttpTestingController).verify());

    it('enables similar-student comparison by default and preserves missing scores as gaps', () => {
        const fixture = TestBed.createComponent(CourseStatisticsPerformanceComponent);
        fixture.componentRef.setInput('courseId', 589);
        fixture.detectChanges();
        TestBed.inject(HttpTestingController)
            .expectOne('api/exercise/courses/589/charts/peer-comparison')
            .flush({
                groupCount: 4,
                peerCount: 20,
                exercises: [
                    { exerciseId: 1, exerciseTitle: 'Assessed zero', ownScore: 0, peerAverage: 30, peerCount: 20 },
                    { exerciseId: 2, exerciseTitle: 'Pending', peerAverage: 40, peerCount: 20 },
                ],
            });
        fixture.detectChanges();
        expect(fixture.componentInstance.showComparison()).toBe(true);
        expect(fixture.componentInstance.chartSeries()).toHaveLength(2);
        expect(fixture.componentInstance.chartSeries()[0].data).toEqual([0, undefined]);
        expect(fixture.componentInstance.chartSeries()[1].data).toEqual([30, 40]);
    });

    it('does not substitute a global comparison when the cohort has insufficient evidence', () => {
        const fixture = TestBed.createComponent(CourseStatisticsPerformanceComponent);
        fixture.componentRef.setInput('courseId', 589);
        fixture.detectChanges();
        TestBed.inject(HttpTestingController)
            .expectOne('api/exercise/courses/589/charts/peer-comparison')
            .flush({ groupCount: 0, peerCount: 0, exercises: [{ exerciseId: 1, exerciseTitle: 'Own result', ownScore: 70 }] });
        fixture.detectChanges();
        expect(fixture.componentInstance.chartSeries()).toHaveLength(1);
        expect(fixture.nativeElement.textContent).toContain('artemisApp.courseOverview.statistics.redesign.comparisonUnavailable');
    });

    it('cancels old course requests and refreshes on reselection', () => {
        const fixture = TestBed.createComponent(CourseStatisticsPerformanceComponent);
        fixture.componentRef.setInput('courseId', 589);
        fixture.detectChanges();
        const old = TestBed.inject(HttpTestingController).expectOne('api/exercise/courses/589/charts/peer-comparison');
        fixture.componentRef.setInput('courseId', 590);
        fixture.detectChanges();
        expect(old.cancelled).toBe(true);
        TestBed.inject(HttpTestingController).expectOne('api/exercise/courses/590/charts/peer-comparison').flush({ exercises: [], groupCount: 0, peerCount: 0 });
        fixture.componentRef.setInput('refresh', 1);
        fixture.detectChanges();
        TestBed.inject(HttpTestingController).expectOne('api/exercise/courses/590/charts/peer-comparison').flush({ exercises: [], groupCount: 0, peerCount: 0 });
    });
});
