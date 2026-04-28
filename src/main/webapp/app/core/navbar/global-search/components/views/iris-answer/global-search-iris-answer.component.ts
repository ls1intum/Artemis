import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronUp, faFile } from '@fortawesome/free-solid-svg-icons';
import { RouterLink } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { IrisThinkingBubbleComponent } from 'app/iris/overview/base-chatbot/iris-thinking-bubble/iris-thinking-bubble.component';
import { LectureSearchService } from 'app/core/navbar/global-search/services/lecture-search.service';
import { IrisSearchResult } from 'app/core/navbar/global-search/models/iris-search-result.model';
import { IrisSearchStatusUpdate } from 'app/core/navbar/global-search/models/iris-search-status-update.model';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { SEARCH_DEBOUNCE_MS } from 'app/core/navbar/global-search/components/views/search-result-view.directive';
import { catchError, debounceTime, of, switchMap, tap } from 'rxjs';

/** Delay in ms after a new result before measuring the rendered answer height. */
const ANSWER_MEASURE_DELAY_MS = 60;

@Component({
    selector: 'jhi-global-search-iris-answer',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, RouterLink, ArtemisTranslatePipe, IrisLogoComponent, HtmlForMarkdownPipe, IrisThinkingBubbleComponent],
    templateUrl: './global-search-iris-answer.component.html',
    styleUrls: ['./global-search-iris-answer.component.scss'],
})
export class GlobalSearchIrisAnswerComponent {
    private readonly lectureSearchService = inject(LectureSearchService);

    readonly searchQuery = input.required<string>();

    private readonly answerBody = viewChild<ElementRef<HTMLElement>>('answerBody');

    protected readonly irisResult = signal<IrisSearchResult | undefined>(undefined);
    protected readonly irisThinking = signal(false);
    protected readonly isExpanded = signal(false);
    protected readonly isOverflowing = signal(false);
    protected readonly moreOpen = signal(false);
    protected readonly shouldClamp = computed(() => this.isOverflowing() && !this.isExpanded());
    protected readonly sources = computed(() => this.irisResult()?.sources ?? []);

    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly faChevronUp = faChevronUp;
    protected readonly faFile = faFile;

    constructor() {
        // Measure answer overflow after each new result; reset when the result clears.
        effect((onCleanup) => {
            const r = this.irisResult();
            untracked(() => {
                this.isExpanded.set(false);
                this.isOverflowing.set(false);
                this.moreOpen.set(false);
            });
            if (!r?.answer) return;
            const measureTimeout = setTimeout(() => {
                const el = this.answerBody()?.nativeElement;
                if (el) {
                    const rawLineHeight = getComputedStyle(el).lineHeight;
                    const lineHeight = rawLineHeight === 'normal' ? 20 : parseFloat(rawLineHeight);
                    untracked(() => this.isOverflowing.set(el.scrollHeight > lineHeight * 4));
                }
            }, ANSWER_MEASURE_DELAY_MS);
            onCleanup(() => clearTimeout(measureTimeout));
        });

        // Iris answer pipeline — runs alongside the main search.
        // ask() emits multiple values: first a thinking update, then the final result.
        // switchMap cancels the previous ask() subscription on every new query.
        toObservable(this.searchQuery)
            .pipe(
                debounceTime(SEARCH_DEBOUNCE_MS),
                tap(() => {
                    this.irisResult.set(undefined);
                    this.irisThinking.set(false);
                }),
                switchMap((query) => {
                    if (!query.trim()) {
                        return of(undefined);
                    }
                    return this.lectureSearchService.ask(query).pipe(catchError(() => of(undefined)));
                }),
                takeUntilDestroyed(),
            )
            .subscribe((update: IrisSearchStatusUpdate | undefined) => {
                if (update === undefined) return;
                if (update.isThinking) {
                    this.irisThinking.set(true);
                } else {
                    this.irisThinking.set(false);
                    this.irisResult.set(update.answer ? { answer: update.answer, sources: update.sources ?? [] } : undefined);
                }
            });
    }

    collapse(): void {
        this.isExpanded.set(false);
    }
}
