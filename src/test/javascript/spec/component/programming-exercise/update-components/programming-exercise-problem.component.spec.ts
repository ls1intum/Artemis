import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { NgModel } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';

import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseProblemComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-problem.component';
import { LearningGoalSelectionComponent } from 'app/shared/learning-goal-selection/learning-goal-selection.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('ProgrammingExerciseProblemComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseProblemComponent>;
    let comp: ProgrammingExerciseProblemComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule)],
            declarations: [
                ProgrammingExerciseProblemComponent,
                MockComponent(FaIconComponent),
                MockComponent(LearningGoalSelectionComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgModel),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseProblemComponent);
                comp = fixture.componentInstance;

                comp.validIdeSelection = () => true;
                comp.programmingExercise = new ProgrammingExercise(undefined, undefined);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize and store exercise', fakeAsync(() => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();

        const exercise = new ProgrammingExercise(undefined, undefined);
        comp.exercise = exercise;

        expect(comp.exercise).toBe(exercise);
    }));
});
