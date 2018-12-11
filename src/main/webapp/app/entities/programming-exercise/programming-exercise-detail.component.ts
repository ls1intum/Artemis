import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { ProgrammingExercise } from './programming-exercise.model';
import { ProgrammingExerciseService } from 'app/entities/programming-exercise/programming-exercise.service';

@Component({
    selector: 'jhi-programming-exercise-detail',
    templateUrl: './programming-exercise-detail.component.html'
})
export class ProgrammingExerciseDetailComponent implements OnInit {
    programmingExercise: ProgrammingExercise;

    constructor(private activatedRoute: ActivatedRoute, private programmingExerciseService: ProgrammingExerciseService) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ programmingExercise }) => {
            this.programmingExercise = programmingExercise;
        });
    }

    previousState() {
        window.history.back();
    }

    generateStructureDiff() {
        this.programmingExerciseService.generateStructureDiff(this.programmingExercise.id).subscribe();
    }
}
