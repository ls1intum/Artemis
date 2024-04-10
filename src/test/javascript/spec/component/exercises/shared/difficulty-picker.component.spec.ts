import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DifficultyPickerComponent } from 'app/exercises/shared/difficulty-picker/difficulty-picker.component';
import { DifficultyLevel, Exercise, ExerciseType } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

const mockExercise: Exercise = {
    id: 1,
    title: 'Sample Exercise',
    maxPoints: 100,
    dueDate: dayjs().subtract(3, 'hours'),
    assessmentType: AssessmentType.AUTOMATIC,
    type: ExerciseType.PROGRAMMING,
} as Exercise;
describe('DifficultyPickerComponent', () => {
    let component: DifficultyPickerComponent;
    let fixture: ComponentFixture<DifficultyPickerComponent>;
    // let mockExercise: Exercise;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [DifficultyPickerComponent, MockPipe(ArtemisTranslatePipe)],
            // Add any required imports here, e.g., FormsModule if you're using ngModel in the template
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(DifficultyPickerComponent);
        component = fixture.componentInstance;

        // Initialize a mock exercise object
        // mockExercise = {
        //     id: 1,
        //     title: 'Sample Exercise',
        //     maxPoints: 100,
        //     dueDate: dayjs().subtract(3, 'hours'),
        //     assessmentType: AssessmentType.AUTOMATIC,
        //     type: ExerciseType.PROGRAMMING,
        // }

        component.exercise = mockExercise;

        fixture.detectChanges(); // Trigger initial data binding
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should set the difficulty level when setDifficulty is called', () => {
        const expectedDifficulty = DifficultyLevel.HARD;
        component.setDifficulty(expectedDifficulty);
        expect(component.exercise.difficulty).toEqual(expectedDifficulty);
    });

    it('should emit a change event when difficulty is set', () => {
        jest.spyOn(component.ngModelChange, 'emit');
        component.setDifficulty(DifficultyLevel.MEDIUM);
        expect(component.ngModelChange.emit).toHaveBeenCalled();
    });
});
