import { ChangeDetectionStrategy, Component, HostBinding, Input, TemplateRef, ViewEncapsulation } from '@angular/core';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { Course } from 'app/entities/course.model';
import { RouterLink } from '@angular/router';
import { TutorialGroupUtilizationIndicatorComponent } from '../../tutorial-group-utilization-indicator/tutorial-group-utilization-indicator.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgTemplateOutlet } from '@angular/common';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MeetingPatternPipe } from 'app/tutorialgroup/shared/meeting-pattern.pipe';

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

    @Input()
    showIdColumn = false;

    /**
     * If true we show the campus column
     */
    @Input()
    tutorialGroupsSplitAcrossMultipleCampuses = false;
    /**
     * If true we show the online / offline column
     */
    @Input()
    mixOfOfflineAndOfflineTutorialGroups = false;

    /**
     * If true we show the language column
     */
    @Input()
    mifOfDifferentLanguages = false;

    @Input()
    showChannelColumn = false;

    @Input() extraColumn: TemplateRef<any>;

    @Input() tutorialGroup: TutorialGroup;

    @Input() course: Course;

    @Input() timeZone?: string = undefined;
}
