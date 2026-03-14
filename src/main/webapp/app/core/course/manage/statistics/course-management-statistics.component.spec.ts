import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { SpanType } from 'app/exercise/shared/entities/statistics.model';
import { CourseManagementStatisticsComponent } from 'app/core/course/manage/statistics/course-management-statistics.component';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import { of } from 'rxjs';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { provideHttpClient } from '@angular/common/http';
import { provideNoopAnimationsForTests } from 'test/helpers/animations';

describe('CourseManagementStatisticsComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CourseManagementStatisticsComponent>;
    let component: CourseManagementStatisticsComponent;
    let service: StatisticsService;

    const returnValue = {
        averageScoreOfCourse: 75,
        averageScoresOfExercises: [
            { exerciseId: 1, exerciseName: 'PatternsExercise', averageScore: 50, exerciseType: ExerciseType.PROGRAMMING },
            { exerciseId: 2, exerciseName: 'MorePatterns', averageScore: 50, exerciseType: ExerciseType.MODELING },
        ],
    };

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [RouterModule.forRoot([]), CourseManagementStatisticsComponent],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ courseId: 123 }) },
                provideHttpClient(),
                provideNoopAnimationsForTests(),
            ],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseManagementStatisticsComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(StatisticsService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        const statisticsService = vi.spyOn(service, 'getCourseStatistics').mockReturnValue(of(returnValue));
        fixture.detectChanges();
        expect(statisticsService).toHaveBeenCalledOnce();
    });

    it('should trigger when tab changed', async () => {
        vi.spyOn(service, 'getCourseStatistics').mockReturnValue(of(returnValue));
        const tabSpy = vi.spyOn(component, 'onTabChanged');
        fixture.detectChanges();

        const button = fixture.debugElement.nativeElement.querySelector('#option3');
        button.click();

        await fixture.whenStable();
        expect(tabSpy).toHaveBeenCalledOnce();
        expect(component.currentSpan()).toBe(SpanType.MONTH);
    });
});
