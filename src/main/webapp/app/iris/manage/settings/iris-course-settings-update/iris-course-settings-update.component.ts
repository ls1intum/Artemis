import { Component, Input, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { IrisSettingsUpdateComponent } from 'app/iris/manage/settings/iris-settings-update/iris-settings-update.component';
import { CourseTitleBarTitleComponent } from 'app/core/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseTitleBarTitleDirective } from 'app/core/course/shared/directives/course-title-bar-title.directive';

/**
 * Wrapper component for course-level Iris settings.
 * Extracts the courseId from the route and passes it to the settings component.
 */
@Component({
    selector: 'jhi-iris-course-settings-update',
    templateUrl: './iris-course-settings-update.component.html',
    imports: [IrisSettingsUpdateComponent, CourseTitleBarTitleComponent, CourseTitleBarTitleDirective],
})
export class IrisCourseSettingsUpdateComponent implements OnInit, ComponentCanDeactivate {
    private route = inject(ActivatedRoute);

    @ViewChild(IrisSettingsUpdateComponent) settingsUpdateComponent?: IrisSettingsUpdateComponent;

    @Input() courseId?: number;

    ngOnInit(): void {
        this.route?.params.subscribe((params) => {
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
