import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, forwardRef, inject, input, output, signal, viewChildren } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft, faFileLines } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { SkeletonModule } from 'primeng/skeleton';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { LectureSearchService } from 'app/core/navbar/global-search/services/lecture-search.service';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, debounceTime, of, switchMap, tap } from 'rxjs';
import { SEARCH_DEBOUNCE_MS, SearchResultView } from 'app/core/navbar/global-search/components/views/search-result-view.directive';
import { parseLectureDeepLink } from 'app/lecture/overview/course-lectures/lecture-deep-link.model';
import { LectureDeepLinkService } from 'app/lecture/overview/course-lectures/lecture-deep-link.service';
import { SearchOverlayService } from 'app/core/navbar/global-search/services/search-overlay.service';

@Component({
    selector: 'jhi-global-search-lecture-results',
    standalone: true,
    templateUrl: 'global-search-lecture-results.component.html',
    styleUrls: ['./global-search-lecture-results.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, FaIconComponent, SkeletonModule],
    providers: [{ provide: SearchResultView, useExisting: forwardRef(() => GlobalSearchLectureResultsComponent) }],
})
export class GlobalSearchLectureResultsComponent extends SearchResultView {
    readonly searchQuery = input.required<string>();
    readonly selectedIndex = input<number>(-1);
    protected readonly back = output<void>();
    private readonly searchService = inject(LectureSearchService);
    private readonly deepLinkService = inject(LectureDeepLinkService);
    private readonly overlay = inject(SearchOverlayService);
    private readonly hostElement = inject(ElementRef<HTMLElement>);
    protected readonly lectureResults = signal<LectureSearchResult[]>([]);
    protected readonly isLoading = signal(false);
    protected readonly hasError = signal(false);
    readonly itemCount = computed(() => this.lectureResults().length);
    private readonly selectableItems = viewChildren<ElementRef>('selectableItem');
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faFileLines = faFileLines;
    protected readonly skeletonItems = Array.from({ length: 5 });

    constructor() {
        super();
        effect(() => {
            const index = this.selectedIndex();
            if (index < 0) {
                this.hostElement.nativeElement.scrollIntoView({ block: 'start' });
            } else {
                this.selectableItems()[index]?.nativeElement.scrollIntoView({ block: 'nearest' });
            }
        });
        toObservable(this.searchQuery)
            .pipe(
                debounceTime(SEARCH_DEBOUNCE_MS),
                tap((query) => {
                    this.hasError.set(false);
                    if (query.trim()) {
                        this.isLoading.set(true);
                    }
                }),
                switchMap((query) => {
                    if (!query.trim()) {
                        return of([]);
                    }
                    return this.searchService.search(query).pipe(
                        catchError(() => {
                            this.hasError.set(true);
                            return of([]);
                        }),
                    );
                }),
                takeUntilDestroyed(),
            )
            .subscribe((results) => {
                this.lectureResults.set(results);
                this.isLoading.set(false);
            });
    }

    @HostListener('window:keydown', ['$event'])
    handleKeydown(event: KeyboardEvent): void {
        if (event.key !== 'Enter') return;
        const index = this.selectedIndex();
        if (index < 0) return;
        const result = this.lectureResults()[index];
        if (result) {
            event.preventDefault();
            this.openResult(result);
        }
    }

    /**
     * Opens the lecture unit the result points at.
     *
     * A jump that stays on the page the user is already on does not navigate, so the overlay has to be closed here —
     * the modal otherwise closes itself on the navigation that no longer happens.
     */
    protected openResult(result: LectureSearchResult): void {
        this.deepLinkService.jump(result.lectureUnit.link, parseLectureDeepLink(result.lectureUnit.queryParams));
        this.overlay.close();
    }
}
