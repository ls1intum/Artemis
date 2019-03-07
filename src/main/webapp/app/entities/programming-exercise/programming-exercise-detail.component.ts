import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { ProgrammingExercise, ProgrammingLanguage } from './programming-exercise.model';
import { ProgrammingExerciseService } from 'app/entities/programming-exercise/programming-exercise.service';
import { JhiAlertService } from 'ng-jhipster';

@Component({
    selector: 'jhi-programming-exercise-detail',
    templateUrl: './programming-exercise-detail.component.html'
})
export class ProgrammingExerciseDetailComponent implements OnInit {
    readonly JAVA = ProgrammingLanguage.JAVA;

    programmingExercise: ProgrammingExercise;

    constructor(private activatedRoute: ActivatedRoute, private programmingExerciseService: ProgrammingExerciseService, private jhiAlertService: JhiAlertService) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ programmingExercise }) => {
            this.programmingExercise = programmingExercise;
        });
    }

    previousState() {
        window.history.back();
    }

    generateStructureOracle() {
        this.programmingExerciseService.generateStructureOracle(this.programmingExercise.id).subscribe(res => {
            const jhiAlert = this.jhiAlertService.success(res);
            jhiAlert.msg = res;
        }, error => {
            const errorMessage = error.headers.get('X-arTeMiSApp-alert');
            // TODO: this is a workaround to avoid translation not found issues. Provide proper translations
            const jhiAlert = this.jhiAlertService.error(errorMessage);
            jhiAlert.msg = errorMessage;
        });
    }
}
