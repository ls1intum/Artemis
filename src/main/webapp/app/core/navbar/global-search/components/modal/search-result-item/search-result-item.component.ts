import { Component, computed, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBook, faCalendarAlt, faLevelDownAlt, faTrophy } from '@fortawesome/free-solid-svg-icons';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { GlobalSearchResult } from '../../../services/global-search.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-global-search-result-item',
    standalone: true,
    imports: [FaIconComponent, ArtemisTranslatePipe],
    templateUrl: './search-result-item.component.html',
    styleUrls: ['./search-result-item.component.scss'],
})
export class SearchResultItemComponent {
    protected readonly faBook = faBook;
    protected readonly faCalendarAlt = faCalendarAlt;
    protected readonly faTrophy = faTrophy;
    protected readonly faLevelDownAlt = faLevelDownAlt;

    result = input.required<GlobalSearchResult>();
    icon = input.required<IconDefinition>();
    isSelected = input.required<boolean>();

    resultClick = output<GlobalSearchResult>();

    protected formattedDueDate = computed(() => {
        const dueDate = this.result().metadata?.['dueDate'];
        if (!dueDate) {
            return '';
        }
        return dayjs(dueDate).format('MMM D, HH:mm');
    });

    protected onClick() {
        this.resultClick.emit(this.result());
    }
}
