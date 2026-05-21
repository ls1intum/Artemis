import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, forwardRef, inject, input, output, signal, viewChild, viewChildren } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faComments, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Skeleton } from 'primeng/skeleton';
import { catchError, debounceTime, of, switchMap, tap } from 'rxjs';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { IrisSearchResult } from 'app/core/navbar/global-search/models/iris-search-result.model';
import { LectureSearchService } from 'app/core/navbar/global-search/services/lecture-search.service';
import { SEARCH_DEBOUNCE_MS, SearchResultView } from 'app/core/navbar/global-search/components/views/search-result-view.directive';

@Component({
    selector: 'jhi-global-search-iris-answer',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, FaIconComponent, Skeleton, RouterLink, IrisLogoComponent, HtmlForMarkdownPipe],
    providers: [{ provide: SearchResultView, useExisting: forwardRef(() => GlobalSearchIrisAnswerComponent) }],
    templateUrl: './global-search-iris-answer.component.html',
    styleUrls: ['./global-search-iris-answer.component.scss'],
})
export class GlobalSearchIrisAnswerComponent extends SearchResultView {
    readonly query = input.required<string>();
    readonly selectedSourceIndex = input<number>(-1);
    readonly active = input<boolean>(false);
    readonly closeDrawer = output<void>();

    private readonly lectureSearchService = inject(LectureSearchService);
    private readonly router = inject(Router);

    protected readonly result = signal<IrisSearchResult | undefined>(undefined);
    protected readonly isLoading = signal(false);
    protected readonly hasError = signal(false);

    readonly itemCount = computed(() => this.result()?.sources.length ?? 0);
    private readonly selectableItems = viewChildren<ElementRef>('selectableItem');
    private readonly contentArea = viewChild<ElementRef<HTMLElement>>('contentArea');

    protected readonly faTimes = faTimes;
    protected readonly faComments = faComments;
    protected readonly IrisLogoSize = IrisLogoSize;

    constructor() {
        super();
        effect(() => {
            const index = this.selectedSourceIndex();
            if (index < 0) {
                // Only scroll to top when the panel itself is still focused (ArrowUp past first source).
                // When the panel is deactivated (ArrowLeft), leave the scroll position as-is.
                if (this.active()) {
                    this.contentArea()?.nativeElement.scrollTo({ top: 0 });
                }
            } else {
                this.selectableItems()[index]?.nativeElement.scrollIntoView({ block: 'nearest' });
            }
        });
        toObservable(this.query)
            .pipe(
                debounceTime(SEARCH_DEBOUNCE_MS),
                tap((query) => {
                    this.hasError.set(false);
                    this.result.set(undefined);
                    if (query.trim()) {
                        this.isLoading.set(true);
                    }
                }),
                switchMap((query) => {
                    if (!query.trim()) {
                        return of(undefined);
                    }
                    return this.lectureSearchService.ask(query).pipe(
                        catchError(() => {
                            this.hasError.set(true);
                            return of(undefined);
                        }),
                    );
                }),
                takeUntilDestroyed(),
            )
            .subscribe((result) => {
                this.result.set(result);
                this.isLoading.set(false);
            });
    }

    @HostListener('window:keydown', ['$event'])
    handleKeydown(event: KeyboardEvent): void {
        if (event.key !== 'Enter') return;
        const index = this.selectedSourceIndex();
        if (index < 0) return;
        const source = this.result()?.sources[index];
        if (source) {
            event.preventDefault();
            this.router.navigateByUrl(source.lectureUnit.link);
        }
    }
}
