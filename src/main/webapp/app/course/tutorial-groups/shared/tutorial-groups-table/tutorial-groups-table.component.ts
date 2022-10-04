import { Component, ContentChild, EventEmitter, Input, OnInit, Output, TemplateRef } from '@angular/core';
import { faSort } from '@fortawesome/free-solid-svg-icons';
import { Language } from 'app/entities/course.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-tutorial-groups-table',
    templateUrl: './tutorial-groups-table.component.html',
    styleUrls: ['./tutorial-groups-table.component.scss'],
})
export class TutorialGroupsTableComponent implements OnInit {
    @Input()
    tutorialGroups: TutorialGroup[] = [];

    @Input()
    courseId: number;

    // emits the id of the selected tutorial group
    @Output()
    tutorialGroupSelected = new EventEmitter<number>();

    @ContentChild(TemplateRef) extraColumn: TemplateRef<any>;

    sortingPredicate = 'title';
    ascending = true;
    faSort = faSort;
    GERMAN = Language.GERMAN;
    ENGLISH = Language.ENGLISH;

    constructor(private sortService: SortService) {}

    ngOnInit(): void {}
    trackId(index: number, item: TutorialGroup) {
        return item.id;
    }

    sortRows() {
        this.sortService.sortByProperty(this.tutorialGroups, this.sortingPredicate, this.ascending);
    }
}
