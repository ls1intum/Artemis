import { Component, EventEmitter, Output } from '@angular/core';
import { faChevronDown, faChevronUp } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button.component';

export class CompetencyFilter {
    competencyTitleSearch: string;
    competencyDescriptionSearch: string;
    courseTitleSearch: string;
    courseSemesterSearch: string;

    constructor(competencyTitleSearch = '', competencyDescriptionSearch = '', courseTitleSearch = '', courseSemesterSearch = '') {
        this.competencyTitleSearch = competencyTitleSearch;
        this.competencyDescriptionSearch = competencyDescriptionSearch;
        this.courseTitleSearch = courseTitleSearch;
        this.courseSemesterSearch = courseSemesterSearch;
    }
}

@Component({
    selector: 'jhi-competency-search',
    templateUrl: './competency-search.component.html',
})
export class CompetencySearchComponent {
    @Output() search = new EventEmitter<CompetencyFilter>();

    filter: CompetencyFilter = new CompetencyFilter();
    advancedSearchEnabled = false;

    //Icons
    protected readonly faChevronDown = faChevronDown;
    protected readonly faChevronUp = faChevronUp;

    //Other constants
    protected readonly ButtonType = ButtonType;

    toggleAdvancedSearch() {
        this.advancedSearchEnabled = !this.advancedSearchEnabled;
    }

    constructor() {}

    /**
     * Resets all filters to default values
     */
    reset() {
        this.filter = new CompetencyFilter();
    }
    /**
     * Sends an updated filter through the event emitter
     * Triggered every time the user manually presses Enter or the search button
     */
    performSearch() {
        if (this.advancedSearchEnabled) {
            this.search.emit(this.filter);
        } else {
            //only search with competency title if advancedSearch is disabled
            this.search.emit(new CompetencyFilter(this.filter.competencyTitleSearch));
        }
    }
}
