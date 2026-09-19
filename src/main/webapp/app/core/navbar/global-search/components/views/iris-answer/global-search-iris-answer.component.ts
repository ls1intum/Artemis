import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faChevronUp, faCircleInfo, faFile, faFilePdf, faFileVideo, faVideo } from '@fortawesome/free-solid-svg-icons';
import { Router, RouterLink } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
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
 * Marker bound for STREAMED drafts: partial updates carry no sources yet, but their raw
 * markers still reference the numbered answer context (server-capped well below this),
 * so they render as chips immediately instead of flashing bracket text. The terminal
 * update replaces them with markers validated against the real sources list.
 */
const PARTIAL_CITATION_MARKER_BOUND = 20;

/**
 * How long the "nothing relevant" card stays before it folds away. It is the only thing telling the
 * reader that Iris ran and decided not to answer, and nothing is left behind afterwards, so it has
 * to outlast a glance away from the screen.
 */
const NO_ANSWER_HOLD_MS = 5400;

/**
 * Reveal delays for the streamed answer, in milliseconds, chosen from how many characters are still
 * waiting to be shown.
 *
 * Pyris delivers tokens in clumps with gaps between them, so rendering each clump on arrival is
 * visibly uneven. Draining at a fixed rate does not fix it either: any rate faster than the tokens
 * arrive empties the buffer and brings the clumping straight back. The delay therefore follows the
 * backlog, sprinting while text is queued and stretching as it runs low, and goes flat out once the
 * server has finished, where there is nothing left to run dry.
 */
const REVEAL_DELAY_MS = { sprint: 14, fast: 24, steady: 38, slow: 58, idle: 50 } as const;

/** Backlog thresholds in characters, paired with {@link REVEAL_DELAY_MS}. */
const REVEAL_BACKLOG = { large: 90, medium: 50, small: 25 } as const;

/**
 * Minimum trimmed query length before the (expensive) Iris LLM answer pipeline is triggered.
 * Queries up to the "quite short" band (<= SHORT_QUERY_MAX_LENGTH) yield poor answers and are
 * not worth an LLM call, so the card stays idle until the query is longer than that band.
 */
const IRIS_ANSWER_MIN_QUERY_LENGTH = SHORT_QUERY_MAX_LENGTH + 1;

/** What the card is currently showing. Everything the template renders follows from this. */
export type IrisAnswerPhase = 'idle' | 'thinking' | 'answering' | 'noAnswer' | 'failed';

@Component({
    selector: 'jhi-global-search-iris-answer',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, RouterLink, ArtemisTranslatePipe, IrisLogoComponent, MarkdownDirective],
    templateUrl: './global-search-iris-answer.component.html',
    styleUrls: ['./global-search-iris-answer.component.scss'],
})
export class GlobalSearchIrisAnswerComponent {
    private readonly irisSearchAnswerService = inject(IrisSearchAnswerService);
    private readonly router = inject(Router);
    private readonly destroyRef = inject(DestroyRef);

    readonly searchQuery = input.required<string>();
    /** Active course include/exclude filters from the search modal; scope the answer's retrieval the
     * same way they already scope the visible results. */
    readonly courseIds = input<number[]>([]);
    readonly excludeCourseIds = input<number[]>([]);

    private readonly answerBody = viewChild<ElementRef<HTMLElement>>('answerBody');

    protected readonly irisResult = signal<IrisSearchResult | undefined>(undefined);
    protected readonly phase = signal<IrisAnswerPhase>('idle');
    private readonly currentRunId = signal<string | undefined>(undefined);
    protected readonly isExpanded = signal(false);
    protected readonly isOverflowing = signal(false);
    protected readonly moreOpen = signal(false);
    protected readonly sources = computed(() => this.irisResult()?.sources ?? []);
    /** Entity sources (course information); their citation numbers continue after the lecture sources. */
    protected readonly entitySources = computed(() => this.irisResult()?.entitySources ?? []);
    /** Per-chip display fields precomputed once per change, so the template's `@for` does not call methods on every render. */
    protected readonly entityChipViews = computed(() =>
        this.entitySources().map((source) => ({ source, icon: this.entityIcon(source), typeLabelKey: this.entityTypeLabelKey(source.entityType) })),
    );

    /** Re-fires the pipeline for the same query when the reader retries after a failure. */
    private readonly retryAttempt = signal(0);
    /** True once the "nothing relevant" notice has had its time and the card has folded away. */
    protected readonly isDismissed = signal(false);

    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly INITIAL_VISIBLE_SOURCE_COUNT = 2;
    protected readonly faChevronUp = faChevronUp;
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faFile = faFile;

