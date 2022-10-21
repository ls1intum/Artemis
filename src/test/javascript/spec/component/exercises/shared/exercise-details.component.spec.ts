import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { AccountService } from 'app/core/auth/account.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ProgrammingExerciseLifecycleComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.component';
import { ExerciseDetailsComponent } from 'app/exercises/shared/exercise/exercise-details/exercise-details.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { getElement } from '../../../helpers/utils/general.utils';
import { ArtemisTestModule } from '../../../test.module';

describe('ExerciseDetailsComponent', () => {
    let component: ExerciseDetailsComponent;
    let fixture: ComponentFixture<ExerciseDetailsComponent>;
    let accountService: AccountService;
    let isAtLeastTutor: jest.SpyInstance;
    let isAtLeastEditor: jest.SpyInstance;
    let isAtLeastInstructor: jest.SpyInstance;

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
                accountService = TestBed.inject(AccountService);
                component.exercise = exercise;
                isAtLeastTutor = jest.spyOn(accountService, 'isAtLeastTutorForExercise');
                isAtLeastTutor.mockReturnValue(true);
                isAtLeastEditor = jest.spyOn(accountService, 'isAtLeastEditorForExercise');
                isAtLeastEditor.mockReturnValue(true);
                isAtLeastInstructor = jest.spyOn(accountService, 'isAtLeastInstructorForExercise');
                isAtLeastInstructor.mockReturnValue(true);
            });
    });

    it('should initialize programming exercise', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.programmingExercise).not.toBeNull();
        expect(isAtLeastTutor).toHaveBeenCalledOnce();
        expect(isAtLeastEditor).toHaveBeenCalledOnce();
        expect(isAtLeastInstructor).toHaveBeenCalledOnce();
    });

    it('should show timeline', () => {
        fixture.detectChanges();
        const timeline = getElement(fixture.debugElement, 'jhi-programming-exercise-lifecycle');
        expect(timeline).not.toBeNull();
    });

    it('should show instructions', () => {
        fixture.detectChanges();
        const instructions = getElement(fixture.debugElement, 'jhi-programming-exercise-instructions');
        expect(instructions).not.toBeNull();
    });

    it('should not show grading criteria', () => {
        fixture.detectChanges();
        const gradingCriteria = getElement(fixture.debugElement, 'jhi-structured-grading-instructions-assessment-layout');
        expect(gradingCriteria).toBeNull();
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

        it('should initialize text exercise', () => {
            component.exercise = textExercise;
            fixture.detectChanges();
            expect(component.programmingExercise).toBeUndefined();
            expect(isAtLeastTutor).toHaveBeenCalledOnce();
            expect(isAtLeastEditor).toHaveBeenCalledOnce();
            expect(isAtLeastInstructor).toHaveBeenCalledOnce();
        });

        it('should show grading criteria', () => {
            component.exercise = textExercise;
            fixture.detectChanges();
            const gradingCriteria = getElement(fixture.debugElement, 'jhi-structured-grading-instructions-assessment-layout');
            expect(gradingCriteria).not.toBeNull();
        });
    });
});
