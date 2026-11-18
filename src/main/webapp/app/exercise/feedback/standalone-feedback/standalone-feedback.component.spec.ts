import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { By } from '@angular/platform-browser';
import { HttpResponse } from '@angular/common/http';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ExerciseCacheService } from 'app/exercise/services/exercise-cache.service';
import { StandaloneFeedbackComponent } from 'app/exercise/feedback/standalone-feedback/standalone-feedback.component';
import { MockComponent, MockProvider } from 'ng-mocks';
import { FeedbackComponent } from 'app/exercise/feedback/feedback.component';
import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ResultService } from 'app/exercise/result/result.service';
import { MockResultService } from 'test/helpers/mocks/service/mock-result.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('StandaloneFeedbackComponent', () => {
    let component: StandaloneFeedbackComponent;
    let fixture: ComponentFixture<StandaloneFeedbackComponent>;

    let exerciseService: ExerciseService;
    let exerciseCacheService: ExerciseCacheService;

    let routeParams: BehaviorSubject<{ exerciseId: string; participationId: string; resultId: string }>;
    let getExerciseDetailsMock: ReturnType<typeof vi.spyOn>;
    let getLatestDueDateMock: ReturnType<typeof vi.spyOn>;

    const course = { id: 1 } as unknown as Course;
    const exercise = new ProgrammingExercise(course, undefined);
    const participation = { id: 2 } as unknown as StudentParticipation;
    const result = { id: 3, participation: { id: 2 } };
    const latestDueDate = new Date();

    beforeEach(async () => {
        routeParams = new BehaviorSubject({ exerciseId: '1', participationId: '2', resultId: '3' });
        const activatedRouteStub = { params: routeParams };

        await TestBed.configureTestingModule({
            imports: [StandaloneFeedbackComponent, MockComponent(FeedbackComponent)],
            providers: [
                { provide: ActivatedRoute, useValue: activatedRouteStub },
                MockProvider(ExerciseService),
                MockProvider(ExerciseCacheService),
                { provide: ResultService, useClass: MockResultService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(StandaloneFeedbackComponent);
        component = fixture.componentInstance;

        exerciseService = TestBed.inject(ExerciseService);
        getExerciseDetailsMock = vi.spyOn(exerciseService, 'getExerciseDetails');
        participation.submissions = [{ results: [result] }];
        exercise.studentParticipations = [participation];
        course.exercises = [exercise];
        getExerciseDetailsMock.mockReturnValue(of({ body: { exercise: exercise } }));

        exerciseCacheService = TestBed.inject(ExerciseCacheService);
        getLatestDueDateMock = vi.spyOn(exerciseCacheService, 'getLatestDueDate');
        getLatestDueDateMock.mockReturnValue(of(latestDueDate));
    });

    it('should set exercise, result and latestDueDate correctly', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.exercise()).toBe(exercise);
        expect(component.result()).toBe(result);
        expect(component.latestDueDate()).toBe(latestDueDate);
    });

    it('recreates the details for the current route and ignores an older request', () => {
        fixture.detectChanges();
        const initialDetails = fixture.debugElement.query(By.directive(FeedbackComponent)).componentInstance;
        const nextResult = { id: 5, score: 60 };
        const outdatedResult = { id: 4, score: 90 };
        participation.submissions = [{ results: [result, outdatedResult, nextResult] }];
        const outdatedRequest = new Subject<HttpResponse<{ exercise: ProgrammingExercise }>>();
        getExerciseDetailsMock.mockReturnValueOnce(outdatedRequest);

        routeParams.next({ exerciseId: '1', participationId: '2', resultId: '4' });
        routeParams.next({ exerciseId: '1', participationId: '2', resultId: '5' });
        outdatedRequest.next(new HttpResponse({ body: { exercise } }));
        fixture.detectChanges();

        expect(component.result()).toBe(nextResult);
        const currentDetails = fixture.debugElement.query(By.directive(FeedbackComponent)).componentInstance;
        expect(currentDetails).not.toBe(initialDetails);
    });

    it('should set showMissingAutomaticFeedbackInformation and messageKey correctly', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.showMissingAutomaticFeedbackInformation()).toBe(false);
        expect(component.messageKey()).toBeUndefined();
    });
});
