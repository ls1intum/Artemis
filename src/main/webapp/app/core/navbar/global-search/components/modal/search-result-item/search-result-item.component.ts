import { Component, computed, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBook, faCalendarAlt, faGraduationCap, faLevelDownAlt, faTrophy } from '@fortawesome/free-solid-svg-icons';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { GlobalSearchResult } from 'app/openapi/model/globalSearchResult';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import dayjs from 'dayjs/esm';

/** Format for displaying dates in search results, e.g. "Apr 19, 14:30" */
const SEARCH_RESULT_DATE_FORMAT = 'MMM D, HH:mm';

@Component({
    selector: 'jhi-global-search-result-item',
    standalone: true,
    imports: [FaIconComponent, ArtemisTranslatePipe, HtmlForMarkdownPipe],
    templateUrl: './search-result-item.component.html',
    styleUrls: ['./search-result-item.component.scss'],
})
export class SearchResultItemComponent {
    protected readonly faBook = faBook;
    protected readonly faCalendarAlt = faCalendarAlt;
    protected readonly faGraduationCap = faGraduationCap;
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

    protected isExamExercise = computed(() => this.result().type === 'exercise' && !!this.result().metadata?.['examId']);

    protected hasAnyMetadata = computed(() => !!(this.courseName() || this.dueDate() || this.startDate() || this.points() || this.difficulty()));
    protected showCourseSeparator = computed(() => !!(this.courseName() && (this.dueDate() || this.startDate() || this.points() || this.difficulty())));
    protected showStartDateOnly = computed(() => !!(this.startDate() && !this.dueDate()));
    protected showDatePointsSeparator = computed(() => !!(this.points() && (this.dueDate() || this.startDate())));
    protected showDifficultySeparator = computed(() => !!(this.difficulty() && (this.dueDate() || this.startDate() || this.points())));

    protected formattedDueDate = computed(() => this.formatMetadataDate('dueDate'));

    protected formattedStartDate = computed(() => this.formatMetadataDate('startDate'));

    /**
     * Returns the description with the first line removed if it is a markdown-formatted
     * repetition of the title (e.g. "# My Exercise" when the title is "My Exercise").
     */
    protected cleanedDescription = computed(() => {
        const description = this.result().description;
        const title = this.result().title;
        if (!description) {
            return undefined;
        }

        const lines = description.split('\n');
        const firstLine = lines[0].trim();

        // Strip common markdown formatting from the first line
        const stripped = firstLine
            .replace(/^#{1,6}\s+/, '') // headings
            .replace(/\*\*(.*?)\*\*/g, '$1') // bold
            .replace(/__(.*?)__/g, '$1') // bold alt
            .replace(/\*(.*?)\*/g, '$1') // italic
            .replace(/_(.*?)_/g, '$1') // italic alt
            .trim();

        if (title && stripped.toLowerCase() === title.trim().toLowerCase()) {
            const rest = lines.slice(1).join('\n').trim();
            return rest || undefined;
        }

        return description;
    });

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
