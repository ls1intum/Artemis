import { Component, computed, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBook, faCalendarAlt, faLevelDownAlt, faTrophy } from '@fortawesome/free-solid-svg-icons';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { GlobalSearchResult } from '../../../services/global-search.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';

/** Format for displaying dates in search results, e.g. "Apr 19, 14:30" */
const SEARCH_RESULT_DATE_FORMAT = 'MMM D, HH:mm';

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

    protected courseName = computed(() => this.result().metadata?.['courseName']);
    protected dueDate = computed(() => this.result().metadata?.['dueDate']);
    protected startDate = computed(() => this.result().metadata?.['startDate']);
    protected points = computed(() => this.result().metadata?.['points']);
    protected difficulty = computed(() => this.result().metadata?.['difficulty']);

    protected hasAnyMetadata = computed(() => !!(this.courseName() || this.dueDate() || this.startDate() || this.points() || this.difficulty()));
    protected showCourseSeparator = computed(() => !!(this.courseName() && (this.dueDate() || this.startDate() || this.points() || this.difficulty())));
    protected showStartDateOnly = computed(() => !!(this.startDate() && !this.dueDate()));
    protected showDatePointsSeparator = computed(() => !!(this.points() && (this.dueDate() || this.startDate())));
    protected showDifficultySeparator = computed(() => !!(this.difficulty() && (this.dueDate() || this.startDate() || this.points())));

    protected formattedDueDate = computed(() => this.formatMetadataDate('dueDate'));

    protected formattedStartDate = computed(() => this.formatMetadataDate('startDate'));

    protected onClick() {
        this.resultClick.emit(this.result());
    }

    private formatMetadataDate(key: string): string {
        const value = this.result().metadata?.[key];
        if (!value) {
            return '';
        }
        return dayjs(value).format(SEARCH_RESULT_DATE_FORMAT);
    }
}
