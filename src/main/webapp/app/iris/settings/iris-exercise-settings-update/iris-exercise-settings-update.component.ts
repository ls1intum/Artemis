import { Component, Input, OnInit, ViewChild, inject } from '@angular/core';
import { IrisSettingsType } from 'app/entities/iris/settings/iris-settings.model';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { IrisSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-settings-update.component';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-iris-exercise-settings-update',
    templateUrl: './iris-exercise-settings-update.component.html',
})
export class IrisExerciseSettingsUpdateComponent implements OnInit, ComponentCanDeactivate {
    private route = inject(ActivatedRoute);

    @ViewChild(IrisSettingsUpdateComponent)
    settingsUpdateComponent?: IrisSettingsUpdateComponent;

    @Input()
    public courseId?: number;
    @Input()
    public exerciseId?: number;

    EXERCISE = IrisSettingsType.EXERCISE;

    ngOnInit(): void {
        this.route.parent?.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            this.exerciseId = Number(params['exerciseId']);
        });
    }

    canDeactivate(): boolean {
        return this.settingsUpdateComponent?.canDeactivate() ?? true;
    }

    get canDeactivateWarning(): string | undefined {
        return this.settingsUpdateComponent?.canDeactivateWarning;
    }
}
