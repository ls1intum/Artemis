import { Component, Input, OnInit } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { ExamPageComponent } from 'app/exam/participate/exercises/exam-page.component';
import { ChangeDetectorRef } from '@angular/core';

@Component({
    selector: 'jhi-exam-exercise-overview-page',
    templateUrl: './exam-exercise-overview-page.component.html',
    styleUrls: ['./exam-exercise-overview-page.scss'],
})
export class ExamExerciseOverviewPageComponent extends ExamPageComponent implements OnInit {
    @Input() exercises: Exercise[];

    constructor(protected changeDetectorReference: ChangeDetectorRef) {
        super(changeDetectorReference);
    }

    ngOnInit(): void {
        console.log('right component');
    }
}
