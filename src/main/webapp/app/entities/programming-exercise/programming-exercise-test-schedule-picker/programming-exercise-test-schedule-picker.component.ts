import { Component, Input, OnInit } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { AssessmentType } from 'app/entities/assessment-type';

@Component({
    selector: 'jhi-programming-exercise-test-schedule-picker',
    templateUrl: './programming-exercise-test-schedule-picker.component.html',
    styleUrls: ['./programming-exercise-test-schedule-picker.scss'],
})
export class ProgrammingExerciseTestSchedulePickerComponent implements OnInit {
    @Input() exercise: ProgrammingExercise;
    readonly assessmentType = AssessmentType;
    hasManualTests: boolean;

    ngOnInit(): void {
        if (!this.exercise.id) {
            this.exercise.assessmentType = AssessmentType.AUTOMATIC;
        }
    }

    toggleHasManualTests() {
        this.exercise.assessmentType = this.exercise.assessmentType === AssessmentType.AUTOMATIC ? AssessmentType.SEMI_AUTOMATIC : AssessmentType.AUTOMATIC;
    }
}