    protected readonly SOURCE_ICONS: Partial<Record<string, IconDefinition>> = {
        lecture_unit_slide: faFilePdf,
        lecture_unit_slide_video: faFileVideo,
        lecture_unit_video: faVideo,
    };

    protected readonly visibleSources = computed(() => (this.moreOpen() ? this.sources() : this.sources().slice(0, this.INITIAL_VISIBLE_SOURCE_COUNT)));

    /** How much of the buffered answer has been revealed so far, in characters. */
    private readonly revealedLength = signal(0);
    /** Where the most recently revealed chunk starts, so only that chunk fades in. */
    private readonly revealStart = signal(0);
    /** True once the server has sent its terminal update, so no further text can arrive. */
    private readonly streamComplete = signal(false);
    /**
     * Whether this run is being revealed word by word. Only a run that actually arrived in pieces is:
     * the reveal exists to smooth out uneven arrival, so pacing out an answer that was delivered whole
     * would be theatre at the reader's expense.
     */
    private readonly progressiveReveal = signal(false);
    private revealTimeout?: ReturnType<typeof setTimeout>;
    private dismissTimeout?: ReturnType<typeof setTimeout>;
    /** Highest `partialSeq` accepted for the active run; undefined until the first seq-carrying partial. */
    private lastPartialSeq?: number;

    /** The part of the answer the reader can currently see. */
    protected readonly displayedAnswer = computed(() => {
        const answer = this.irisResult()?.answer ?? '';
        return this.progressiveReveal() ? answer.slice(0, this.revealedLength()) : answer;
    });
    /**
     * Whether text is still arriving from the server, or still being revealed from the buffer. Only a
     * progressive run can be mid-flight: an answer delivered whole is shown whole, so it is never in
     * this state at all.
     */
    protected readonly isStreaming = computed(() => {
        if (this.phase() !== 'answering' || !this.progressiveReveal()) {
            return false;
        }
        return !this.streamComplete() || this.revealedLength() < (this.irisResult()?.answer?.length ?? 0);
    });
    /** Whether the answer is complete and fully shown; gates the clamp, the sources and the toggle. */
    protected readonly isSettled = computed(() => !this.isStreaming());
    /** The card is a slim strip until there is something to show inside it. */
    protected readonly isOpen = computed(() => this.phase() === 'answering' || this.phase() === 'noAnswer' || this.phase() === 'failed');

    /**
     * Answer markdown with `[n]` citation markers converted to chip elements, plus the cited numbers.
     * The most recent chunk is wrapped so it can fade in on its own; a chunk that crosses a line
     * break is left alone, since a span across a block boundary would not survive markdown rendering.
     */
    protected readonly citationView = computed(() => {
        const text = this.displayedAnswer();
        const bound = this.sources().length + this.entitySources().length || (this.isSettled() ? 0 : PARTIAL_CITATION_MARKER_BOUND);
        const start = this.revealStart();
        const tail = text.slice(start);
        const animated = this.isStreaming() && start > 0 && tail.length > 0 && !tail.includes('\n');
        return renderCitationMarkers(animated ? `${text.slice(0, start)}<span class="iris-answer-tail">${tail}</span>` : text, bound);
    });
    /** Whether the answer carries inline citations; gates the chip numbering. */
    protected readonly hasCitations = computed(() => this.citationView().citedNumbers.size > 0);
    /** Only a settled answer can be clamped: a toggle means nothing while the text is still arriving. */
    protected readonly shouldClamp = computed(() => this.isSettled() && this.isOverflowing() && !this.isExpanded());
    /** Source numbers currently highlighted, linking answer passages and source chips in both directions. */
    protected readonly activeCitations = signal<ReadonlySet<number>>(new Set());
    /** Popover state for a hovered inline citation, resolved to display fields at show time. */
    protected readonly citationPopover = signal<{ left: number; top: number; name: string; meta?: string; icon: IconDefinition; entityTypeKey?: string } | undefined>(undefined);
    /** Bumped when the lazily-rendered markdown lands, so the highlight effect re-runs over the new DOM. */
    private readonly markdownRenderTick = signal(0);

