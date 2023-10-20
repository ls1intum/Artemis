import { Component, Input } from '@angular/core';
import { IrisSettingsType } from 'app/entities/iris/settings/iris-settings.model';

@Component({
    selector: 'jhi-iris-course-settings-update',
    templateUrl: './iris-course-settings-update.component.html',
})
export class IrisCourseSettingsUpdateComponent {
    @Input()
    courseId?: number;

    COURSE = IrisSettingsType.COURSE;
}
