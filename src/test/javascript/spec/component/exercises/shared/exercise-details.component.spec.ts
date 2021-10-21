import { ExerciseDetailsComponent } from 'app/exercises/shared/exercise/exercise-details/exercise-details.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseLifecycleComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.component';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { getElement } from '../../../helpers/utils/general.utils';

describe('ExerciseDetailsComponent', () => {
    let component: ExerciseDetailsComponent;
    let fixture: ComponentFixture<ExerciseDetailsComponent>;

    const exercise: Exercise = {
        id: 1,
        type: ExerciseType.PROGRAMMING,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([])],
            declarations: [
                ExerciseDetailsComponent,
                MockComponent(ProgrammingExerciseLifecycleComponent),
                MockComponent(ProgrammingExerciseInstructionComponent),
                MockComponent(StructuredGradingInstructionsAssessmentLayoutComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseDetailsComponent);
                component = fixture.componentInstance;
                component.exercise = exercise;
            });
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).toBeDefined();
        expect(component.programmingExercise).toBeDefined();
    });

    it('should show timeline', () => {
        const timeline = getElement(fixture.debugElement, 'jhi-programming-exercise-lifecycle');
        expect(timeline).not.toBeNull;
    });

    it('should show instructions', () => {
        const instructions = getElement(fixture.debugElement, 'jhi-programming-exercise-instructions');
        expect(instructions).not.toBeNull;
    });

    describe('ExerciseDetailsComponent with Text Exercise', () => {
        const textExercise: Exercise = {
            id: 1,
            type: ExerciseType.TEXT,
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
            gradingCriteria: [{ title: 'criterion', structuredGradingInstructions: [] }],
        };

        it('should initialize', () => {
            component.exercise = textExercise;
            fixture.detectChanges();
            expect(component.programmingExercise).toBeUndefined();
        });

        it('should show grading criteria', () => {
            const gradingCriteria = getElement(fixture.debugElement, 'jhi-structured-grading-instructions-assessment-layout');
            expect(gradingCriteria).not.toBeNull;
        });
    });
});
