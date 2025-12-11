import { Component, Input, ViewChild } from '@angular/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/programming/manage/update/update-components/custom-build-plans/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';

@Component({
    selector: 'jhi-programming-exercise-custom-build-plan',
    templateUrl: './programming-exercise-custom-build-plan.component.html',
    styleUrls: ['../../../../shared/programming-exercise-form.scss'],
    imports: [FormsModule, TranslateDirective, HelpIconComponent, ProgrammingExerciseBuildConfigurationComponent],
})
export class ProgrammingExerciseCustomBuildPlanComponent {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;
    @ViewChild(ProgrammingExerciseBuildConfigurationComponent) programmingExerciseDockerImageComponent?: ProgrammingExerciseBuildConfigurationComponent;
}
