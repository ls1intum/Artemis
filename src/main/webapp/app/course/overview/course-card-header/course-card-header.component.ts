import { Component, computed, input } from '@angular/core';
import { ImageComponent } from 'app/shared-ui/image/image.component';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { RouterModule } from '@angular/router';
import { CommonModule, SlicePipe } from '@angular/common';
import { getContrastingTextColor } from 'app/foundation/util/color.utils';
import { CourseNotificationBubbleComponent } from 'app/notification/course-notification/course-notification-bubble/course-notification-bubble.component';

@Component({
    selector: 'jhi-course-card-header',
    templateUrl: './course-card-header.component.html',
    styleUrls: ['./course-card-header.component.scss'],
    imports: [ImageComponent, RouterModule, SlicePipe, CommonModule, CourseNotificationBubbleComponent],
})
export class CourseCardHeaderComponent {
    protected readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    courseIcon = input.required<string>();
    courseTitle = input.required<string>();
    courseColor = input.required<string>();
    courseId = input.required<number>();
    courseNotificationCount = input<number>(0);
    archiveMode = input<boolean>(false);

    readonly color = computed(() => this.courseColor() || this.ARTEMIS_DEFAULT_COLOR);
    readonly titleColor = computed(() => getContrastingTextColor(this.color()));
}
