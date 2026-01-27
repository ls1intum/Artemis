import { Component, input } from '@angular/core';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { CourseTutorialGroupDetailSessionStatusIndicatorComponent } from 'app/tutorialgroup/overview/course-tutorial-group-detail-session-status-indicator/course-tutorial-group-detail-session-status-indicator.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-tutorial-registrations',
    imports: [IconFieldModule, InputIconModule, InputTextModule, ButtonModule, CourseTutorialGroupDetailSessionStatusIndicatorComponent, TranslateDirective],
    templateUrl: './tutorial-registrations.component.html',
    styleUrl: './tutorial-registrations.component.scss',
})
export class TutorialRegistrationsComponent {
    courseId = input.required<number>();
    tutorialGroupId = input.required<number>();
}
