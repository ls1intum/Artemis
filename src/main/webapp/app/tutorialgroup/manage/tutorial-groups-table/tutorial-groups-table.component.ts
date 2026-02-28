import { ChangeDetectionStrategy, Component, TemplateRef, contentChild, effect, inject, input } from '@angular/core';
import { faQuestionCircle, faSort } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { SortService } from 'app/shared/service/sort.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { RouterLink } from '@angular/router';
import { TutorialGroupRowComponent } from './tutorial-group-row/tutorial-group-row.component';
import { NgClass } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-tutorial-groups-table',
    templateUrl: './tutorial-groups-table.component.html',
    styleUrls: ['./tutorial-groups-table.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, SortDirective, SortByDirective, FaIconComponent, NgbTooltip, RouterLink, TutorialGroupRowComponent, NgClass, ArtemisTranslatePipe],
})
export class TutorialGroupsTableComponent {
    private sortService = inject(SortService);

    readonly extraColumn = contentChild(TemplateRef);

    readonly showIdColumn = input(false);

    readonly showChannelColumn = input(false);

    readonly tutorialGroups = input<TutorialGroup[]>([]);

    readonly course = input.required<Course>();

    readonly timeZone = input<string>();

    timeZoneUsedForDisplay = dayjs.tz.guess();

    sortingPredicate = 'title';
    ascending = true;
    faSort = faSort;
    faQuestionCircle = faQuestionCircle;

    /**
     * If true we show the campus column
     */
    tutorialGroupsSplitAcrossMultipleCampuses = false;
    /**
     * If true we show the online / offline column
     */
    mixOfOfflineAndOfflineTutorialGroups = false;

    /**
     * If true we show the language column
     */
    mifOfDifferentLanguages = false;

    constructor() {
        // Effect to update timeZone
        effect(() => {
            const tz = this.timeZone();
            if (tz) {
                this.timeZoneUsedForDisplay = tz;
            }
        });

        // Effect to update tutorial groups display properties
        effect(() => {
            const groups = this.tutorialGroups();
            if (groups && groups.length > 0) {
                this.tutorialGroupsSplitAcrossMultipleCampuses = groups.some((tutorialGroup) => tutorialGroup.campus !== groups[0].campus);
                this.mixOfOfflineAndOfflineTutorialGroups = groups.some((tutorialGroup) => tutorialGroup.isOnline !== groups[0].isOnline);
                this.mifOfDifferentLanguages = groups.some((tutorialGroup) => tutorialGroup.language !== groups[0].language);
            }
        });
    }

    trackId(index: number, item: TutorialGroup) {
        return item.id;
    }

    sortRows() {
        if (this.sortingPredicate === 'dayAndTime') {
            this.sortService.sortByMultipleProperties(this.tutorialGroups(), ['tutorialGroupSchedule.dayOfWeek', 'tutorialGroupSchedule.startTime'], this.ascending);
        } else if (this.sortingPredicate === 'capacityAndRegistrations') {
            this.sortService.sortByMultipleProperties(this.tutorialGroups(), ['capacity', 'numberOfRegisteredUsers'], this.ascending);
        } else {
            this.sortService.sortByProperty(this.tutorialGroups(), this.sortingPredicate, this.ascending);
        }
    }
}
