import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, forwardRef, inject, input, output, signal, viewChildren } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft, faFileLines } from '@fortawesome/free-solid-svg-icons';
import { Router, RouterLink } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { LectureSearchService } from 'app/core/navbar/global-search/services/lecture-search.service';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, debounceTime, of, switchMap } from 'rxjs';
import { SearchResultView } from 'app/core/navbar/global-search/components/views/search-result-view.directive';

const SEARCH_DEBOUNCE_MS = 300;

@Component({
    selector: 'jhi-global-search-lecture-results',
    standalone: true,
    templateUrl: 'global-search-lecture-results.component.html',
    styleUrls: ['./global-search-lecture-results.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, FaIconComponent, RouterLink],
    providers: [{ provide: SearchResultView, useExisting: forwardRef(() => GlobalSearchLectureResultsComponent) }],
})
export class GlobalSearchLectureResultsComponent extends SearchResultView {
    readonly searchQuery = input.required<string>();
    readonly selectedIndex = input<number>(-1);
    protected readonly back = output<void>();
    private readonly searchService = inject(LectureSearchService);
    private readonly router = inject(Router);
    private readonly hostElement = inject(ElementRef<HTMLElement>);
    protected readonly lectureResults = signal<LectureSearchResult[]>([]);
    readonly itemCount = computed(() => this.lectureResults().length);
    private readonly cards = viewChildren<ElementRef<HTMLAnchorElement>>('cardRef');
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faFileLines = faFileLines;

    constructor() {
        super();
        effect(() => {
            const index = this.selectedIndex();
            if (index < 0) {
                this.hostElement.nativeElement.scrollIntoView({ block: 'start' });
            } else {
                this.cards()[index]?.nativeElement.scrollIntoView({ block: 'nearest' });
            }
        });
        toObservable(this.searchQuery)
            .pipe(
                debounceTime(SEARCH_DEBOUNCE_MS),
                switchMap((query) => {
                    if (!query.trim()) {
                        return of([]);
                    }
                    return this.searchService.search(query).pipe(catchError(() => of([])));
                }),
                takeUntilDestroyed(),
            )
            .subscribe((results) => this.lectureResults.set(results));
    }

    @HostListener('window:keydown', ['$event'])
    handleKeydown(event: KeyboardEvent): void {
        const index = this.selectedIndex();
        const result = this.lectureResults()[index];
        if (event.key === 'Enter' && result) {
            event.preventDefault();
            this.router.navigateByUrl(result.lectureUnitLink);
        }
    }
}
