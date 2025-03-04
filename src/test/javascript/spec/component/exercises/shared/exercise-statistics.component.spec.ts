import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { SpanType } from 'app/entities/statistics.model';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { of } from 'rxjs';
import { ExerciseStatisticsComponent } from 'app/exercises/shared/statistics/exercise-statistics.component';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { Exercise } from 'app/entities/exercise.model';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockActivatedRoute } from '../../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';

describe('ExerciseStatisticsComponent', () => {
    let fixture: ComponentFixture<ExerciseStatisticsComponent>;
    let component: ExerciseStatisticsComponent;
    let service: StatisticsService;
    let exerciseService: ExerciseService;

    let statisticsSpy: jest.SpyInstance;
    let exerciseSpy: jest.SpyInstance;

    const exercise = {
        id: 1,
        course: {
            id: 2,
        },
    } as Exercise;

    const exerciseStatistics = {
        averageScoreOfExercise: 50,
        maxPointsOfExercise: 10,
        absoluteAveragePoints: 5,
        scoreDistribution: [5, 0, 0, 0, 0, 0, 0, 0, 0, 5],
        numberOfExerciseScores: 10,
        numberOfParticipations: 10,
        numberOfStudentsOrTeamsInCourse: 10,
        participationsInPercent: 100,
        numberOfPosts: 4,
        numberOfResolvedPosts: 2,
        resolvedPostsInPercent: 50,
    } as ExerciseManagementStatisticsDto;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NoopAnimationsModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ id: 123 }) },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseStatisticsComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(StatisticsService);
                exerciseService = TestBed.inject(ExerciseService);
                statisticsSpy = jest.spyOn(service, 'getExerciseStatistics').mockReturnValue(of(exerciseStatistics));
                exerciseSpy = jest.spyOn(exerciseService, 'find').mockReturnValue(of({ body: exercise } as HttpResponse<Exercise>));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(statisticsSpy).toHaveBeenCalledOnce();
        expect(exerciseSpy).toHaveBeenCalledOnce();
        expect(component.exerciseStatistics.participationsInPercent).toBe(100);
        expect(component.exerciseStatistics.resolvedPostsInPercent).toBe(50);
        expect(component.exerciseStatistics.absoluteAveragePoints).toBe(5);
    });

    it('should trigger when tab changed', fakeAsync(() => {
        const tabSpy = jest.spyOn(component, 'onTabChanged');
        fixture.detectChanges();

        const button = fixture.debugElement.nativeElement.querySelector('#option3');
        button.click();

        tick();
        expect(tabSpy).toHaveBeenCalledOnce();
        expect(component.currentSpan).toEqual(SpanType.MONTH);
        expect(statisticsSpy).toHaveBeenCalledOnce();
        expect(exerciseSpy).toHaveBeenCalledOnce();
        expect(component.exerciseStatistics.participationsInPercent).toBe(100);
        expect(component.exerciseStatistics.resolvedPostsInPercent).toBe(50);
        expect(component.exerciseStatistics.absoluteAveragePoints).toBe(5);
    }));
});
