import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, HostListener, ViewEncapsulation, computed, inject, input } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';
import { htmlForMarkdown } from 'app/foundation/util/markdown.conversion.util';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AlertService } from 'app/foundation/service/alert.service';
import { IrisCitationParsed, IrisCitationVersion } from './iris-citation-text.model';
import { IrisCitationMaterialVersionService } from './iris-citation-material-version.service';
import { escapeHtml, formatCitationLabel, replaceCitationBlocks, resolveCitationTypeClass } from './iris-citation-text.util';
import { IconDefinition, faChevronLeft, faChevronRight, faCircleExclamation, faCircleQuestion, faFilePdf, faFileVideo } from '@fortawesome/free-solid-svg-icons';

/**
 * Translation keys for the notices shown when the cited material is no longer the one the answer was written against.
 */
const CITATION_STALE_WARNING_KEY = 'artemisApp.iris.citation.outdated.stale';
const CITATION_GONE_ERROR_KEY = 'artemisApp.iris.citation.outdated.gone';
const CITATION_UNVERIFIED_WARNING_KEY = 'artemisApp.iris.citation.outdated.unverified';

/**
 * Statuses that mean the unit itself cannot be reached, rather than that the check failed. The lecture page reports these once it has loaded its units, so the click stays
 * silent for them and does not say the same thing twice in two different words.
 */
const UNIT_UNREACHABLE_STATUSES = [403, 404];

/**
 * Component that processes text containing citation markers and renders them as interactive citation bubbles.
 * Takes raw text with [cite:entityID:page:start_time:end_time:keyword:summary] markers as input and outputs rendered HTML with citation bubbles.
 */
