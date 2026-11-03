import { Component } from '@angular/core';
import { IdeSettingsComponent } from 'app/account/user/settings/ide-preferences/ide-settings.component';
import { QuizTrainingSettingsComponent } from 'app/account/user/settings/quiz-training-settings/quiz-training-settings.component';

@Component({
    selector: 'jhi-exercise-settings',
    templateUrl: './exercise-settings.component.html',
    imports: [IdeSettingsComponent, QuizTrainingSettingsComponent],
})
export class ExerciseSettingsComponent {}
