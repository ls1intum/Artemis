import { Component } from '@angular/core';
import { NotificationSettingsComponent } from 'app/core/course/overview/course-settings/notification-settings/notification-settings.component';

@Component({
    selector: 'jhi-course-settings',
    imports: [NotificationSettingsComponent],
    templateUrl: './course-settings.component.html',
    styleUrl: './course-settings.component.scss',
})
export class CourseSettingsComponent {}
