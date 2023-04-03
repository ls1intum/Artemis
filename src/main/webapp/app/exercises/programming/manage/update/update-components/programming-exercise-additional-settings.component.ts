import { Component, Input } from '@angular/core';
import { InfoStepInputs } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard.component';
import { ProgrammingExercise, ProjectType } from 'app/entities/programming-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-programming-exercise-additional-settings',
    templateUrl: './programming-exercise-additional-settings.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseAdditionalSettingsComponent {
    readonly ProjectType = ProjectType;

    @Input() selectedProjectType: ProjectType;
    @Input() validIdeSelection: () => boolean | undefined;

    @Input() isImport: boolean;
    @Input() isEdit: boolean;
    @Input() programmingExercise: ProgrammingExercise;

    @Input() shouldHidePreview = false;
    @Input() infoInputs: InfoStepInputs;

    faQuestionCircle = faQuestionCircle;
}