    constructor() {
        this.destroyRef.onDestroy(() => this.clearTimers());

        // Measure answer overflow once the answer has settled; reset while it is still arriving.
        // The answer is rendered by the lazily-loaded markdown directive, so its final
        // height is only known once that chunk resolves and populates the element. A
        // fixed timer can fire while the element is still empty on a cold load, leaving
        // long answers unclamped; a ResizeObserver re-measures whenever the rendered
        // content changes size, so the clamp/show-more control appears reliably.
        effect((onCleanup) => {
            const answer = this.irisResult()?.answer;
            const settled = this.isSettled();
            untracked(() => {
                this.isExpanded.set(false);
                this.moreOpen.set(false);
                if (!answer || !settled) {
                    this.isOverflowing.set(false);
                }
            });
            if (!answer || !settled) return;
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
        // A retry re-emits the same query, which is why the source carries the attempt; the course scope
        // rides along so a scoped palette re-asks when the user switches course.
        toObservable(computed(() => ({ query: this.searchQuery(), courseIds: this.courseIds(), excludeCourseIds: this.excludeCourseIds(), attempt: this.retryAttempt() })))
            .pipe(
                switchMap(({ query, courseIds, excludeCourseIds }) => {
                    this.resetRun();
                    if (query.trim().length < IRIS_ANSWER_MIN_QUERY_LENGTH) {
                        return of(undefined);
                    }
                    // timer(X) waits X ms before emitting, giving the outer switchMap time to cancel
                    // it on the next keystroke. Unlike of(query).pipe(debounceTime(X)), timer does not
                    // complete immediately — debounceTime flushes instantly when its source completes,
                    // which would bypass the debounce window entirely.
                    return timer(IRIS_ANSWER_DEBOUNCE_MS).pipe(
                        switchMap(() =>
                            this.irisSearchAnswerService.ask(query, 5, courseIds, excludeCourseIds).pipe(
                                catchError(() => {
                                    // A failure is worth saying out loud: it is the one ending the reader can act on.
                                    // A reveal timer scheduled by an earlier partial on this same run must not keep
                                    // firing against a run the UI has already moved past.
                                    this.clearTimers();
                                    this.phase.set('failed');
                                    return of(undefined);
                                }),
                            ),
                        ),
                    );
                }),
                takeUntilDestroyed(),
            )
            .subscribe((update: IrisSearchStatusUpdate | undefined) => {
                if (update === undefined) {
                    return;
                }
                if (update.isThinking) {
                    this.onThinkingUpdate(update);
                } else {
                    this.onTerminalUpdate(update);
                }
            });
    }

    /** Clears everything belonging to the previous run, including anything still on a timer. */
    private resetRun(): void {
        this.clearTimers();
        this.irisResult.set(undefined);
        this.phase.set('idle');
        this.currentRunId.set(undefined);
        this.revealedLength.set(0);
        this.revealStart.set(0);
        this.streamComplete.set(false);
        this.progressiveReveal.set(false);
        this.isDismissed.set(false);
        this.activeCitations.set(new Set());
        this.citationPopover.set(undefined);
        this.lastPartialSeq = undefined;
    }

    private clearTimers(): void {
        clearTimeout(this.revealTimeout);
        clearTimeout(this.dismissTimeout);
        this.revealTimeout = undefined;
        this.dismissTimeout = undefined;
    }

    private onThinkingUpdate(update: IrisSearchStatusUpdate): void {
        // A RUNNING update that lands after the terminal one — Iris's own partial sender
        // documents this as a best-effort race it cannot fully close on its own side — must
        // never reopen a run that has already settled: the terminal message carries no
        // partialSeq of its own for isNewerPartial() to compare against, so this is the only
        // guard standing between a late partial and silently overwriting the finished answer.
        if (this.streamComplete()) {
            return;
        }
        this.currentRunId.set(update.runId);
        if (update.partialResult === undefined) {
            if (this.phase() === 'idle') {
                this.phase.set('thinking');
            }
            return;
        }
        if (!this.isNewerPartial(update)) {
            return;
        }
        const previousLength = this.irisResult()?.answer?.length ?? 0;
        if (update.partialResult.length < previousLength) {
            // A provider retry legitimately restarts shorter (even empty) than what was already shown,
            // to wipe the stale draft; the old reveal progress no longer describes this text.
            this.revealedLength.set(0);
            this.revealStart.set(0);
        }
        this.phase.set('answering');
        this.progressiveReveal.set(true);
        this.irisResult.set({ answer: update.partialResult, sources: [] });
        this.scheduleReveal();
    }

    /**
     * Whether a streamed draft is newer than what is currently shown. Ordered by the server's
     * monotonic `partialSeq` when present: a provider retry sends a SHORTER or empty draft with a
     * HIGHER seq to clear a stale one, which a plain length comparison would wrongly reject as
     * stale. An older Iris that omits `partialSeq` falls back to length, the previous behavior.
     */
    private isNewerPartial(update: IrisSearchStatusUpdate): boolean {
        if (update.partialSeq === undefined) {
            return update.partialResult!.length > (this.irisResult()?.answer?.length ?? 0);
        }
        if (this.lastPartialSeq !== undefined && update.partialSeq <= this.lastPartialSeq) {
            return false;
        }
        this.lastPartialSeq = update.partialSeq;
        return true;
    }

    private onTerminalUpdate(update: IrisSearchStatusUpdate): void {
        if (this.currentRunId() !== undefined && update.runId !== this.currentRunId()) {
            return; // stale response from a superseded pipeline run
        }
        if (!update.answer) {
            // Iris ran and chose not to answer. Saying so and then leaving beats vanishing mid-thought,
            // which is indistinguishable from the feature being broken.
            this.phase.set('noAnswer');
            this.dismissTimeout = setTimeout(() => this.isDismissed.set(true), NO_ANSWER_HOLD_MS);
            return;
        }
        this.phase.set('answering');
        this.streamComplete.set(true);
        this.irisResult.set({ answer: update.answer, sources: update.sources ?? [], entitySources: update.entitySources ?? [] });
        if (this.progressiveReveal()) {
            this.scheduleReveal();
        }
    }

    /**
     * Reveals the next word of the buffered answer and schedules the one after it. Runs until the
     * reveal has caught up with what has arrived, then stops; the next update starts it again.
     */
    private scheduleReveal(): void {
        if (this.revealTimeout !== undefined) {
            return;
        }
        const step = () => {
            this.revealTimeout = undefined;
            const text = this.irisResult()?.answer ?? '';
            const from = this.revealedLength();
            if (from >= text.length) {
                return;
            }
            const next = this.nextRevealIndex(text, from);
            if (next > from) {
                this.revealStart.set(from);
                this.revealedLength.set(next);
            }
            if (this.revealedLength() < text.length) {
                this.revealTimeout = setTimeout(step, this.revealDelay(text.length - this.revealedLength()));
            }
        };
        this.revealTimeout = setTimeout(step, this.revealDelay((this.irisResult()?.answer?.length ?? 0) - this.revealedLength()));
    }

    /** See {@link REVEAL_DELAY_MS}: the pace follows the backlog rather than a fixed rate. */
    private revealDelay(backlog: number): number {
        if (backlog <= 0) {
            return REVEAL_DELAY_MS.idle;
        }
        if (this.streamComplete() || backlog > REVEAL_BACKLOG.large) {
            return REVEAL_DELAY_MS.sprint;
        }
        if (backlog > REVEAL_BACKLOG.medium) {
            return REVEAL_DELAY_MS.fast;
        }
        return backlog > REVEAL_BACKLOG.small ? REVEAL_DELAY_MS.steady : REVEAL_DELAY_MS.slow;
    }

    /**
     * The end of the next word, or the current position when advancing would expose half of a
     * citation marker. Holding back is safe: more text is either on its way or the stream is
     * complete, and a complete stream reveals the remainder regardless.
     */
    private nextRevealIndex(text: string, from: number): number {
        const space = text.indexOf(' ', from);
        const candidate = space === -1 ? text.length : space + 1;
        if (this.streamComplete() || !this.endsInsideMarker(text, candidate)) {
            return candidate;
        }
        return from;
    }

    private endsInsideMarker(text: string, end: number): boolean {
        const open = text.lastIndexOf('[', end - 1);
        if (open === -1) {
            return false;
        }
        const close = text.indexOf(']', open);
        return close === -1 || close >= end;
    }

    /** Re-runs the pipeline for the current query after a failure. */
    protected retry(): void {
        this.retryAttempt.update((attempt) => attempt + 1);
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

    /**
     * Enter/Space on a focused inline citation activates it exactly like a click. Only
     * preventDefault when the target actually IS a citation chip, so Space still scrolls
     * normally everywhere else in the answer region.
     */
    protected onAnswerKeydownActivate(event: KeyboardEvent): void {
        if (!(event.target as HTMLElement).closest('.iris-cite')) {
            return;
        }
        event.preventDefault();
        this.onAnswerClick(event);
    }

    protected clearCitationHighlight(): void {
        this.activeCitations.set(new Set());
        this.citationPopover.set(undefined);
    }

    /**
     * Hovering a source chip highlights the answer passages it supports. The highlight is not
     * released when the pointer leaves the chip: it has to survive the trip into the answer, or
     * reading what a source supports would need a click, and a click opens the source instead.
     * The card's own mouseleave and Escape clear it.
     */
    protected setChipHighlight(sourceNumber: number): void {
        this.activeCitations.set(new Set([sourceNumber]));
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
