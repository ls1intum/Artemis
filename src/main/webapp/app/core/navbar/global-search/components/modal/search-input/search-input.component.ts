import { Component, ElementRef, computed, input, output, viewChild } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSearch } from '@fortawesome/free-solid-svg-icons';
import { ChipModule } from 'primeng/chip';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-global-search-input',
    standalone: true,
    imports: [FaIconComponent, ChipModule, ArtemisTranslatePipe],
    templateUrl: './search-input.component.html',
    styleUrls: ['./search-input.component.scss'],
})
export class SearchInputComponent {
    // Inputs
    searchQuery = input.required<string>();
    activeFilters = input.required<string[]>();
    isLoading = input.required<boolean>();

    // Outputs
    searchInput = output<string>();
    searchKeyDown = output<KeyboardEvent>();
    filterRemoved = output<string>();

    // Icons
    protected readonly faSearch = faSearch;

    // View child
    protected searchInputElement = viewChild<ElementRef<HTMLInputElement>>('searchInput');

    // Computed
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

    protected onFilterRemove(filter: string) {
        this.filterRemoved.emit(filter);
    }
}
