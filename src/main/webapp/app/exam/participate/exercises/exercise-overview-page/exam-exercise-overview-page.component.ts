import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Exercise, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { ExamPageComponent } from 'app/exam/participate/exercises/exam-page.component';
import { ChangeDetectorRef } from '@angular/core';

@Component({
    selector: 'jhi-exam-exercise-overview-page',
    templateUrl: './exam-exercise-overview-page.component.html',
    styleUrls: ['./exam-exercise-overview-page.scss'],
})
export class ExamExerciseOverviewPageComponent extends ExamPageComponent {
    @Input() exercises: Exercise[];
    @Output() onPageChanged = new EventEmitter<{ overViewChange: boolean; exercise: Exercise; forceSave: boolean }>();
    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    constructor(protected changeDetectorReference: ChangeDetectorRef) {
        super(changeDetectorReference);
    }

    openExercise(exercise: Exercise) {
        this.onPageChanged.emit({ overViewChange: false, exercise, forceSave: false });
    }
}
