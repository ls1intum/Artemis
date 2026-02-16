import { ChangeDetectionStrategy, Component, HostBinding, TemplateRef, ViewEncapsulation, input } from '@angular/core';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { RouterLink } from '@angular/router';
import { TutorialGroupUtilizationIndicatorComponent } from 'app/tutorialgroup/manage/tutorial-group-utilization-indicator/tutorial-group-utilization-indicator.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgTemplateOutlet } from '@angular/common';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MeetingPatternPipe } from 'app/tutorialgroup/shared/pipe/meeting-pattern.pipe';

@Component({
    selector: '[jhi-tutorial-group-row]',
    templateUrl: './tutorial-group-row.component.html',
    styleUrls: ['./tutorial-group-row.component.scss'],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [RouterLink, TutorialGroupUtilizationIndicatorComponent, TranslateDirective, NgTemplateOutlet, ArtemisDatePipe, ArtemisTranslatePipe, MeetingPatternPipe],
})
export class TutorialGroupRowComponent {
    readonly Math = Math;
    @HostBinding('class') class = 'tutorial-group-row';

    readonly showIdColumn = input(false);

    readonly tutorialGroupsSplitAcrossMultipleCampuses = input(false);
    readonly mixOfOfflineAndOfflineTutorialGroups = input(false);

    readonly mifOfDifferentLanguages = input(false);

    readonly showChannelColumn = input(false);

    readonly extraColumn = input<TemplateRef<any>>();

    readonly tutorialGroup = input.required<TutorialGroup>();

    readonly course = input<Course>();

    readonly timeZone = input<string>();
}
