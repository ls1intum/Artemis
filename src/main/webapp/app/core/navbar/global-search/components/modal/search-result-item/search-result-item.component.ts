import { Component, computed, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBook, faCalendarAlt, faGraduationCap, faHashtag, faLevelDownAlt, faReply, faTrophy } from '@fortawesome/free-solid-svg-icons';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { GlobalSearchResult } from 'app/openapi/model/globalSearchResult';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import dayjs from 'dayjs/esm';

/** Format for displaying dates in search results, e.g. "Apr 19, 14:30" */
const SEARCH_RESULT_DATE_FORMAT = 'MMM D, HH:mm';

/**
 * Matches Markdown headings.
 * @example "## Title" → "Title"
 */
const MARKDOWN_HEADING_PATTERN = /^#{1,6}\s+/;

/**
 * Matches Markdown bold.
 * @example "**Title**" → "Title"
 */
const MARKDOWN_BOLD_PATTERN = /\*\*(.*?)\*\*/g;

/**
 * Matches Markdown bold (alt syntax).
 * @example "__Title__" → "Title"
 */
const MARKDOWN_BOLD_ALT_PATTERN = /__(.*?)__/g;

/**
 * Matches Markdown italic.
 * @example "*Title*" → "Title"
 */
const MARKDOWN_ITALIC_PATTERN = /\*(.*?)\*/g;

/**
 * Matches Markdown italic (alt syntax).
 * @example "_Title_" → "Title"
 */
const MARKDOWN_ITALIC_ALT_PATTERN = /_(.*?)_/g;

/**
 * Removes common Markdown formatting (headings, bold, italic) from a line of text.
 */
function stripMarkdownFormatting(text: string): string {
    return text
        .trim()
        .replace(MARKDOWN_HEADING_PATTERN, '')
        .replace(MARKDOWN_BOLD_PATTERN, '$1')
        .replace(MARKDOWN_BOLD_ALT_PATTERN, '$1')
        .replace(MARKDOWN_ITALIC_PATTERN, '$1')
        .replace(MARKDOWN_ITALIC_ALT_PATTERN, '$1')
        .trim();
}

/** Maximum number of characters to keep in a search result description before truncating. */
const DESCRIPTION_MAX_LENGTH = 300;

/**
 * Matches fenced code block delimiters (``` with optional language tag).
 */
const FENCED_CODE_BLOCK_PATTERN = /^(`{3,})/gm;

/**
 * Truncates a markdown description to a maximum length.
 * If the truncation lands inside a fenced code block, the block is closed properly.
 */
function truncateDescription(description: string, maxLength: number = DESCRIPTION_MAX_LENGTH): string {
    if (description.length <= maxLength) {
        return description;
    }

    const truncated = description.substring(0, maxLength);

    // Count fenced code block delimiters to detect if we're inside an unclosed block
    const fenceMatches = truncated.match(FENCED_CODE_BLOCK_PATTERN);
    const insideCodeBlock = fenceMatches != undefined && fenceMatches.length % 2 !== 0;

    if (insideCodeBlock) {
        return truncated + '…\n```';
    }

    return truncated + '…';
}

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
    protected readonly faHashtag = faHashtag;
    protected readonly faReply = faReply;

    result = input.required<GlobalSearchResult>();
    icon = input.required<IconDefinition>();
    isSelected = input.required<boolean>();

    resultClick = output<GlobalSearchResult>();

    protected courseName = computed(() => this.result().metadata?.['courseName']);
    protected dueDate = computed(() => this.result().metadata?.['dueDate']);
    protected startDate = computed(() => this.result().metadata?.['startDate']);
    protected points = computed(() => this.result().metadata?.['points']);
    protected difficulty = computed(() => this.result().metadata?.['difficulty']);
    protected channelName = computed(() => this.result().metadata?.['channelName']);
    protected isReply = computed(() => !!this.result().metadata?.['isReply']);

    protected isExamExercise = computed(() => this.result().type === 'exercise' && !!this.result().metadata?.['examId']);
    protected isMessage = computed(() => this.result().type === 'post' || this.result().type === 'answer_post');

    protected hasAnyMetadata = computed(() => !!(this.courseName() || this.dueDate() || this.startDate() || this.points() || this.difficulty() || this.channelName()));
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
        const strippedFirstLine = stripMarkdownFormatting(lines[0]);
        const firstLineMatchesTitle = title && strippedFirstLine.toLowerCase() === title.trim().toLowerCase();
        if (firstLineMatchesTitle) {
            const descriptionWithoutFirstLine = lines.slice(1).join('\n').trim();
            return descriptionWithoutFirstLine ? truncateDescription(descriptionWithoutFirstLine) : undefined;
        }

        return truncateDescription(description);
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
