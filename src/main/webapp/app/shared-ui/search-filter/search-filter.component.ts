import { Component, input, output, signal } from '@angular/core';
import { TumUiSearchFieldComponent } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-search-filter',
    templateUrl: './search-filter.component.html',
    styleUrls: ['./search-filter.component.scss'],
    imports: [TumUiSearchFieldComponent],
})
export class SearchFilterComponent {
    readonly placeholderKey = input<string>('artemisApp.course.exercise.search.searchPlaceholder');
    readonly disabled = input(false);
    readonly newSearchEvent = output<string>();

    readonly searchValue = signal('');

    setSearchValue(value: string) {
        this.searchValue.set(value);
        this.newSearchEvent.emit(value);
    }

    resetSearchValue() {
        this.setSearchValue('');
    }
}
