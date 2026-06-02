import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { DifficultyLevel, Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { DifficultyPickerComponent } from 'app/exercise/difficulty-picker/difficulty-picker.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import dayjs from 'dayjs/esm';

function createExercise(): Exercise {
    return {
        id: 1,
        title: 'Sample Exercise',
        maxPoints: 100,
        dueDate: dayjs().subtract(3, 'hours'),
        assessmentType: AssessmentType.AUTOMATIC,
        type: ExerciseType.PROGRAMMING,
    } as Exercise;
}

describe('DifficultyPickerComponent', () => {
    setupTestBed({ zoneless: true });

    let component: DifficultyPickerComponent;
    let fixture: ComponentFixture<DifficultyPickerComponent>;
    let exercise: Exercise;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [DifficultyPickerComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        exercise = createExercise();
        fixture = TestBed.createComponent(DifficultyPickerComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should set the difficulty level when setDifficulty is called', () => {
        component.setDifficulty(DifficultyLevel.HARD);

        expect(exercise.difficulty).toBe(DifficultyLevel.HARD);
    });

    it('should emit a change event when difficulty is set', () => {
        const emitSpy = vi.spyOn(component.ngModelChange, 'emit');

        component.setDifficulty(DifficultyLevel.MEDIUM);

        expect(emitSpy).toHaveBeenCalledTimes(1);
    });
});
