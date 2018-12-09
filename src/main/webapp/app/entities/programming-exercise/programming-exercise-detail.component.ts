import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { ProgrammingExercise } from './programming-exercise.model';

import { NgxSpinnerService } from 'ngx-spinner';
import { ProgrammingExerciseService } from 'app/entities/programming-exercise/programming-exercise.service';

@Component({
    selector: 'jhi-programming-exercise-detail',
    templateUrl: './programming-exercise-detail.component.html'
})
export class ProgrammingExerciseDetailComponent implements OnInit {
    programmingExercise: ProgrammingExercise;

    constructor(
        private activatedRoute: ActivatedRoute,
        private programmingExerciseService: ProgrammingExerciseService,
        private spinner: NgxSpinnerService
    ) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ programmingExercise }) => {
            this.programmingExercise = programmingExercise;
        });
    }

    previousState() {
        window.history.back();
    }

    generateStructureDiff() {
        // Show the popup "Generating structure diff..."
        this.spinner.show();
        setTimeout(() => {
            /** spinner ends after 8 seconds */
            this.spinner.hide();
        }, 8000);

        // Call the structure diff generator
        this.programmingExerciseService.generateTestDiff(this.programmingExercise.id);

        // Dismiss the popup
    }
}
