import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { ProgrammingExercise, ProjectType } from 'app/entities/programming-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { TeamConfigFormGroupComponent } from 'app/exercises/shared/team-config-form-group/team-config-form-group.component';

@Component({
    selector: 'jhi-programming-exercise-difficulty',
    templateUrl: './programming-exercise-difficulty.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseDifficultyComponent {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;
    @ViewChild(TeamConfigFormGroupComponent) teamConfigComponent: TeamConfigFormGroupComponent;

    @Output() triggerValidation = new EventEmitter<void>();

    protected readonly ProjectType = ProjectType;

    faQuestionCircle = faQuestionCircle;
}
