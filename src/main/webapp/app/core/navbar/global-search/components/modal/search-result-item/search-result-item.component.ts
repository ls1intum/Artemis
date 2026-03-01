import { Component, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBook, faCalendarAlt, faTrophy } from '@fortawesome/free-solid-svg-icons';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { GlobalSearchResult } from '../../../services/global-search.service';

@Component({
    selector: 'jhi-global-search-result-item',
    standalone: true,
    imports: [FaIconComponent],
    templateUrl: './search-result-item.component.html',
    styleUrls: ['./search-result-item.component.scss'],
})
export class SearchResultItemComponent {
    // Inputs
    result = input.required<GlobalSearchResult>();
    icon = input.required<IconDefinition>();
    isSelected = input.required<boolean>();

    // Outputs
    resultClick = output<GlobalSearchResult>();

    // Icons
    protected readonly faBook = faBook;
    protected readonly faCalendarAlt = faCalendarAlt;
    protected readonly faTrophy = faTrophy;

    protected onClick() {
        this.resultClick.emit(this.result());
    }
}
