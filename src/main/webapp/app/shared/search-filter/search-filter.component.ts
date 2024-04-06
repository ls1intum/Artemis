import { Component, EventEmitter, Output } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { faMagnifyingGlass, faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-search-filter',
    templateUrl: './search-filter.component.html',
    styleUrls: ['./search-filter.component.scss'],
})
export class SearchFilterComponent {
    faMagnifyingGlass = faMagnifyingGlass;
    faTimes = faTimes;

    @Output() newSearchEvent = new EventEmitter<string>();

    filterForm: FormGroup = new FormGroup({
        searchFilter: new FormControl<string>(''),
    });

    setSearchValue(searchValue: string) {
        this.newSearchEvent.emit(searchValue);
    }
    resetSearchValue() {
        this.filterForm.get('searchFilter')?.reset();
        this.newSearchEvent.emit('');
    }
}
