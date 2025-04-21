import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faChevronDown, faChevronUp } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button/button.component';
import { getSemesters } from 'app/shared/util/semester-utils';
import { CourseCompetencyFilter } from 'app/shared/table/pageable-table';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent } from 'app/shared/components/button/button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-competency-search',
    templateUrl: './competency-search.component.html',
    imports: [TranslateDirective, FormsModule, NgbCollapse, ButtonComponent, FaIconComponent],
})
export class CompetencySearchComponent {
    @Input() search: CourseCompetencyFilter;
    @Output() searchChange = new EventEmitter<CourseCompetencyFilter>();

    advancedSearchEnabled = false;

    //Icons
    protected readonly faChevronDown = faChevronDown;
    protected readonly faChevronUp = faChevronUp;

    //Other constants for HTML
    protected readonly ButtonType = ButtonType;
    protected readonly semesters = getSemesters();

    /**
     * Toggles advanced search (expands component to show more search fields)
     */
    toggleAdvancedSearch() {
        this.advancedSearchEnabled = !this.advancedSearchEnabled;
    }

    /**
     * Resets all filters to default values
     */
    reset() {
        this.search = {
            title: '',
            description: '',
            courseTitle: '',
            semester: '',
        };
    }

    /**
     * Sends an updated filter through the event emitter
     * Triggered every time the user manually presses Enter or the search button
     */
    performSearch() {
        if (this.advancedSearchEnabled) {
            this.searchChange.emit(this.search);
        } else {
            //only search with competency title if advancedSearch is disabled
            this.searchChange.emit({
                title: this.search.title,
                description: '',
                courseTitle: '',
                semester: '',
            });
        }
    }
}
