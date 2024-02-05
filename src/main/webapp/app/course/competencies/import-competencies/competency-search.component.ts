import { Component, EventEmitter, Output } from '@angular/core';

export class CompetencySearch {
    courseTitleSearch: string;
    courseSemesterSearch: string;
    competencyDescriptionSearch: string;

    constructor(courseTitleSearch = '', courseSemesterSearch = '', competencyDescriptionSearch = '') {
        this.courseTitleSearch = courseTitleSearch;
        this.courseSemesterSearch = courseSemesterSearch;
        this.competencyDescriptionSearch = competencyDescriptionSearch;
    }
}

@Component({
    selector: 'jhi-competency-search',
    templateUrl: './competency-search.component.html',
})
export class CompetencySearchComponent {
    search: CompetencySearch = new CompetencySearch();

    @Output()
    searchEvent: EventEmitter<void>;

    isCollapsed = true;

    toggleCollapse() {
        this.isCollapsed = !this.isCollapsed;
    }

    constructor() {}

    /**
     * Resets all searches to default values
     */
    reset() {
        this.search = new CompetencySearch();
    }
    /**
     * Sends an updated filter through the event emitter
     * Triggered every time the user manually presses Enter or the search button
     */
    performSearch() {
        //TODO: emit with result
        this.searchEvent.emit();
    }
}
