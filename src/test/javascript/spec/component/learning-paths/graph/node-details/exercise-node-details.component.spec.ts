import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltipMocksModule } from '../../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { ExerciseNodeDetailsComponent } from 'app/course/learning-paths/learning-path-graph/node-details/exercise-node-details.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Exercise } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';

describe('ExerciseNodeDetailsComponent', () => {
    let fixture: ComponentFixture<ExerciseNodeDetailsComponent>;
    let comp: ExerciseNodeDetailsComponent;
    let exerciseService: ExerciseService;
    let findStub: jest.SpyInstance;
    let exercise: Exercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbTooltipMocksModule],
            declarations: [ExerciseNodeDetailsComponent, MockPipe(ArtemisTranslatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseNodeDetailsComponent);
                comp = fixture.componentInstance;
                exercise = new TextExercise(undefined, undefined);
                exercise.id = 1;
                exercise.title = 'Some arbitrary title';

                exerciseService = TestBed.inject(ExerciseService);
                findStub = jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));
                comp.exerciseId = exercise.id;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load exercise on init if not present', () => {
        fixture.detectChanges();
        expect(findStub).toHaveBeenCalledOnce();
        expect(findStub).toHaveBeenCalledWith(exercise.id);
        expect(comp.exercise).toEqual(exercise);
    });

    it('should not load exercise on init if already present', () => {
        comp.exercise = exercise;
        fixture.detectChanges();
        expect(findStub).not.toHaveBeenCalled();
    });
});
