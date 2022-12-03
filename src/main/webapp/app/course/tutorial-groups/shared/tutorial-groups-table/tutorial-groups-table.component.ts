import { Component, ContentChild, Input, OnChanges, SimpleChanges, TemplateRef } from '@angular/core';
import { faSort } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { SortService } from 'app/shared/service/sort.service';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-tutorial-groups-table',
    templateUrl: './tutorial-groups-table.component.html',
    styleUrls: ['./tutorial-groups-table.component.scss'],
})
export class TutorialGroupsTableComponent implements OnChanges {
    @ContentChild(TemplateRef) extraColumn: TemplateRef<any>;

    @Input()
    showIdColumn = false;

    @Input()
    tutorialGroups: TutorialGroup[] = [];

    @Input()
    course: Course;

    @Input()
    tutorialGroupClickHandler: (tutorialGroup: TutorialGroup) => void;

    @Input()
    timeZone?: string = undefined;

    timeZoneUsedForDisplay = dayjs.tz.guess();

    sortingPredicate = 'title';
    ascending = true;
    faSort = faSort;

    constructor(private sortService: SortService) {}

    trackId(index: number, item: TutorialGroup) {
        return item.id;
    }

    sortRows() {
        this.sortService.sortByProperty(this.tutorialGroups, this.sortingPredicate, this.ascending);
    }

    ngOnChanges(changes: SimpleChanges) {
        for (const propName in changes) {
            if (changes.hasOwnProperty(propName)) {
                const change = changes[propName];
                switch (propName) {
                    case 'timeZone': {
                        if (change.currentValue) {
                            this.timeZoneUsedForDisplay = change.currentValue;
                        }
                        break;
                    }
                }
            }
        }
    }
}
