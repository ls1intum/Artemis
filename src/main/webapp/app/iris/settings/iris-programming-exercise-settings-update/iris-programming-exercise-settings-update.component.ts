import { Component, Input } from '@angular/core';
import { IrisSettingsType } from 'app/iris/settings/iris-settings-update/iris-settings-update.component';

@Component({
    selector: 'jhi-iris-programming-exercise-settings-update',
    templateUrl: './iris-programming-exercise-settings-update.component.html',
})
export class IrisProgrammingExerciseSettingsUpdateComponent {
    @Input()
    public programmingExerciseId?: number;

    PROGRAMMING_EXERCISE = IrisSettingsType.PROGRAMMING_EXERCISE;
}
