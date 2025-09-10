import { Component, effect, input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'jhi-course-notification-bubble',
    imports: [CommonModule],
    templateUrl: './course-notification-bubble.component.html',
    styleUrls: ['./course-notification-bubble.component.scss'],
})
export class CourseNotificationBubbleComponent {
    totalAmount = input.required<number>();
    isSmall = input<boolean>(false);

    shownAmount = 0;

    constructor() {
        effect(() => {
            this.shownAmount = Math.min(99, this.totalAmount());
        });
    }
}
