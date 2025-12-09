import { Component, model } from '@angular/core';
import { faChevronDown, faChevronUp } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';
import { getSemesters } from 'app/shared/util/semester-utils';
import { CourseCompetencyFilter } from 'app/shared/table/pageable-table';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-competency-search',
    templateUrl: './competency-search.component.html',
    imports: [TranslateDirective, FormsModule, NgbCollapse, ButtonComponent, FaIconComponent],
})
export class CompetencySearchComponent {
    search = model.required<CourseCompetencyFilter>();

    advancedSearchEnabled = false;

    protected readonly faChevronDown = faChevronDown;
    protected readonly faChevronUp = faChevronUp;

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
        this.search.set({
            title: '',
            description: '',
            courseTitle: '',
            semester: '',
        });
    }

    /**
     * Update a single field on the search model. Used by template event bindings.
     */
    updateSearchField(field: 'title' | 'description' | 'courseTitle' | 'semester', value: string) {
        this.search.update((s) => Object.assign({}, s, { [field]: value }) as CourseCompetencyFilter);
    }

    /**
     * Sends an updated filter through the event emitter
     * Triggered every time the user manually presses Enter or the search button
     */
    performSearch() {
        if (this.advancedSearchEnabled) {
            this.search.update((s) => Object.assign({}, s));
        } else {
            //only search with competency title if advancedSearch is disabled
            this.search.set({
                title: this.search().title,
                description: '',
                courseTitle: '',
                semester: '',
            });
        }
    }
}
