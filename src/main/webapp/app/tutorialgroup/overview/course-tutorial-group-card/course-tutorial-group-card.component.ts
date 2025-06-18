import { ChangeDetectionStrategy, Component, Input, input } from '@angular/core';
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
    // TODO: Skipped for migration because:
    //  This input is used in a control flow expression (e.g. `@if` or `*ngIf`)
    //  and migrating would break narrowing currently.
    @Input()
    course: Course;
    // TODO: Skipped for migration because:
    //  This input is used in a control flow expression (e.g. `@if` or `*ngIf`)
    //  and migrating would break narrowing currently.
    @Input()
    tutorialGroup: TutorialGroup;

    readonly showChannelLink = input(false);

    // icons
    faPersonChalkboard = faPersonChalkboard;

    readonly isMessagingEnabled = isMessagingEnabled;
}
