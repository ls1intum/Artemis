import { Component, input, output, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faMagnifyingGlass, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-search-filter',
    templateUrl: './search-filter.component.html',
    imports: [FaIconComponent, ArtemisTranslatePipe],
})
export class SearchFilterComponent {
    readonly faMagnifyingGlass = faMagnifyingGlass;
    readonly faTimes = faTimes;

    readonly placeholderKey = input<string>('artemisApp.course.exercise.search.searchPlaceholder');
    readonly newSearchEvent = output<string>();

    readonly searchValue = signal('');

    setSearchValue(value: string) {
        this.searchValue.set(value);
        this.newSearchEvent.emit(value);
    }

    resetSearchValue() {
        this.searchValue.set('');
        this.newSearchEvent.emit('');
    }
}
