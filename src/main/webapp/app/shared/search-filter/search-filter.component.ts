import { Component, EventEmitter, Output } from '@angular/core';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faMagnifyingGlass, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-search-filter',
    templateUrl: './search-filter.component.html',
    styleUrls: ['./search-filter.component.scss'],
    imports: [ArtemisSharedModule, FaIconComponent, FormsModule, ReactiveFormsModule],
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
