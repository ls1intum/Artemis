import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { StatisticsGraphComponent } from 'app/shared/statistics-graph/statistics-graph.component';
import { SpanType } from 'app/exercise/shared/entities/statistics.model';
import { CourseManagementStatisticsComponent } from 'app/core/course/manage/statistics/course-management-statistics.component';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import { of } from 'rxjs';
import { StatisticsAverageScoreGraphComponent } from 'app/shared/statistics-graph/average-score-graph/statistics-average-score-graph.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { provideHttpClient } from '@angular/common/http';

describe('CourseManagementStatisticsComponent', () => {
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

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [RouterModule.forRoot([])],
            declarations: [
                CourseManagementStatisticsComponent,
                MockComponent(StatisticsGraphComponent),
                MockComponent(StatisticsAverageScoreGraphComponent),
                MockComponent(DocumentationButtonComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ courseId: 123 }) },
                provideHttpClient(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseManagementStatisticsComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(StatisticsService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        const statisticsService = jest.spyOn(service, 'getCourseStatistics').mockReturnValue(of(returnValue));
        fixture.detectChanges();
        expect(statisticsService).toHaveBeenCalledOnce();
    });

    it('should trigger when tab changed', fakeAsync(() => {
        jest.spyOn(service, 'getCourseStatistics').mockReturnValue(of(returnValue));
        const tabSpy = jest.spyOn(component, 'onTabChanged');
        fixture.detectChanges();

        const button = fixture.debugElement.nativeElement.querySelector('#option3');
        button.click();

        tick();
        expect(tabSpy).toHaveBeenCalledOnce();
        expect(component.currentSpan).toBe(SpanType.MONTH);
    }));
});
