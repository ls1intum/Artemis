import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { faPersonChalkboard } from '@fortawesome/free-solid-svg-icons';
import { Course, isMessagingEnabled } from 'app/entities/course.model';

@Component({
    selector: 'jhi-course-tutorial-group-card',
    templateUrl: './course-tutorial-group-card.component.html',
    styleUrls: ['./course-tutorial-group-card.component.scss'],
    // eslint-disable-next-line @angular-eslint/no-host-metadata-property
    host: { class: 'card tutorial-group-card' },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseTutorialGroupCardComponent {
    @Input()
    course: Course;
    @Input()
    tutorialGroup: TutorialGroup;

    @Input()
    showChannelLink = false;

    // icons
    faPersonChalkboard = faPersonChalkboard;

    readonly isMessagingEnabled = isMessagingEnabled;
}
