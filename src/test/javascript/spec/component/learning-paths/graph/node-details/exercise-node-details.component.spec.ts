import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { MockComponent, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { CompetencyNodeDetailsComponent } from 'app/course/learning-paths/learning-path-graph/node-details/competency-node-details.component';
import { Competency, CompetencyProgress, CompetencyTaxonomy } from 'app/entities/competency.model';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { CompetencyRingsComponent } from 'app/course/competencies/competency-rings/competency-rings.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltipMocksModule } from '../../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { ExerciseNodeDetailsComponent } from 'app/course/learning-paths/learning-path-graph/node-details/exercise-node-details.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Exercise } from 'app/entities/exercise.model';

describe('ExerciseNodeDetailsComponent', () => {
    let fixture: ComponentFixture<ExerciseNodeDetailsComponent>;
    let comp: ExerciseNodeDetailsComponent;
    let exerciseService: ExerciseService;
    let findStub: jest.SpyInstance;
    let exercise: Exercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbTooltipMocksModule],
            declarations: [ExerciseNodeDetailsComponent],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseNodeDetailsComponent);
                comp = fixture.componentInstance;
                exercise = new Exercise();
                exercise.id = 1;

                exerciseService = TestBed.inject(ExerciseService);
                findStub = jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));
                comp.exerciseId = exercise.id;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load exercise on init', () => {
        fixture.detectChanges();
        expect(findStub).toHaveBeenCalledOnce();
        expect(findStub).toHaveBeenCalledWith(exercise.id);
        expect(comp.exercise).toEqual(exercise);
    });
});
