import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faChevronUp, faFile, faFilePdf, faFileVideo, faVideo } from '@fortawesome/free-solid-svg-icons';
import { Router, RouterLink } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { IrisThinkingBubbleComponent } from 'app/iris/overview/base-chatbot/iris-thinking-bubble/iris-thinking-bubble.component';
import { IrisSearchAnswerService } from 'app/core/navbar/global-search/services/iris-search-answer.service';
import { EntitySearchSource } from 'app/core/navbar/global-search/models/entity-search-source.model';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { IrisSearchResult } from 'app/core/navbar/global-search/models/iris-search-result.model';
import { IrisSearchStatusUpdate } from 'app/core/navbar/global-search/models/iris-search-status-update.model';
import { iconForEntityType } from 'app/core/navbar/global-search/util/entity-type-icons.util';
import { parseCitationNumbers, renderCitationMarkers } from 'app/core/navbar/global-search/util/iris-citation-markers.util';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { SEARCH_DEBOUNCE_MS, SHORT_QUERY_MAX_LENGTH } from 'app/core/navbar/global-search/components/views/search-result-view.directive';
import { catchError, of, switchMap, timer } from 'rxjs';

/** Number of lines shown before the answer is clamped. Must match the CSS `max-height` on `.iris-answer-text.is-clamped`. */
const CLAMP_LINE_COUNT = 4;

/** Fallback line-height in px used when `getComputedStyle` returns `"normal"` (no explicit value set). */
const DEFAULT_LINE_HEIGHT_PX = 20;

/**
 * Extra debounce for the Iris pipeline on top of the base search debounce.
 * Firing an LLM pipeline on every keystroke is wasteful; waiting longer means
 * the user has likely finished typing before the request goes out.
 */
const IRIS_ANSWER_DEBOUNCE_MS = SEARCH_DEBOUNCE_MS + 300;

/**
 * Minimum trimmed query length before the (expensive) Iris LLM answer pipeline is triggered.
 * Queries up to the "quite short" band (<= SHORT_QUERY_MAX_LENGTH) yield poor answers and are
 * not worth an LLM call, so the card stays idle until the query is longer than that band.
 */
const IRIS_ANSWER_MIN_QUERY_LENGTH = SHORT_QUERY_MAX_LENGTH + 1;

/**
 * Marker bound for STREAMED drafts: partial updates carry no sources yet, but their raw
 * markers still reference the numbered answer context (server-capped well below this),
 * so they render as chips immediately instead of flashing bracket text. The terminal
 * update replaces them with markers validated against the real sources list.
 */
const PARTIAL_CITATION_MARKER_BOUND = 20;

@Component({
    selector: 'jhi-global-search-iris-answer',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, RouterLink, ArtemisTranslatePipe, IrisLogoComponent, MarkdownDirective, IrisThinkingBubbleComponent],
    templateUrl: './global-search-iris-answer.component.html',
    styleUrls: ['./global-search-iris-answer.component.scss'],
})
export class GlobalSearchIrisAnswerComponent {
    private readonly irisSearchAnswerService = inject(IrisSearchAnswerService);
    private readonly router = inject(Router);

    readonly searchQuery = input.required<string>();
    /** Active course filter from the search modal; scopes the answer's retrieval to that course. */
    readonly courseId = input<number | undefined>(undefined);

    private readonly answerBody = viewChild<ElementRef<HTMLElement>>('answerBody');

    protected readonly irisResult = signal<IrisSearchResult | undefined>(undefined);
    protected readonly irisThinking = signal(false);
    private readonly currentRunId = signal<string | undefined>(undefined);
    protected readonly isExpanded = signal(false);
    protected readonly isOverflowing = signal(false);
    protected readonly moreOpen = signal(false);
    protected readonly shouldClamp = computed(() => this.isOverflowing() && !this.isExpanded());
    protected readonly sources = computed(() => this.irisResult()?.sources ?? []);
    /** Entity sources (course information); their citation numbers continue after the lecture sources. */
    protected readonly entitySources = computed(() => this.irisResult()?.entitySources ?? []);

    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly INITIAL_VISIBLE_SOURCE_COUNT = 2;
    protected readonly faChevronUp = faChevronUp;
    protected readonly faFile = faFile;

