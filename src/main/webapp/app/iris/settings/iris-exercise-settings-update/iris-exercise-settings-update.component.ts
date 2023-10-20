import { Component, Input } from '@angular/core';
import { IrisSettingsType } from 'app/entities/iris/settings/iris-settings.model';

@Component({
    selector: 'jhi-iris-exercise-settings-update',
    templateUrl: './iris-exercise-settings-update.component.html',
})
export class IrisExerciseSettingsUpdateComponent {
    @Input()
    public courseId?: number;
    @Input()
    public exerciseId?: number;

    EXERCISE = IrisSettingsType.EXERCISE;
}
