import { ChangeDetectionStrategy, Component, HostListener, computed, inject, input } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { IrisCitationParsed } from './iris-citation-text.model';
import { formatCitationLabel, replaceCitationBlocks, resolveCitationTypeClass } from './iris-citation-text.util';
import { faChevronLeft, faChevronRight, faCircleExclamation, faCircleQuestion, faFilePdf, faPlayCircle } from '@fortawesome/free-solid-svg-icons';

/**
 * Component that processes text containing citation markers and renders them as interactive citation bubbles.
 * Takes raw text with [cite:...] markers as input and outputs rendered HTML with citation bubbles.
 */
@Component({
    selector: 'jhi-iris-citation-text',
    templateUrl: './iris-citation-text.component.html',
    styleUrls: ['./iris-citation-text.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: true,
})
export class IrisCitationTextComponent {
    private readonly domSanitizer = inject(DomSanitizer);

    // Inputs
    readonly text = input.required<string>();
    readonly citationInfo = input<IrisCitationMetaDTO[]>([]);

    // Computed rendered content
    readonly renderedContent = computed<SafeHtml>(() => {
        const processed = this.processText(this.text(), this.citationInfo());
        return this.domSanitizer.bypassSecurityTrustHtml(processed);
    });

    /**
     * Processes text by applying markdown rendering first, then replacing citation markers with HTML.
     */
    private processText(text: string, citationInfo: IrisCitationMetaDTO[]): string {
        // 1. First apply markdown rendering (this converts markdown syntax to HTML)
        const markdownHtml = htmlForMarkdown(text);

        // 2. Then replace [cite:...] blocks in the HTML with citation bubbles
        const withCitations = replaceCitationBlocks(markdownHtml, citationInfo, {
            renderSingle: (parsed, meta) => this.renderCitationHtml(parsed, meta),
            renderGroup: (parsed, metas) => this.renderCitationGroupHtml(parsed, metas),
        });

        return withCitations;
    }

    /**
     * Renders a single citation as HTML.
     */
    private renderCitationHtml(parsed: IrisCitationParsed, meta?: IrisCitationMetaDTO): string {
        const label = formatCitationLabel(parsed);
        const typeClass = resolveCitationTypeClass(parsed);
        const hasSummary = !!parsed.summary;
        const classes = ['iris-citation', typeClass, hasSummary ? 'iris-citation--has-summary' : ''].filter(Boolean).join(' ');
        const iconSvg = this.getIconSvg(typeClass);

        let html = `<span class="${classes}">`;
        html += `<span class="iris-citation__icon">${iconSvg}</span>`;
        html += `<span class="iris-citation__text">${label}</span>`;

        // Add summary tooltip if available
        if (hasSummary) {
            html += `<span class="iris-citation__summary">`;
            html += `<span class="iris-citation__summary-content">`;
            html += `<span class="iris-citation__summary-keyword">${label}</span>`;
            if (meta?.lectureTitle || meta?.lectureUnitTitle) {
                html += `<span class="iris-citation__summary-meta">`;
                if (meta.lectureTitle) html += `${this.escapeHtml(meta.lectureTitle)}`;
                if (meta.lectureTitle && meta.lectureUnitTitle) html += ` • `;
                if (meta.lectureUnitTitle) html += `${this.escapeHtml(meta.lectureUnitTitle)}`;
                html += `</span>`;
            }
            html += `<span>${this.escapeHtml(parsed.summary)}</span>`;
            html += `</span>`;
            html += `</span>`;
        }

        html += `</span>`;
        return html;
    }

    /**
     * Renders a group of citations as HTML with aggregation count.
     */
    private renderCitationGroupHtml(parsed: IrisCitationParsed[], metas: Array<IrisCitationMetaDTO | undefined>): string {
        const first = parsed[0];
        const label = formatCitationLabel(first);
        const typeClass = resolveCitationTypeClass(first);
        const hasSummary = parsed.some((p) => !!p.summary);
        const groupClasses = ['iris-citation-group', typeClass, hasSummary ? 'iris-citation-group--has-summary' : ''].filter(Boolean).join(' ');
        const count = parsed.length - 1;
        const iconSvg = this.getIconSvg(typeClass);

        let html = `<span class="${groupClasses}">`;
        html += `<span class="iris-citation ${typeClass}">`;
        html += `<span class="iris-citation__icon">${iconSvg}</span>`;
        html += `<span class="iris-citation__text">${label}</span>`;
        html += `</span>`;
        html += `<span class="iris-citation__count">+${count}</span>`;

        // Add summary tooltip with navigation for multiple citations
        if (hasSummary) {
            html += `<span class="iris-citation__summary">`;
            html += `<span class="iris-citation__summary-content">`;

            // Render each citation as a navigable item
            parsed.forEach((cite, index) => {
                if (cite.summary) {
                    const citeLabel = formatCitationLabel(cite);
                    const meta = metas[index];
                    const isActive = index === 0 ? 'is-active' : '';
                    html += `<span class="iris-citation__summary-item ${isActive}">`;
                    html += `<span class="iris-citation__summary-keyword">${citeLabel}</span>`;
                    if (meta?.lectureTitle || meta?.lectureUnitTitle) {
                        html += `<span class="iris-citation__summary-meta">`;
                        if (meta.lectureTitle) html += `${this.escapeHtml(meta.lectureTitle)}`;
                        if (meta.lectureTitle && meta.lectureUnitTitle) html += ` • `;
                        if (meta.lectureUnitTitle) html += `${this.escapeHtml(meta.lectureUnitTitle)}`;
                        html += `</span>`;
                    }
                    html += `<span>${this.escapeHtml(cite.summary)}</span>`;
                    html += `</span>`;
                }
            });

            html += `</span>`;

            // Add navigation if there are multiple summaries
            const summaryCount = parsed.filter((p) => !!p.summary).length;
            if (summaryCount > 1) {
                const chevronLeftSvg = this.getNavIconSvg('left');
                const chevronRightSvg = this.getNavIconSvg('right');
                html += `<span class="iris-citation__nav">`;
                html += `<button class="iris-citation__nav-button" type="button">${chevronLeftSvg}</button>`;
                html += `<span>1 / ${summaryCount}</span>`;
                html += `<button class="iris-citation__nav-button" type="button">${chevronRightSvg}</button>`;
                html += `</span>`;
            }

            html += `</span>`;
        }

        html += `</span>`;
        return html;
    }

    /**
     * Returns the appropriate Font Awesome icon SVG based on citation type.
     */
    private getIconSvg(typeClass: string): string {
        let icon;
        switch (typeClass) {
            case 'iris-citation--slide':
                icon = faFilePdf;
                break;
            case 'iris-citation--video':
                icon = faPlayCircle;
                break;
            case 'iris-citation--faq':
                icon = faCircleQuestion;
                break;
            case 'iris-citation--source':
                icon = faCircleExclamation;
                break;
            default:
                icon = faCircleExclamation;
        }

        const [width, height, , , svgPath] = icon.icon;
        return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" fill="currentColor"><path d="${svgPath}"/></svg>`;
    }

    /**
     * Returns the appropriate Font Awesome navigation icon SVG.
     */
    private getNavIconSvg(direction: 'left' | 'right'): string {
        const icon = direction === 'left' ? faChevronLeft : faChevronRight;
        const [width, height, , , svgPath] = icon.icon;
        return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" fill="currentColor"><path d="${svgPath}"/></svg>`;
    }

    /**
     * Escapes HTML special characters.
     */
    private escapeHtml(text: string): string {
        return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
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

        // Remove focus from button to prevent focus-within from keeping tooltip open
        (button as HTMLElement).blur();

        const citationGroup = button.closest('.iris-citation-group--has-summary');
        if (!citationGroup) return;

        const navButtons = Array.from(citationGroup.querySelectorAll('.iris-citation__nav-button'));
        const summaryItems = Array.from(citationGroup.querySelectorAll('.iris-citation__summary-item'));
        const counterDisplay = citationGroup.querySelector('.iris-citation__nav span');

        if (navButtons.length !== 2 || summaryItems.length === 0) return;

        // Find current active index
        const currentActiveIndex = summaryItems.findIndex((item) => item.classList.contains('is-active'));
        const currentIndex = currentActiveIndex >= 0 ? currentActiveIndex : 0;

        // Determine direction based on which button was clicked
        const isPrevious = navButtons[0] === button;
        const newIndex = isPrevious ? (currentIndex - 1 + summaryItems.length) % summaryItems.length : (currentIndex + 1) % summaryItems.length;

        // Update display
        summaryItems.forEach((item, index) => {
            item.classList.toggle('is-active', index === newIndex);
        });
        if (counterDisplay) {
            counterDisplay.textContent = `${newIndex + 1} / ${summaryItems.length}`;
        }
    }
}
