import { Component, input, output, signal } from '@angular/core';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-search-filter',
    templateUrl: './search-filter.component.html',
    imports: [IconFieldModule, InputIconModule, InputTextModule, ArtemisTranslatePipe],
})
export class SearchFilterComponent {
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
