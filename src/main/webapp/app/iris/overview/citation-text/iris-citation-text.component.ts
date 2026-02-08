import { ChangeDetectionStrategy, Component, HostListener, ViewEncapsulation, computed, inject, input } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { IrisCitationParsed } from './iris-citation-text.model';
import { escapeHtml, formatCitationLabel, getCitationLabelText, replaceCitationBlocks, resolveCitationTypeClass } from './iris-citation-text.util';
import { faChevronLeft, faChevronRight, faCircleExclamation, faCircleQuestion, faFilePdf, faFileVideo } from '@fortawesome/free-solid-svg-icons';

/**
 * Component that processes text containing citation markers and renders them as interactive citation bubbles.
 * Takes raw text with [cite:entityID:page:start_time:end_time:keyword:summary] markers as input and outputs rendered HTML including citation bubbles.
 */
@Component({
    selector: 'jhi-iris-citation-text',
    templateUrl: './iris-citation-text.component.html',
    styleUrls: ['./iris-citation-text.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
    standalone: true,
})
export class IrisCitationTextComponent {
    private readonly domSanitizer = inject(DomSanitizer);

    /**
     * Maps citation type classes to FontAwesome icons.
     * Used for rendering citation bubbles with appropriate visual indicators.
     */
    private readonly CITATION_TYPE_ICONS = new Map([
        ['iris-citation--slide', faFilePdf], // Slide citations
        ['iris-citation--video', faFileVideo], // Transcription citations
        ['iris-citation--faq', faCircleQuestion], // FAQ citations
        ['iris-citation--source', faCircleExclamation], // Unknown source citations
    ]);

    readonly text = input.required<string>();
    readonly citationInfo = input<IrisCitationMetaDTO[]>([]);

    readonly renderedContent = computed<SafeHtml>(() => {
        const processed = this.processText(this.text(), this.citationInfo());
        return this.domSanitizer.bypassSecurityTrustHtml(processed);
    });

    /**
     * Processes text by applying markdown rendering first, then replacing citation markers with HTML.
     */
    private processText(text: string, citationInfo: IrisCitationMetaDTO[]): string {
        // Apply markdown rendering (this converts markdown syntax to HTML)
        const markdownHtml = htmlForMarkdown(text);

        // Replace [cite:...] blocks in the HTML with citation bubbles
        const withCitations = replaceCitationBlocks(markdownHtml, citationInfo, {
            renderSingle: (parsed, meta) => this.renderCitationHtml(parsed, meta),
            renderGroup: (parsed, metas) => this.renderCitationGroupHtml(parsed, metas),
        });

        return withCitations;
    }

    /**
     * Renders a single citation as HTML with icon, label, and optional summary tooltip.
     */
    private renderCitationHtml(parsed: IrisCitationParsed, meta?: IrisCitationMetaDTO): string {
        const label = formatCitationLabel(parsed);
        const typeClass = resolveCitationTypeClass(parsed);
        const hasSummary = !!parsed.summary;
        const classes = `iris-citation ${typeClass}${hasSummary ? ' iris-citation--has-summary' : ''}`;
        const iconSvg = this.getIconSvg(typeClass);
        const summaryFallbackTitle = getCitationLabelText(parsed);

        // Include summary tooltip with fallback title and lecture context if available
        const summaryContent = hasSummary
            ? `<span class="iris-citation__summary">
                   <span class="iris-citation__summary-content">
                       ${this.renderSummaryContent(parsed.summary, meta, summaryFallbackTitle)}
                   </span>
               </span>`
            : '';

        return `
            <span class="${classes}">
                <span class="iris-citation__icon">${iconSvg}</span>
                <span class="iris-citation__text">${label}</span>
                ${summaryContent}
            </span>
        `.trim();
    }

    /**
     * Renders a group of citations as HTML with aggregation count, summary tooltips, and navigation controls.
     */
    private renderCitationGroupHtml(parsed: IrisCitationParsed[], metas: Array<IrisCitationMetaDTO | undefined>): string {
        const first = parsed[0];
        const label = formatCitationLabel(first);
        const typeClass = resolveCitationTypeClass(first);
        const hasSummary = parsed.some((p) => !!p.summary);
        const groupClasses = `iris-citation-group ${typeClass}${hasSummary ? ' iris-citation-group--has-summary' : ''}`;
        const count = parsed.length - 1;
        const iconSvg = this.getIconSvg(typeClass);

        // Render summary tooltip with navigation if available
        const summaryContent = hasSummary
            ? `<span class="iris-citation__summary">
                   <span class="iris-citation__summary-content">
                       ${this.renderSummaryItems(parsed, metas)}
                   </span>
                   ${this.renderNavigationControls(parsed.filter((p) => !!p.summary).length)}
               </span>`
            : '';

        return `
            <span class="${groupClasses}">
                <span class="iris-citation ${typeClass}">
                    <span class="iris-citation__icon">${iconSvg}</span>
                    <span class="iris-citation__text">${label}</span>
                </span>
                <span class="iris-citation__count">+${count}</span>
                ${summaryContent}
            </span>
        `.trim();
    }

    /**
     * Renders the content of a citation summary tooltip including title, lecture context, and summary text.
     */
    private renderSummaryContent(summary: string, meta?: IrisCitationMetaDTO, fallbackTitle?: string): string {
        const lectureUnitTitle = meta?.lectureUnitTitle?.trim();
        const lectureTitle = meta?.lectureTitle?.trim();
        const summaryText = summary?.trim();
        const title = lectureUnitTitle || fallbackTitle || '';

        const titleHtml = title ? `<span class="iris-citation__summary-title">${escapeHtml(title)}</span>` : '';
        const lectureHtml = lectureTitle ? `<span class="iris-citation__summary-lecture">in ${escapeHtml(lectureTitle)}</span>` : '';
        const summaryHtml = summaryText ? `<span class="iris-citation__summary-text">${escapeHtml(summaryText)}</span>` : '';

        return `${titleHtml}${lectureHtml}${summaryHtml}`;
    }

    /**
     * Renders navigable summary items for a citation group.
     * Each item represents a citation with a summary, with the first one marked as active.
     */
    private renderSummaryItems(parsed: IrisCitationParsed[], metas: Array<IrisCitationMetaDTO | undefined>): string {
        let summaryIndex = 0;
        return parsed
            .map((cite, index) => {
                if (!cite.summary) return '';

                const meta = metas[index];
                const isActive = summaryIndex === 0 ? 'is-active' : '';
                const summaryFallbackTitle = getCitationLabelText(cite);
                summaryIndex++;

                return `
                    <span class="iris-citation__summary-item ${isActive}">
                        ${this.renderSummaryContent(cite.summary, meta, summaryFallbackTitle)}
                    </span>
                `.trim();
            })
            .filter(Boolean)
            .join('');
    }

    /**
     * Renders navigation controls for multi-citation summary tooltips.
     * Returns empty string if there is only one summary (no navigation needed).
     */
    private renderNavigationControls(summaryCount: number): string {
        if (summaryCount <= 1) {
            return '';
        }

        const chevronLeftSvg = this.getNavIconSvg('left');
        const chevronRightSvg = this.getNavIconSvg('right');

        return `
            <span class="iris-citation__nav">
                <button class="iris-citation__nav-button" type="button">${chevronLeftSvg}</button>
                <span>1 / ${summaryCount}</span>
                <button class="iris-citation__nav-button" type="button">${chevronRightSvg}</button>
            </span>
        `.trim();
    }

    /**
     * Generates an SVG element from a FontAwesome icon definition.
     * Used internally for rendering all citation and navigation icons.
     */
    private generateSvg(fontAwesomeIcon: typeof faFilePdf): string {
        const [width, height, , , svgPath] = fontAwesomeIcon.icon;

        return `
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" fill="currentColor">
                <path d="${svgPath}"/>
            </svg>
        `.trim();
    }

    /**
     * Returns the appropriate FontAwesome icon SVG based on citation type class.
     * Defaults to exclamation icon for unknown types.
     */
    private getIconSvg(typeClass: string): string {
        const fontAwesomeIcon = this.CITATION_TYPE_ICONS.get(typeClass) ?? faCircleExclamation;
        return this.generateSvg(fontAwesomeIcon);
    }

    /**
     * Returns the appropriate FontAwesome navigation icon SVG for left/right chevrons.
     * Used in multi-citation summary navigation controls.
     */
    private getNavIconSvg(direction: 'left' | 'right'): string {
        const fontAwesomeIcon = direction === 'left' ? faChevronLeft : faChevronRight;
        return this.generateSvg(fontAwesomeIcon);
    }

    /**
     * Handles navigation button clicks using event delegation.
     */
    @HostListener('click', ['$event'])
    onHostClick(event: MouseEvent): void {
        const target = event.target as HTMLElement;
        const button = target.closest('.iris-citation__nav-button');

        if (!button) return;

        event.stopPropagation();

        (button as HTMLElement).blur();

        const citationGroup = button.closest('.iris-citation-group--has-summary');
        if (!citationGroup) return;

        const navButtons = Array.from(citationGroup.querySelectorAll('.iris-citation__nav-button'));
        const summaryItems = Array.from(citationGroup.querySelectorAll('.iris-citation__summary-item'));
        const counterDisplay = citationGroup.querySelector('.iris-citation__nav span');

        if (navButtons.length !== 2 || summaryItems.length === 0) return;

        const currentActiveIndex = summaryItems.findIndex((item) => item.classList.contains('is-active'));
        const currentIndex = currentActiveIndex >= 0 ? currentActiveIndex : 0;

        const isPrevious = navButtons[0] === button;
        const newIndex = isPrevious ? (currentIndex - 1 + summaryItems.length) % summaryItems.length : (currentIndex + 1) % summaryItems.length;

        summaryItems.forEach((item, index) => {
            item.classList.toggle('is-active', index === newIndex);
        });
        if (counterDisplay) {
            counterDisplay.textContent = `${newIndex + 1} / ${summaryItems.length}`;
        }
    }

    /**
     * Handles citation tooltip collision detection on mouse over.
     * Adjusts tooltip position if it would overflow the boundary.
     */
    @HostListener('mouseover', ['$event'])
    onCitationMouseOver(event: MouseEvent): void {
        const target = event.target as HTMLElement | null;
        const citation = target?.closest('.iris-citation--has-summary, .iris-citation-group--has-summary') as HTMLElement | null;

        if (!citation) return;

        const summary = citation.querySelector('.iris-citation__summary') as HTMLElement | null;
        if (!summary) return;

        const bubble = citation.closest('.bubble-left') as HTMLElement | null;
        const boundary = bubble ?? (citation.closest('jhi-iris-citation-text') as HTMLElement);

        if (!boundary) return;

        citation.style.setProperty('--iris-citation-shift', '0px');
        const boundaryRect = boundary.getBoundingClientRect();
        const summaryRect = summary.getBoundingClientRect();

        let shift = 0;
        if (summaryRect.left < boundaryRect.left + 10) {
            shift = boundaryRect.left - summaryRect.left + 10;
        } else if (summaryRect.right > boundaryRect.right) {
            shift = boundaryRect.right - summaryRect.right;
        }

        if (shift !== 0) {
            citation.style.setProperty('--iris-citation-shift', `${shift}px`);
        }
    }

    /**
     * Resets citation tooltip position on mouse out.
     */
    @HostListener('mouseout', ['$event'])
    onCitationMouseOut(event: MouseEvent): void {
        const target = event.target as HTMLElement | null;
        const citation = target?.closest('.iris-citation--has-summary, .iris-citation-group--has-summary') as HTMLElement | null;

        if (!citation) return;

        const relatedTarget = event.relatedTarget as HTMLElement | null;
        if (relatedTarget && citation.contains(relatedTarget)) return;

        citation.style.setProperty('--iris-citation-shift', '0px');
    }
}
