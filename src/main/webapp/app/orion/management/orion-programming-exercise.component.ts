import { Component, Input, OnInit } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { ExerciseView, OrionState } from 'app/shared/orion/orion';
import { Router } from '@angular/router';

@Component({
    selector: 'jhi-orion-programming-exercise',
    templateUrl: './orion-programming-exercise.component.html',
})
export class OrionProgrammingExerciseComponent implements OnInit {
    @Input() programmingExercises: ProgrammingExercise[];

    readonly ExerciseView = ExerciseView;

    orionState: OrionState;

    constructor(private orionConnectorService: OrionConnectorService, private router: Router) {}

    ngOnInit() {
        this.orionConnectorService.state().subscribe((state) => {
            this.orionState = state;
        });
    }

    editInIDE(programmingExercise: ProgrammingExercise) {
        this.orionConnectorService.editExercise(programmingExercise);
    }

    openOrionEditor(exercise: ProgrammingExercise) {
        try {
            this.router.navigate(['code-editor', 'ide', exercise.id, 'admin', exercise.templateParticipation?.id]);
        } catch (error) {
            this.orionConnectorService.log(error);
        }
    }
}