@Component({
    selector: 'jhi-iris-citation-text',
    templateUrl: './iris-citation-text.component.html',
    styleUrls: ['./iris-citation-text.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
})
export class IrisCitationTextComponent {
    private readonly domSanitizer = inject(DomSanitizer);
    private readonly translateService = inject(TranslateService);
    private readonly router = inject(Router);
    private readonly alertService = inject(AlertService);
    private readonly materialVersionService = inject(IrisCitationMaterialVersionService);
    private readonly destroyRef = inject(DestroyRef);

    /**
     * Maps citation type classes to FontAwesome icons.
     * Used for rendering citation bubbles with appropriate visual indicators.
     */
    private readonly CITATION_TYPE_ICONS: Record<string, IconDefinition> = {
        'iris-citation--slide': faFilePdf, // Slide citations
        'iris-citation--video': faFileVideo, // Transcription citations
        'iris-citation--faq': faCircleQuestion, // FAQ citations
        'iris-citation--source': faCircleExclamation, // Unknown source citations
    };

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
        const markdownHtml = htmlForMarkdown(text, [], undefined, undefined, true);

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
        const isClickable = this.isCitationClickable(parsed, meta);
        const iconSvg = this.getIconSvg(typeClass);
        const dataAttrs = this.buildNavigationDataAttributes(parsed, meta);

        if (hasSummary) {
            const wrapperClasses = `iris-citation-single ${typeClass} iris-citation--has-summary`;
            const citationClasses = `iris-citation ${typeClass}${isClickable ? ' iris-citation--clickable' : ''}`;

            const summaryContent = `<span class="iris-citation__summary">
                   <span class="iris-citation__summary-content">
                       <span class="iris-citation__summary-item is-active${isClickable ? ' iris-citation__summary-item--clickable' : ''}"${dataAttrs}>
                           ${this.renderSummaryContent(parsed.summary, meta)}
                       </span>
                   </span>
               </span>`;

            return `
                <span class="${wrapperClasses}">
                    <span class="${citationClasses}"${dataAttrs}>
                        <span class="iris-citation__icon">${iconSvg}</span>
                        <span class="iris-citation__text">${label}</span>
                    </span>
                    ${summaryContent}
                </span>
            `.trim();
        }

        const classes = `iris-citation ${typeClass}${isClickable ? ' iris-citation--clickable' : ''}`;
        return `
            <span class="${classes}"${dataAttrs}>
                <span class="iris-citation__icon">${iconSvg}</span>
                <span class="iris-citation__text">${label}</span>
            </span>
        `.trim();
    }

    /**
     * Renders a group of citations as HTML with aggregation count, summary tooltips, and navigation controls.
     */
    private renderCitationGroupHtml(parsedIrisCitation: IrisCitationParsed[], metadata: Array<IrisCitationMetaDTO | undefined>): string {
        const first = parsedIrisCitation[0];
        const firstMeta = metadata[0];
        const label = formatCitationLabel(first);
        const typeClass = resolveCitationTypeClass(first);
        const hasSummary = parsedIrisCitation.some((p) => !!p.summary);
        const isClickable = this.isCitationClickable(first, firstMeta);
        const groupClasses = `iris-citation-group ${typeClass}${hasSummary ? ' iris-citation-group--has-summary' : ''}`;
        const count = parsedIrisCitation.length - 1;
        const iconSvg = this.getIconSvg(typeClass);
        const dataAttrs = this.buildNavigationDataAttributes(first, firstMeta);

        // Render summary tooltip with navigation if available
        const summaryContent = hasSummary
            ? `<span class="iris-citation__summary">
                   <span class="iris-citation__summary-content">
                       ${this.renderSummaryItems(parsedIrisCitation, metadata)}
                   </span>
                   ${this.renderNavigationControls(parsedIrisCitation.filter((p) => !!p.summary).length)}
               </span>`
            : '';

        return `
            <span class="${groupClasses}">
                <span class="iris-citation ${typeClass}${isClickable ? ' iris-citation--clickable' : ''}"${dataAttrs}>
                    <span class="iris-citation__icon">${iconSvg}</span>
                    <span class="iris-citation__text">${label}</span>
                </span>
                <span class="iris-citation__count">+${count}</span>
                ${summaryContent}
            </span>
        `.trim();
    }

    /**
     * Renders the content of a citation summary tooltip including summary text and optional lecture metadata.
     */
    private renderSummaryContent(summary: string, meta?: IrisCitationMetaDTO): string {
        const lectureUnitTitle = meta?.lectureUnitTitle?.trim();
        const lectureTitle = meta?.lectureTitle?.trim();
        const summaryText = summary?.trim();
        const unitTitle = lectureUnitTitle || '';
        const hasUnit = !!unitTitle;
        const hasLecture = !!lectureTitle;
        const hasMeta = hasUnit || hasLecture;

        const summaryHtml = summaryText ? `<span class="iris-citation__summary-text">${escapeHtml(summaryText)}</span>` : '';
        if (!hasMeta) {
            return summaryHtml;
        }

        const unitLabel = escapeHtml(this.translateService.instant('artemisApp.iris.citation.unitLabel'));
        const lectureLabel = escapeHtml(this.translateService.instant('artemisApp.iris.citation.lectureLabel'));
        const dividerHtml = '<span class="iris-citation__summary-divider"></span>';
        const unitHtml = hasUnit ? this.renderSummaryMetaRow('unit', unitLabel, unitTitle) : '';
        const lectureHtml = hasLecture ? this.renderSummaryMetaRow('lecture', lectureLabel, lectureTitle ?? '') : '';
        const metaHtml = `
            <span class="iris-citation__summary-meta">
                ${unitHtml}${lectureHtml}
            </span>
        `.trim();

        const divider = summaryHtml.trim() ? dividerHtml : '';

        return `${summaryHtml}${divider}${metaHtml}`;
    }

    private renderSummaryMetaRow(type: 'unit' | 'lecture', label: string, value: string): string {
        return `
            <span class="iris-citation__summary-row iris-citation__summary-row--${type}">
                <span class="iris-citation__summary-label">${label}</span>
                <span class="iris-citation__summary-value">${escapeHtml(value)}</span>
            </span>
        `.trim();
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
                const dataAttrs = this.buildNavigationDataAttributes(cite, meta);
                const isClickable = this.isCitationClickable(cite, meta);
                summaryIndex++;

                return `
                    <span class="iris-citation__summary-item ${isActive}${isClickable ? ' iris-citation__summary-item--clickable' : ''}"
                          data-citation-index="${index}"${dataAttrs}>
                        ${this.renderSummaryContent(cite.summary, meta)}
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
                <path d="${Array.isArray(svgPath) ? svgPath.join('') : svgPath}"/>
            </svg>
        `.trim();
    }

    /**
     * Returns the appropriate FontAwesome icon SVG based on citation type class.
     * Defaults to exclamation icon for unknown types.
     */
    private getIconSvg(typeClass: string): string {
        const fontAwesomeIcon = this.CITATION_TYPE_ICONS[typeClass] ?? faCircleExclamation;
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
     * Decides whether a citation can be navigated to.
     */
    private isCitationClickable(parsed: IrisCitationParsed, meta?: IrisCitationMetaDTO): boolean {
        return !!meta && !!meta.courseId && !!meta.lectureId && !!parsed.entityId && (!!parsed.page || !!parsed.start);
    }

    /**
     * Builds data attributes for navigation.
     */
    private buildNavigationDataAttributes(parsed: IrisCitationParsed, meta?: IrisCitationMetaDTO): string {
        if (!meta) {
            return '';
        }

        const courseId = meta.courseId;
        const lectureId = meta.lectureId;
        const unitId = parsed.entityId;

        if (courseId === undefined || courseId === null || lectureId === undefined || lectureId === null || !unitId) {
            return '';
        }

        const attrs: string[] = [];
        attrs.push(`data-course-id="${escapeHtml(String(courseId))}"`);
        attrs.push(`data-lecture-id="${escapeHtml(String(lectureId))}"`);
        attrs.push(`data-unit-id="${escapeHtml(String(unitId))}"`);
        if (parsed.start) {
            attrs.push(`data-timestamp="${escapeHtml(parsed.start)}"`);
        }
        if (parsed.page) {
            attrs.push(`data-page="${escapeHtml(parsed.page)}"`);
        }
        // Kept on the element rather than put into the link: only needed to compare against the version the server reports on click. The tag of the version field is what
        // says whether it is about slides or a video - re-deriving that from the timestamp would be a second, drifting answer to a question the server has settled.
        const pinnedVersion = parsed.pinnedVersion;
        if (pinnedVersion) {
            attrs.push(`data-pinned-version="${escapeHtml(pinnedVersion.version)}"`);
            attrs.push(`data-pinned-kind="${pinnedVersion.kind}"`);
        }

        return attrs.length > 0 ? ' ' + attrs.join(' ') : '';
    }

    /**
     * Navigates to the citation source.
     * <p>
     * A citation pinned to a material version is checked against the server first, so the decision is based on the material as it is at this very moment. Citations
     * written before versions existed carry no pinned version and navigate straight away.
     */
    private navigateToCitation(element: HTMLElement): void {
        const courseId = element.getAttribute('data-course-id');
        const lectureId = element.getAttribute('data-lecture-id');
        const unitId = element.getAttribute('data-unit-id');

        if (!courseId || !lectureId || !unitId) {
            return;
        }

        const pinnedVersion = element.getAttribute('data-pinned-version');
        const pinnedKindAttribute = element.getAttribute('data-pinned-kind');
        if (!pinnedVersion || !pinnedKindAttribute) {
            this.navigateToLectureUnit(element, courseId, lectureId, unitId, true);
            return;
        }
        const pinnedKind: IrisCitationVersion['kind'] = pinnedKindAttribute === 'video' ? 'video' : 'attachment';

        this.materialVersionService
            .getMaterialVersions(Number(unitId))
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (versions) => {
                    // Which kind of material this citation is about was decided when the marker was stamped, so it is read here rather than derived again.
                    const currentVersion = pinnedKind === 'video' ? versions.videoVersion : versions.attachmentVersion;
                    if (currentVersion !== undefined && currentVersion !== null) {
                        const isUnchanged = String(currentVersion) === pinnedVersion;
                        if (!isUnchanged) {
                            this.alertService.warning(CITATION_STALE_WARNING_KEY);
                        }
                        this.navigateToLectureUnit(element, courseId, lectureId, unitId, isUnchanged, pinnedKind);
                        return;
                    }
                    // A version can only have been pinned for material that was processed, so a missing one means the material is no longer what it was. A video whose
                    // transcription is missing is the one case where it may still be entirely intact: the transcription is deleted and rewritten whenever the video
                    // changes, so during that window the video plays while nothing can be compared against it. Calling that "gone" over a visibly playing video would be
                    // false, so it is reported as unverifiable instead.
                    const isUnverifiableVideo = pinnedKind === 'video' && !!versions.hasVideo;
                    if (isUnverifiableVideo) {
                        this.alertService.warning(CITATION_UNVERIFIED_WARNING_KEY);
                    } else {
                        this.alertService.error(CITATION_GONE_ERROR_KEY);
                    }
                    this.navigateToLectureUnit(element, courseId, lectureId, unitId, false);
                },
                error: (response: HttpErrorResponse) => {
                    // An unreachable unit is an answer, not a failed check, and the lecture page words it better once it knows which units it has. Anything else means
                    // the check was lost: the link is kept, but not the exact position, because a page number that could not be verified may well be the wrong one.
                    if (!UNIT_UNREACHABLE_STATUSES.includes(response.status)) {
                        this.alertService.warning(CITATION_UNVERIFIED_WARNING_KEY);
                    }
                    this.navigateToLectureUnit(element, courseId, lectureId, unitId, false);
                },
            });
    }

    /**
     * Navigates to the cited lecture unit, jumping to the exact page or timestamp only when the material is unchanged.
     * <p>
     * The `unit` parameter is what tells the lecture page that a specific unit was asked for rather than the lecture as a whole, so that it can say so when it cannot find
     * it. It is sent for every citation, including ones carrying no pinned version: whether the unit still exists is a question that does not need versions to answer.
     * <p>
     * A stamped citation only travels with the coordinate of the material it was checked against. A video citation carries the companion slide number of its transcript
     * segment, and that PDF may have changed on its own since — the transcription version says nothing about it, so jumping to that page would present an unverified slide
     * as the cited one. Citations without a pinned version keep taking both, exactly as they did before versions existed.
     */
    private navigateToLectureUnit(
        element: HTMLElement,
        courseId: string,
        lectureId: string,
        unitId: string,
        includeExactPosition: boolean,
        pinnedKind?: IrisCitationVersion['kind'],
    ): void {
        const queryParams: Record<string, string> = { unit: unitId };
        if (includeExactPosition) {
            const timestamp = element.getAttribute('data-timestamp');
            const page = element.getAttribute('data-page');
            if (timestamp && pinnedKind !== 'attachment') {
                queryParams.timestamp = timestamp;
            }
            if (page && pinnedKind !== 'video') {
                queryParams.page = page;
            }
        }

        void this.router.navigate(['/courses', courseId, 'lectures', lectureId], { queryParams });
    }

    /**
     * Handles citation and navigation button clicks using event delegation.
     */
    @HostListener('click', ['$event'])
    onHostClick(event: MouseEvent): void {
        const target = event.target as HTMLElement;
        const summaryItem = target.closest<HTMLElement>('.iris-citation__summary-item--clickable');
        if (summaryItem) {
            event.stopPropagation();
            this.navigateToCitation(summaryItem);
            return;
        }

        const citation = target.closest<HTMLElement>('.iris-citation--clickable');
        if (citation) {
            const summaryElement = target.closest('.iris-citation__summary');
            if (!summaryElement) {
                event.stopPropagation();
                this.navigateToCitation(citation);
                return;
            }
        }

        const summary = target.closest('.iris-citation__summary');
        if (summary && !target.closest('.iris-citation__nav')) {
            const activeSummaryItem = summary.querySelector<HTMLElement>('.iris-citation__summary-item.is-active.iris-citation__summary-item--clickable');
            if (activeSummaryItem) {
                event.stopPropagation();
                this.navigateToCitation(activeSummaryItem);
                return;
            }
        }

        const button = target.closest('.iris-citation__nav-button');
        if (!button) return;

        event.stopPropagation();

        (button as HTMLElement).blur();

        const citationGroup = button.closest('.iris-citation-group--has-summary');
        if (!citationGroup) return;

        const navButtons = Array.from(citationGroup.querySelectorAll('.iris-citation__nav-button'));
        const summaryItems = Array.from(citationGroup.querySelectorAll('.iris-citation__summary-item'));
        const counterDisplay = citationGroup.querySelector('.iris-citation__nav span');

        const hasInvalidNavigationStructure = navButtons.length !== 2 || summaryItems.length === 0;
        if (hasInvalidNavigationStructure) return;

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

        const summary = citation.querySelector('.iris-citation__summary');
        if (!summary) return;

        // Different boundaries for horizontal and vertical collision detection
        const bubble = citation.closest('.bubble-left');
        const messagesDiv = citation.closest('div.messages');
        const defaultBoundary = citation.closest('jhi-iris-citation-text') as HTMLElement;

        const horizontalBoundary = bubble ?? defaultBoundary;
        const verticalBoundary = messagesDiv ?? defaultBoundary;

        if (!horizontalBoundary || !verticalBoundary) return;

        // Reset positioning to get accurate measurements
        citation.style.setProperty('--iris-citation-shift', '0px');
        citation.style.setProperty('--iris-citation-vertical-offset', 'calc(-100% - 18px)');

        const horizontalBoundaryRect = horizontalBoundary.getBoundingClientRect();
        const verticalBoundaryRect = verticalBoundary.getBoundingClientRect();
        const summaryRect = summary.getBoundingClientRect();

        // horizontal collision detection
        let shift = 0;
        if (summaryRect.left < horizontalBoundaryRect.left) {
            shift = horizontalBoundaryRect.left - summaryRect.left;
        } else if (summaryRect.right > horizontalBoundaryRect.right) {
            shift = horizontalBoundaryRect.right - summaryRect.right;
        }
        if (shift !== 0) {
            citation.style.setProperty('--iris-citation-shift', `${shift}px`);
        }

        // vertical collision detection
        if (summaryRect.top < verticalBoundaryRect.top) {
            citation.style.setProperty('--iris-citation-vertical-offset', '0px');
            summary.classList.add('iris-citation__summary--flipped');
        } else {
            summary.classList.remove('iris-citation__summary--flipped');
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

        // Only remove the flipped class - don't reset CSS properties to avoid visual jump during fade-out
        // Properties will be reset on next mouseover anyway
        const summary = citation.querySelector('.iris-citation__summary');
        summary?.classList.remove('iris-citation__summary--flipped');
    }
}
