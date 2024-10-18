import { Component, Input, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { IrisSettingsType } from 'app/entities/iris/settings/iris-settings.model';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { IrisSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-settings-update.component';

@Component({
    selector: 'jhi-iris-course-settings-update',
    templateUrl: './iris-course-settings-update.component.html',
})
export class IrisCourseSettingsUpdateComponent implements OnInit, ComponentCanDeactivate {
    private route = inject(ActivatedRoute);

    @ViewChild(IrisSettingsUpdateComponent)
    settingsUpdateComponent?: IrisSettingsUpdateComponent;

    @Input()
    courseId?: number;

    COURSE = IrisSettingsType.COURSE;

    ngOnInit(): void {
        this.route.parent?.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
        });
    }

    canDeactivate(): boolean {
        return this.settingsUpdateComponent?.canDeactivate() ?? true;
    }

    get canDeactivateWarning(): string | undefined {
        return this.settingsUpdateComponent?.canDeactivateWarning;
    }
}
