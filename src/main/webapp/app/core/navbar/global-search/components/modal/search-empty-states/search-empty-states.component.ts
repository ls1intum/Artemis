import { Component, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSearch } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

export type SearchStateType = 'loading' | 'empty' | 'fallback';

@Component({
    selector: 'jhi-global-search-empty-states',
    standalone: true,
    imports: [FaIconComponent, ArtemisTranslatePipe],
    templateUrl: './search-empty-states.component.html',
})
export class SearchEmptyStatesComponent {
    protected readonly faSearch = faSearch;

    stateType = input.required<SearchStateType>();
}
