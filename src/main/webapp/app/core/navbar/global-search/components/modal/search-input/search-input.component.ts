import { Component, ElementRef, computed, input, output, viewChild } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSearch } from '@fortawesome/free-solid-svg-icons';
import { ChipModule } from 'primeng/chip';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { SearchEntityType } from '../../../models/searchable-entity.model';

@Component({
    selector: 'jhi-global-search-input',
    standalone: true,
    imports: [FaIconComponent, ChipModule, ArtemisTranslatePipe],
    templateUrl: './search-input.component.html',
})
export class SearchInputComponent {
    protected readonly faSearch = faSearch;

    searchQuery = input.required<string>();
    activeFilters = input.required<SearchEntityType[]>();
    isLoading = input.required<boolean>();

    searchInput = output<string>();
    searchKeyDown = output<KeyboardEvent>();
    filterRemoved = output<SearchEntityType>();

    protected searchInputElement = viewChild<ElementRef<HTMLInputElement>>('searchInput');

    protected hasActiveFilters = computed(() => this.activeFilters().length > 0);

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
        this.searchKeyDown.emit(event);
    }

    protected onFilterRemove(filter: SearchEntityType) {
        this.filterRemoved.emit(filter);
    }
}
