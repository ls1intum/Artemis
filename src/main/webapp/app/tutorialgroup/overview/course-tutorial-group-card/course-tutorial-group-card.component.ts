import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { faPersonChalkboard } from '@fortawesome/free-solid-svg-icons';
import { Course, isMessagingEnabled } from 'app/core/course/shared/entities/course.model';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

@Component({
    selector: 'jhi-course-tutorial-group-card',
    templateUrl: './course-tutorial-group-card.component.html',
    styleUrls: ['./course-tutorial-group-card.component.scss'],
    host: { class: 'card tutorial-group-card' },
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [RouterLink, FaIconComponent, TranslateDirective, ArtemisDatePipe],
})
export class CourseTutorialGroupCardComponent {
    readonly course = input.required<Course>();
    readonly tutorialGroup = input.required<TutorialGroup>();

    readonly showChannelLink = input(false);

    // icons
    faPersonChalkboard = faPersonChalkboard;

    readonly isMessagingEnabled = isMessagingEnabled;
}
