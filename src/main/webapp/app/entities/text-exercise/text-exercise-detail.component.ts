import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { ITextExercise } from 'app/shared/model/text-exercise.model';

@Component({
    selector: 'jhi-text-exercise-detail',
    templateUrl: './text-exercise-detail.component.html'
})
export class TextExerciseDetailComponent implements OnInit {
    textExercise: ITextExercise;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ textExercise }) => {
            this.textExercise = textExercise;
        });
    }

    previousState() {
        window.history.back();
    }
}
