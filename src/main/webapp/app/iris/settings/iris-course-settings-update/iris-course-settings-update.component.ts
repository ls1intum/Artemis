import { Component, Input } from '@angular/core';
import { IrisSettingsType } from 'app/iris/settings/iris-settings-update/iris-settings-update.component';

@Component({
    selector: 'jhi-iris-course-settings-update',
    templateUrl: './iris-course-settings-update.component.html',
})
export class IrisCourseSettingsUpdateComponent {
    @Input()
    courseId?: number;

    COURSE = IrisSettingsType.COURSE;
}
