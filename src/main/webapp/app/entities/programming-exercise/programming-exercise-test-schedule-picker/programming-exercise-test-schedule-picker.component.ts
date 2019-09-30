import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise';

@Component({
    selector: 'jhi-programming-exercise-test-schedule-picker',
    templateUrl: './programming-exercise-test-schedule-picker.component.html',
    styleUrls: ['./programming-exercise-test-schedule-picker.scss'],
})
export class ProgrammingExerciseTestSchedulePickerComponent {
    @Input() exercise: ProgrammingExercise;
    hasManualTests: boolean;

    toggleHasManualTests() {
        this.hasManualTests = !this.hasManualTests;
    }
}
