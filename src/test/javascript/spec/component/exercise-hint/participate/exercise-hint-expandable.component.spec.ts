import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockPipe } from 'ng-mocks';
import { HttpResponse } from '@angular/common/http';

import { ArtemisTestModule } from '../../../test.module';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { ExerciseHintResponse, ExerciseHintService } from 'app/exercises/shared/exercise-hint/shared/exercise-hint.service';
import { ExerciseHintExpandableComponent } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-expandable.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { StarRatingComponent } from 'app/exercises/shared/rating/star-rating/star-rating.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { CastToCodeHintPipe } from 'app/exercises/shared/exercise-hint/services/code-hint-cast.pipe';

describe('Exercise Hint Expandable Component', () => {
    let comp: ExerciseHintExpandableComponent;
    let fixture: ComponentFixture<ExerciseHintExpandableComponent>;

    let service: ExerciseHintService;

    const exercise = new ProgrammingExercise(undefined, undefined);
    exercise.id = 1;
    const availableExerciseHint = new ExerciseHint();
    availableExerciseHint.id = 2;
    availableExerciseHint.exercise = exercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExerciseHintExpandableComponent, MockPipe(ArtemisTranslatePipe), StarRatingComponent, CastToCodeHintPipe],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseHintExpandableComponent);
                comp = fixture.componentInstance;

                service = TestBed.inject(ExerciseHintService);
                comp.exerciseHint = availableExerciseHint;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load available exercise hint content', () => {
        const activateHintSpy = jest.spyOn(service, 'activateExerciseHint').mockReturnValue(of({ body: availableExerciseHint } as ExerciseHintResponse));
        comp.displayHintContent();
        expect(activateHintSpy).toHaveBeenCalledOnce();
        expect(activateHintSpy).toHaveBeenCalledWith(1, 2);
        expect(comp.expanded).toBeTrue();
        expect(comp.isLoading).toBeFalse();
        expect(comp.hasUsed).toBeTrue();
        expect(comp.exerciseHint).toEqual(availableExerciseHint);
    });

    it('should not load content when hint has already been used', () => {
        comp.hasUsed = true;
        const activateHintSpy = jest.spyOn(service, 'activateExerciseHint').mockReturnValue(of({ body: availableExerciseHint } as ExerciseHintResponse));
        comp.displayHintContent();
        expect(activateHintSpy).not.toHaveBeenCalled();
    });

    it('should collapse exercise hint content', () => {
        comp.collapseContent();
        expect(comp.expanded).toBeFalse();
    });

    it('should rate exercise hint', () => {
        const rateSpy = jest.spyOn(service, 'rateExerciseHint').mockReturnValue(of({} as HttpResponse<void>));
        comp.exerciseHint = availableExerciseHint;
        comp.onRate({ oldValue: 0, newValue: 4, starRating: new StarRatingComponent() });

        expect(rateSpy).toHaveBeenCalledOnce();
        expect(rateSpy).toHaveBeenCalledWith(1, 2, 4);
        expect(comp.exerciseHint.currentUserRating).toBe(4);
    });
});
