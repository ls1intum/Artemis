import { Component, output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { faMagnifyingGlass, faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-search',
    templateUrl: './search.component.html',
    styleUrls: ['./search.component.scss'],
    standalone: true,
    imports: [ReactiveFormsModule, ArtemisSharedModule],
})
export class SearchComponent {
    faMagnifyingGlass = faMagnifyingGlass;
    faTimes = faTimes;

    searchValue: string;
    newSearchEvent = output<string>();

    filterForm: FormGroup = new FormGroup({
        searchFilter: new FormControl<string>(''),
    });

    setSearchValue(searchValue: string) {
        this.searchValue = searchValue;
    }

    applySearch(searchValue: string) {
        this.newSearchEvent.emit(searchValue);
    }
}
