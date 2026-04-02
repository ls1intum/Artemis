import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, forwardRef, inject, input, output, signal, viewChildren } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft, faFileLines } from '@fortawesome/free-solid-svg-icons';
import { Router, RouterLink } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SkeletonModule } from 'primeng/skeleton';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { LectureSearchService } from 'app/core/navbar/global-search/services/lecture-search.service';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, debounceTime, of, switchMap, tap } from 'rxjs';
import { SEARCH_DEBOUNCE_MS, SearchResultView } from 'app/core/navbar/global-search/components/views/search-result-view.directive';
import { GlobalSearchActionItemComponent } from 'app/core/navbar/global-search/components/action-item/global-search-action-item.component';
import { SearchView } from 'app/core/navbar/global-search/models/search-view.model';
import { MODULE_FEATURE_IRIS } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';

@Component({
    selector: 'jhi-global-search-lecture-results',
    standalone: true,
    templateUrl: 'global-search-lecture-results.component.html',
    styleUrls: ['./global-search-lecture-results.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, FaIconComponent, RouterLink, SkeletonModule, GlobalSearchActionItemComponent, IrisLogoComponent],
    providers: [{ provide: SearchResultView, useExisting: forwardRef(() => GlobalSearchLectureResultsComponent) }],
})
export class GlobalSearchLectureResultsComponent extends SearchResultView {
    readonly searchQuery = input.required<string>();
    readonly selectedIndex = input<number>(-1);
    readonly irisOpen = input<boolean>(false);
    protected readonly back = output<void>();
    protected readonly viewSelected = output<SearchView>();
    private readonly searchService = inject(LectureSearchService);
    private readonly router = inject(Router);
    private readonly hostElement = inject(ElementRef<HTMLElement>);
    private readonly profileService = inject(ProfileService);
    protected readonly lectureResults = signal<LectureSearchResult[]>([]);
    protected readonly isLoading = signal(false);
    protected readonly hasError = signal(false);
    protected readonly irisEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);
    readonly itemCount = computed(() => (this.irisEnabled && !this.irisOpen() ? 1 : 0) + this.lectureResults().length);
    private readonly selectableItems = viewChildren<ElementRef>('selectableItem');
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faFileLines = faFileLines;
    protected readonly skeletonItems = Array.from({ length: 5 });
    protected readonly SearchView = SearchView;
    protected readonly IrisLogoSize = IrisLogoSize;

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
        // Iris button visible when iris is enabled and the split view is not already open
        if (this.irisEnabled && !this.irisOpen() && index === 0) {
            event.preventDefault();
            this.viewSelected.emit(SearchView.Iris);
            return;
        }
        // Offset by 1 if iris button is visible
        const resultIndex = this.irisEnabled && !this.irisOpen() ? index - 1 : index;
        const result = this.lectureResults()[resultIndex];
        if (result) {
            event.preventDefault();
            this.router.navigateByUrl(result.lectureUnit.link);
        }
    }
}