    protected readonly SOURCE_ICONS: Partial<Record<string, IconDefinition>> = {
        lecture_unit_slide: faFilePdf,
        lecture_unit_slide_video: faFileVideo,
        lecture_unit_video: faVideo,
    };

    protected readonly visibleSources = computed(() => (this.moreOpen() ? this.sources() : this.sources().slice(0, this.INITIAL_VISIBLE_SOURCE_COUNT)));

    /** Whether the current answer text is a streamed draft (no sources attached yet). */
    protected readonly isPartialAnswer = signal(false);
    /** Highest partial sequence number seen for the current run; lower numbers are stale. */
    private lastPartialSeq = 0;

    /** Answer markdown with `[n]` citation markers converted to chip elements, plus the cited numbers. */
    protected readonly citationView = computed(() => {
        const sourceCount = this.sources().length + this.entitySources().length;
        return renderCitationMarkers(this.irisResult()?.answer, sourceCount || (this.isPartialAnswer() ? PARTIAL_CITATION_MARKER_BOUND : 0));
    });
    /** Whether the answer carries inline citations; gates the chip numbering. */
    protected readonly hasCitations = computed(() => this.citationView().citedNumbers.size > 0);
    /** Source numbers currently highlighted, linking answer passages and source chips in both directions. */
    protected readonly activeCitations = signal<ReadonlySet<number>>(new Set());
    /** Popover state for a hovered inline citation, resolved to display fields at show time. */
    protected readonly citationPopover = signal<{ left: number; top: number; name: string; meta?: string; icon: IconDefinition; entityTypeKey?: string } | undefined>(undefined);
    /** Bumped when the lazily-rendered markdown lands, so the highlight effect re-runs over the new DOM. */
    private readonly markdownRenderTick = signal(0);

