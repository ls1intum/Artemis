import { Component } from '@angular/core';
import { IdeSettingsComponent } from 'app/account/user/settings/ide-preferences/ide-settings.component';
import { QuizTrainingSettingsComponent } from 'app/account/user/settings/quiz-training-settings/quiz-training-settings.component';

@Component({
    selector: 'jhi-exercise-settings-v2',
    templateUrl: './exercise-settings-v2.component.html',
    imports: [IdeSettingsComponent, QuizTrainingSettingsComponent],
})
export class ExerciseSettingsV2Component {}
