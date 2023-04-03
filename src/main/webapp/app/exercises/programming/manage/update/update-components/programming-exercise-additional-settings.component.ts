import { Component, Input } from '@angular/core';
import { ProjectType } from 'app/entities/programming-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseUpdateService } from 'app/exercises/programming/manage/update/programming-exercise-update.service';

@Component({
    selector: 'jhi-programming-exercise-additional-settings',
    templateUrl: './programming-exercise-additional-settings.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseAdditionalSettingsComponent {
    readonly ProjectType = ProjectType;

    @Input() selectedProjectType: ProjectType;
    @Input() validIdeSelection: () => boolean | undefined;

    @Input() isEdit: boolean;

    @Input() shouldHidePreview = false;

    faQuestionCircle = faQuestionCircle;

    constructor(public programmingExerciseUpdateService: ProgrammingExerciseUpdateService) {}
}
