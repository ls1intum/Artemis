import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faChevronUp, faFile, faFilePdf, faFileVideo, faVideo } from '@fortawesome/free-solid-svg-icons';
import { RouterLink } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { HtmlForMarkdownPipe } from 'app/foundation/pipes/html-for-markdown.pipe';
import { IrisThinkingBubbleComponent } from 'app/iris/overview/base-chatbot/iris-thinking-bubble/iris-thinking-bubble.component';
import { IrisSearchAnswerService } from 'app/core/navbar/global-search/services/iris-search-answer.service';
import { IrisSearchResult } from 'app/core/navbar/global-search/models/iris-search-result.model';
import { IrisSearchStatusUpdate } from 'app/core/navbar/global-search/models/iris-search-status-update.model';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { SEARCH_DEBOUNCE_MS } from 'app/core/navbar/global-search/components/views/search-result-view.directive';
import { catchError, of, switchMap, timer } from 'rxjs';

/** Delay in ms after a new result before measuring the rendered answer height. */
const ANSWER_MEASURE_DELAY_MS = 60;

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

@Component({
    selector: 'jhi-global-search-iris-answer',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, RouterLink, ArtemisTranslatePipe, IrisLogoComponent, HtmlForMarkdownPipe, IrisThinkingBubbleComponent],
    templateUrl: './global-search-iris-answer.component.html',
    styleUrls: ['./global-search-iris-answer.component.scss'],
})
export class GlobalSearchIrisAnswerComponent {
    private readonly irisSearchAnswerService = inject(IrisSearchAnswerService);

    readonly searchQuery = input.required<string>();

    private readonly answerBody = viewChild<ElementRef<HTMLElement>>('answerBody');

    protected readonly irisResult = signal<IrisSearchResult | undefined>(undefined);
    protected readonly irisThinking = signal(false);
    private readonly currentRunId = signal<string | undefined>(undefined);
    protected readonly isExpanded = signal(false);
    protected readonly isOverflowing = signal(false);
    protected readonly moreOpen = signal(false);
    protected readonly shouldClamp = computed(() => this.isOverflowing() && !this.isExpanded());
    protected readonly sources = computed(() => this.irisResult()?.sources ?? []);

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

    constructor() {
        // Measure answer overflow after each new result; reset when the result clears.
        effect((onCleanup) => {
            const result = this.irisResult();
            untracked(() => {
                this.isExpanded.set(false);
                this.isOverflowing.set(false);
                this.moreOpen.set(false);
            });
            if (!result?.answer) return;
            const measureTimeout = setTimeout(() => {
                const element = this.answerBody()?.nativeElement;
                if (element) {
                    const rawLineHeight = getComputedStyle(element).lineHeight;
                    const lineHeight = rawLineHeight === 'normal' ? DEFAULT_LINE_HEIGHT_PX : parseFloat(rawLineHeight);
                    untracked(() => this.isOverflowing.set(element.scrollHeight > lineHeight * CLAMP_LINE_COUNT));
                }
            }, ANSWER_MEASURE_DELAY_MS);
            onCleanup(() => clearTimeout(measureTimeout));
        });

        // Iris answer pipeline — runs alongside the main search.
        // ask() emits multiple values: first a thinking update, then the final result.
        // The outer switchMap ensures every new searchQuery emission immediately cancels
        // both the debounce timer and any in-flight ask() subscription, so a stale
        // WebSocket update from a superseded run can never reach the subscriber.
        // State is reset at the top of the outer switchMap — before the debounce window —
        // so the UI clears on every keystroke even if the request has not fired yet.
        toObservable(this.searchQuery)
            .pipe(
                switchMap((query) => {
                    this.irisResult.set(undefined);
                    this.irisThinking.set(false);
                    this.currentRunId.set(undefined);
                    if (!query.trim()) {
                        return of(undefined);
                    }
                    // timer(X) waits X ms before emitting, giving the outer switchMap time to cancel
                    // it on the next keystroke. Unlike of(query).pipe(debounceTime(X)), timer does not
                    // complete immediately — debounceTime flushes instantly when its source completes,
                    // which would bypass the debounce window entirely.
                    return timer(IRIS_ANSWER_DEBOUNCE_MS).pipe(switchMap(() => this.irisSearchAnswerService.ask(query).pipe(catchError(() => of(undefined)))));
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
                    this.irisThinking.set(true);
                } else {
                    if (this.currentRunId() !== undefined && update.runId !== this.currentRunId()) {
                        return; // stale response from a superseded pipeline run
                    }
                    this.irisThinking.set(false);
                    this.irisResult.set(update.answer ? { answer: update.answer, sources: update.sources ?? [] } : undefined);
                }
            });
    }

    collapse(): void {
        this.isExpanded.set(false);
    }
}
