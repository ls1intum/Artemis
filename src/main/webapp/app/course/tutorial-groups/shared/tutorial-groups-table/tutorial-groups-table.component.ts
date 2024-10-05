import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ContentChild, Input, OnChanges, SimpleChanges, TemplateRef, inject } from '@angular/core';
import { faQuestionCircle, faSort } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { SortService } from 'app/shared/service/sort.service';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-tutorial-groups-table',
    templateUrl: './tutorial-groups-table.component.html',
    styleUrls: ['./tutorial-groups-table.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TutorialGroupsTableComponent implements OnChanges {
    private sortService = inject(SortService);
    private cdr = inject(ChangeDetectorRef);

    @ContentChild(TemplateRef, { static: true }) extraColumn: TemplateRef<any>;

    @Input()
    showIdColumn = false;

    @Input()
    showChannelColumn = false;

    @Input()
    tutorialGroups: TutorialGroup[] = [];

    @Input()
    course: Course;

    @Input()
    timeZone?: string = undefined;

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

    trackId(index: number, item: TutorialGroup) {
        return item.id;
    }

    sortRows() {
        if (this.sortingPredicate === 'dayAndTime') {
            this.sortService.sortByMultipleProperties(this.tutorialGroups, ['tutorialGroupSchedule.dayOfWeek', 'tutorialGroupSchedule.startTime'], this.ascending);
        } else if (this.sortingPredicate === 'capacityAndRegistrations') {
            this.sortService.sortByMultipleProperties(this.tutorialGroups, ['capacity', 'numberOfRegisteredUsers'], this.ascending);
        } else {
            this.sortService.sortByProperty(this.tutorialGroups, this.sortingPredicate, this.ascending);
        }
    }

    ngOnChanges(changes: SimpleChanges) {
        for (const propName in changes) {
            if (changes.hasOwnProperty(propName)) {
                const change = changes[propName];
                switch (propName) {
                    case 'timeZone': {
                        if (change.currentValue) {
                            this.timeZoneUsedForDisplay = change.currentValue;
                            this.cdr.detectChanges();
                        }
                        break;
                    }
                    case 'tutorialGroups':
                        {
                            if (change.currentValue && change.currentValue.length > 0) {
                                this.tutorialGroupsSplitAcrossMultipleCampuses = this.tutorialGroups.some(
                                    (tutorialGroup) => tutorialGroup.campus !== this.tutorialGroups[0].campus,
                                );
                                this.mixOfOfflineAndOfflineTutorialGroups = this.tutorialGroups.some((tutorialGroup) => tutorialGroup.isOnline !== this.tutorialGroups[0].isOnline);
                                this.mifOfDifferentLanguages = this.tutorialGroups.some((tutorialGroup) => tutorialGroup.language !== this.tutorialGroups[0].language);
                                this.cdr.detectChanges();
                            }
                        }
                        break;
                }
            }
        }
    }
}
