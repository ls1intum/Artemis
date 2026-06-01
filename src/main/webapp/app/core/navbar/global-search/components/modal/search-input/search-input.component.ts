import { Component, ElementRef, computed, inject, input, output, viewChild } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSearch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateService } from '@ngx-translate/core';
import { SearchEntityType } from '../../../models/searchable-entity.model';

@Component({
    selector: 'jhi-global-search-input',
    standalone: true,
    imports: [FaIconComponent, ArtemisTranslatePipe],
    templateUrl: './search-input.component.html',
    styleUrls: ['./search-input.component.scss'],
})
export class SearchInputComponent {
    /**
     * Maps API filter tags to translation keys for display in filter chips.
     */
    private static readonly FILTER_TAG_LABELS: Record<string, string> = {
        exercise: 'global.search.entities.exercisesTitle',
        lecture: 'global.search.entities.lecturesTitle',
        channel: 'global.search.entities.communicationTitle',
        faq: 'global.search.entities.faqsTitle',
        exam: 'global.search.entities.examsTitle',
    };

    /**
     * Communication-related filter types that are grouped under a single "Communication" chip.
     */
    private static readonly COMMUNICATION_FILTER_TYPES: Set<SearchEntityType> = new Set(['channel', 'post', 'answer_post']);

    private readonly translateService = inject(TranslateService);
    protected readonly faSearch = faSearch;
    protected readonly faTimes = faTimes;

    searchQuery = input.required<string>();
    activeFilters = input.required<SearchEntityType[]>();
    courseFilterLabel = input<string | undefined>(undefined);
    isLoading = input.required<boolean>();

    searchInput = output<string>();
    searchKeyDown = output<KeyboardEvent>();
    filterRemoved = output<SearchEntityType>();
    courseFilterRemoved = output<void>();
    /** Emitted when Backspace is pressed while the input is empty. */
    backspaceOnEmpty = output<void>();

    protected searchInputElement = viewChild<ElementRef<HTMLInputElement>>('searchInput');

    protected hasActiveFilters = computed(() => this.activeFilters().length > 0 || this.courseFilterLabel() !== undefined);

    /**
     * Collapses communication-related filters (channel, post, answer_post) into a single
     * "channel" entry for display, while keeping the underlying activeFilters intact for the API.
     */
    protected displayFilters = computed(() => {
        const filters = this.activeFilters();
        let hasCommunication = false;
        const result: SearchEntityType[] = [];
        for (const f of filters) {
            if (SearchInputComponent.COMMUNICATION_FILTER_TYPES.has(f)) {
                if (!hasCommunication) {
                    result.push('channel');
                    hasCommunication = true;
                }
            } else {
                result.push(f);
            }
        }
        return result;
    });

    focusInput() {
        setTimeout(() => {
            this.searchInputElement()?.nativeElement.focus();
        }, 0);
    }

    protected onInput(event: Event) {
        const query = (event.target as HTMLInputElement).value;
        this.searchInput.emit(query);
    }

    protected onKeyDown(event: KeyboardEvent) {
        // Detect backspace on empty input directly from the DOM element,
        // which is always up-to-date (unlike the signal that may lag during keydown).
        if (event.key === 'Backspace' && this.searchInputElement()?.nativeElement.value === '') {
            this.backspaceOnEmpty.emit();
        }
        this.searchKeyDown.emit(event);
    }

    protected onFilterRemove(filter: SearchEntityType) {
        this.filterRemoved.emit(filter);
    }

    protected onCourseFilterRemove() {
        this.courseFilterRemoved.emit();
    }

    protected getFilterLabel(filterTag: string): string {
        const translationKey = SearchInputComponent.FILTER_TAG_LABELS[filterTag];
        if (translationKey) {
            return this.translateService.instant(translationKey);
        }
        return filterTag;
    }
}