    constructor() {
        // Measure answer overflow after each new result; reset when the result clears.
        // The answer is rendered by the lazily-loaded markdown directive, so its final
        // height is only known once that chunk resolves and populates the element. A
        // fixed timer can fire while the element is still empty on a cold load, leaving
        // long answers unclamped; a ResizeObserver re-measures whenever the rendered
        // content changes size, so the clamp/show-more control appears reliably.
        effect((onCleanup) => {
            const result = this.irisResult();
            untracked(() => {
                this.isExpanded.set(false);
                this.isOverflowing.set(false);
                this.moreOpen.set(false);
            });
            if (!result?.answer) return;
            // Reactive read: the effect re-runs once the `#answerBody` element is rendered.
            const element = this.answerBody()?.nativeElement;
            if (!element) return;

            const measure = () => {
                const rawLineHeight = getComputedStyle(element).lineHeight;
                const lineHeight = rawLineHeight === 'normal' ? DEFAULT_LINE_HEIGHT_PX : parseFloat(rawLineHeight);
                untracked(() => this.isOverflowing.set(element.scrollHeight > lineHeight * CLAMP_LINE_COUNT));
            };

            measure();
            if (typeof ResizeObserver === 'undefined') return;
            // eslint-disable-next-line localRules/enforce-cleanup-on-destroy -- disconnected via the effect's onCleanup, which runs on destroy
            const observer = new ResizeObserver(() => measure());
            observer.observe(element);
            onCleanup(() => observer.disconnect());
        });

        // Bidirectional attribution highlight: wash the passages a hovered source
        // chip supports, and light the chips a hovered passage cites. The answer
        // is directive-rendered innerHTML, so classes are toggled on the real DOM;
        // clearing first keeps a paragraph lit when only one of its citation
        // chips matches the active set.
        effect(() => {
            const active = this.activeCitations();
            this.markdownRenderTick();
            const element = this.answerBody()?.nativeElement;
            if (!element) {
                return;
            }
            element.querySelectorAll('.iris-attr-lit').forEach((lit) => lit.classList.remove('iris-attr-lit'));
            for (const chip of element.querySelectorAll<HTMLElement>('.iris-cite')) {
                const isLit = parseCitationNumbers(chip.dataset.n).some((n) => active.has(n));
                chip.classList.toggle('iris-cite-lit', isLit);
                if (isLit) {
                    chip.closest('p, li')?.classList.add('iris-attr-lit');
                }
            }
        });

        // Iris answer pipeline — runs alongside the main search.
        // ask() emits multiple values: first a thinking update, then the final result.
        // The outer switchMap ensures every new searchQuery emission immediately cancels
        // both the debounce timer and any in-flight ask() subscription, so a stale
        // WebSocket update from a superseded run can never reach the subscriber.
        // State is reset at the top of the outer switchMap — before the debounce window —
        // so the UI clears on every keystroke even if the request has not fired yet.
        toObservable(computed(() => ({ query: this.searchQuery(), courseId: this.courseId() })))
            .pipe(
                switchMap(({ query, courseId }) => {
                    this.irisResult.set(undefined);
                    this.irisThinking.set(false);
                    this.currentRunId.set(undefined);
                    this.isPartialAnswer.set(false);
                    this.lastPartialSeq = 0;
                    if (query.trim().length < IRIS_ANSWER_MIN_QUERY_LENGTH) {
                        return of(undefined);
                    }
                    // timer(X) waits X ms before emitting, giving the outer switchMap time to cancel
                    // it on the next keystroke. Unlike of(query).pipe(debounceTime(X)), timer does not
                    // complete immediately — debounceTime flushes instantly when its source completes,
                    // which would bypass the debounce window entirely.
                    return timer(IRIS_ANSWER_DEBOUNCE_MS).pipe(switchMap(() => this.irisSearchAnswerService.ask(query, 5, courseId).pipe(catchError(() => of(undefined)))));
                }),
                takeUntilDestroyed(),
            )
            .subscribe((update: IrisSearchStatusUpdate | undefined) => {
                if (update === undefined) {
                    this.irisThinking.set(false);
                    return;
                }
                if (update.isThinking) {
                    this.currentRunId.set(update.runId);
                    if (update.partialResult) {
                        // Streamed draft: render the text as it grows; ignore out-of-order snapshots.
                        const seq = update.partialSeq ?? 0;
                        if (seq <= this.lastPartialSeq) {
                            return;
                        }
                        this.lastPartialSeq = seq;
                        this.irisThinking.set(false);
                        this.isPartialAnswer.set(true);
                        this.irisResult.set({ answer: update.partialResult, sources: [] });
                    } else if (!this.isPartialAnswer()) {
                        this.irisThinking.set(true);
                    }
                } else {
                    if (this.currentRunId() !== undefined && update.runId !== this.currentRunId()) {
                        return; // stale response from a superseded pipeline run
                    }
                    this.irisThinking.set(false);
                    this.isPartialAnswer.set(false);
                    this.lastPartialSeq = 0;
                    this.irisResult.set(update.answer ? { answer: update.answer, sources: update.sources ?? [], entitySources: update.entitySources ?? [] } : undefined);
                }
            });
    }

    collapse(): void {
        this.isExpanded.set(false);
    }

    protected onMarkdownRendered(): void {
        this.markdownRenderTick.update((tick) => tick + 1);
    }

    /** Hovering an inline citation highlights its sources and shows the preview popover. */
    protected onAnswerOver(event: Event): void {
        const chip = (event.target as HTMLElement).closest<HTMLElement>('.iris-cite');
        if (!chip) {
            return;
        }
        const numbers = parseCitationNumbers(chip.dataset.n);
        this.activeCitations.set(new Set(numbers));
        this.showCitationPopover(chip, numbers[0]);
    }

