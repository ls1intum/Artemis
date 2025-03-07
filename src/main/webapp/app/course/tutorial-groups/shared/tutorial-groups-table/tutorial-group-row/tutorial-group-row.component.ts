import { ChangeDetectionStrategy, Component, HostBinding, TemplateRef, ViewEncapsulation, input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Course } from 'app/entities/course.model';
import { RouterLink } from '@angular/router';
import { TutorialGroupUtilizationIndicatorComponent } from '../../tutorial-group-utilization-indicator/tutorial-group-utilization-indicator.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgTemplateOutlet } from '@angular/common';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MeetingPatternPipe } from 'app/course/tutorial-groups/shared/meeting-pattern.pipe';

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

    showIdColumn = input(false);

    /**
     * If true we show the campus column
     */
    tutorialGroupsSplitAcrossMultipleCampuses = input(false);
    /**
     * If true we show the online / offline column
     */
    mixOfOfflineAndOfflineTutorialGroups = input(false);

    /**
     * If true we show the language column
     */
    mifOfDifferentLanguages = input(false);

    showChannelColumn = input(false);

    extraColumn = input<TemplateRef<any>>();

    tutorialGroup = input<TutorialGroup>();

    course = input<Course>();

    timeZone = input<string | undefined>(undefined);
}
