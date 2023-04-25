import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExerciseCacheService } from 'app/exercises/shared/exercise/exercise-cache.service';
import { StandaloneFeedbackComponent } from 'app/exercises/shared/feedback/standalone-feedback/standalone-feedback.component';
import { MockComponent, MockProvider } from 'ng-mocks';
import { FeedbackComponent } from 'app/exercises/shared/feedback/feedback.component';
import { Course } from 'app/entities/course.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

describe('StandaloneFeedbackComponent', () => {
    let component: StandaloneFeedbackComponent;
    let fixture: ComponentFixture<StandaloneFeedbackComponent>;

    let exerciseService: ExerciseService;
    let exerciseCacheService: ExerciseCacheService;

    let getExerciseDetailsMock: jest.SpyInstance;
    let getLatestDueDateMock: jest.SpyInstance;

    const course = { id: 1 } as unknown as Course;
    const exercise = new ProgrammingExercise(course, undefined);
    const participation = { id: 2 } as unknown as StudentParticipation;
    const result = { id: 3, participation: { id: 2 } };
    const latestDueDate = new Date();

    beforeEach(() => {
        const activatedRouteStub = {
            params: of({ exerciseId: '1', participationId: '2', resultId: '3', isTemplateStatusMissing: 'false' }),
        };

        TestBed.configureTestingModule({
            declarations: [StandaloneFeedbackComponent, MockComponent(FeedbackComponent)],
            providers: [{ provide: ActivatedRoute, useValue: activatedRouteStub }, MockProvider(ExerciseService), MockProvider(ExerciseCacheService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StandaloneFeedbackComponent);
                component = fixture.componentInstance;

                // mock exerciseService
                exerciseService = fixture.debugElement.injector.get(ExerciseService);
                getExerciseDetailsMock = jest.spyOn(exerciseService, 'getExerciseDetails');
                participation.results = [result];
                exercise.studentParticipations = [participation];
                course.exercises = [exercise];
                getExerciseDetailsMock.mockReturnValue(of({ body: exercise }));

                // mock exerciseCacheService
                exerciseCacheService = fixture.debugElement.injector.get(ExerciseCacheService);
                getLatestDueDateMock = jest.spyOn(exerciseCacheService, 'getLatestDueDate');
                getLatestDueDateMock.mockReturnValue(of(latestDueDate));
            });
    });

    it('should set exercise, result and latestDueDate correctly', fakeAsync(() => {
        fixture.detectChanges();
        tick(500);

        expect(component.exercise).toBe(exercise);
        expect(component.result).toBe(result);
        expect(component.latestDueDate).toBe(latestDueDate);
    }));

    it('should set showMissingAutomaticFeedbackInformation and messageKey correctly', fakeAsync(() => {
        fixture.detectChanges();
        tick(500);

        expect(component.showMissingAutomaticFeedbackInformation).toBeFalse();
        expect(component.messageKey).toBeUndefined();
    }));
});