    protected onAnswerOut(event: Event): void {
        if ((event.target as HTMLElement).closest('.iris-cite')) {
            this.activeCitations.set(new Set());
            this.citationPopover.set(undefined);
        }
    }

    /** Clicking an inline citation opens the cited source, exactly like clicking its chip below. */
    protected onAnswerClick(event: Event): void {
        const chip = (event.target as HTMLElement).closest<HTMLElement>('.iris-cite');
        if (!chip) {
            return;
        }
        const sourceNumber = parseCitationNumbers(chip.dataset.n)[0] ?? 0;
        const lectureSource = this.citedLectureSource(sourceNumber);
        if (lectureSource) {
            void this.router.navigate([lectureSource.lectureUnit.link], { queryParams: lectureSource.lectureUnit.queryParams });
            return;
        }
        const entitySource = sourceNumber > 0 ? this.citedEntitySource(sourceNumber) : undefined;
        if (entitySource) {
            this.openEntitySource(entitySource);
        }
        // Streamed draft: sources arrive with the terminal update, clicks are ignored until then.
    }

    protected clearCitationHighlight(): void {
        this.activeCitations.set(new Set());
        this.citationPopover.set(undefined);
    }

    /** Hovering a source chip highlights the answer passages it supports. */
    protected setChipHighlight(sourceNumber: number | undefined): void {
        this.activeCitations.set(sourceNumber ? new Set([sourceNumber]) : new Set());
    }

    /** Resolves a citation number onto the combined numbering: lecture sources first, then entity sources. */
    private citedLectureSource(sourceNumber: number): LectureSearchResult | undefined {
        return this.sources()[sourceNumber - 1];
    }

    private citedEntitySource(sourceNumber: number): EntitySearchSource | undefined {
        return this.entitySources()[sourceNumber - this.sources().length - 1];
    }

    /** The translation key for an entity type label, e.g. `global.search.entityType.exercise`. */
    protected entityTypeLabelKey(entityType: string): string {
        return 'global.search.entityType.' + entityType;
    }

    /** The palette's icon for an entity source, so the same entity looks the same everywhere. */
    protected entityIcon(source: EntitySearchSource): IconDefinition {
        return iconForEntityType(source.entityType, source.exerciseType);
    }

    /** Opens an entity source; the link may carry a query string, so plain URL navigation is used. */
    protected openEntitySource(source: EntitySearchSource): void {
        if (source.link) {
            void this.router.navigateByUrl(source.link);
        }
    }

    private showCitationPopover(chip: HTMLElement, sourceNumber: number | undefined): void {
        const region = chip.closest('.iris-answer-region');
        if (!sourceNumber || !(region instanceof HTMLElement)) {
            this.citationPopover.set(undefined);
            return;
        }
        const chipRect = chip.getBoundingClientRect();
        const regionRect = region.getBoundingClientRect();
        const left = chipRect.left - regionRect.left + chipRect.width / 2;
        const top = chipRect.top - regionRect.top;
        const lectureSource = this.citedLectureSource(sourceNumber);
        if (lectureSource) {
            this.citationPopover.set({
                left,
                top,
                name: lectureSource.lectureUnit.name,
                meta: lectureSource.lectureUnit.displayMeta,
                icon: this.SOURCE_ICONS[lectureSource.lectureUnit.sourceType] ?? this.faFile,
            });
            return;
        }
        const entitySource = this.citedEntitySource(sourceNumber);
        if (entitySource) {
            this.citationPopover.set({
                left,
                top,
                name: entitySource.title ?? '',
                meta: entitySource.course?.name,
                icon: this.entityIcon(entitySource),
                entityTypeKey: this.entityTypeLabelKey(entitySource.entityType),
            });
            return;
        }
        this.citationPopover.set(undefined);
    }
}
