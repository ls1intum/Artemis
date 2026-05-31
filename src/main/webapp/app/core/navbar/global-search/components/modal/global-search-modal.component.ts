import { ChangeDetectionStrategy, Component, HostListener, OnDestroy, computed, effect, inject, signal, untracked, viewChild, viewChildren } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, filter, of, switchMap, tap, timer } from 'rxjs';
import { SearchOverlayService } from '../../services/search-overlay.service';
import { OsDetectorService } from '../../services/os-detector.service';
import { AccountService } from 'app/core/auth/account.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowDown, faArrowUp } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DialogModule } from 'primeng/dialog';
import { SearchView } from 'app/core/navbar/global-search/models/search-view.model';
import { GlobalSearchNavigationViewComponent } from 'app/core/navbar/global-search/components/views/navigation-view/global-search-navigation-view.component';
import { SEARCH_DEBOUNCE_MS, SearchResultView } from 'app/core/navbar/global-search/components/views/search-result-view.directive';
import { GlobalSearchResult } from 'app/openapi/model/globalSearchResult';
import { GlobalSearchApiService } from 'app/openapi/api/globalSearchApi.service';
import { SearchInputComponent } from './search-input/search-input.component';
import { SearchEntityType, SearchableEntity } from '../../models/searchable-entity.model';
import { GlobalSearchLectureResultsComponent } from 'app/core/navbar/global-search/components/views/lecture-results/global-search-lecture-results.component';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { TranslateService } from '@ngx-translate/core';

interface SearchState {
    query: string;
    filters: SearchEntityType[];
}

@Component({
    selector: 'jhi-global-search-modal',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [DialogModule, FaIconComponent, ArtemisTranslatePipe, GlobalSearchNavigationViewComponent, GlobalSearchLectureResultsComponent, SearchInputComponent],
    templateUrl: './global-search-modal.component.html',
    styleUrls: ['./global-search-modal.component.scss'],
})
export class GlobalSearchModalComponent implements OnDestroy {
    protected readonly overlay = inject(SearchOverlayService);
    private readonly osDetector = inject(OsDetectorService);
    private readonly accountService = inject(AccountService);
    private readonly router = inject(Router);
    private readonly searchService = inject(GlobalSearchApiService);
    private readonly courseStorageService = inject(CourseStorageService);
    private readonly translateService = inject(TranslateService);
    protected readonly faArrowUp = faArrowUp;
    protected readonly faArrowDown = faArrowDown;
    protected readonly searchInputComponent = viewChild<SearchInputComponent>(SearchInputComponent);
    protected readonly currentView = signal(SearchView.Navigation);
    protected readonly SearchView = SearchView;
    protected readonly searchQuery = signal('');
    protected readonly activeFilters = signal<SearchEntityType[]>([]);
    protected readonly activeCourseId = signal<number | undefined>(undefined);
    protected readonly activeCourseLabel = signal<string | undefined>(undefined);
    protected readonly results = signal<GlobalSearchResult[]>([]);
    protected readonly isLoading = signal<boolean>(false);
    protected readonly hasSearched = signal<boolean>(false);
    protected readonly searchError = signal<string | undefined>(undefined);
    protected readonly selectedIndex = signal(-1);
    private readonly allViews = viewChildren(SearchResultView);
    private readonly maxIndex = computed(() => (this.allViews()[0]?.itemCount() ?? 0) - 1);
    private readonly searchSubject = new Subject<SearchState | null>();
    // Cache for placeholder results (empty-query + filter) so re-adding a filter serves from cache
    private readonly placeholderCache = new Map<string, GlobalSearchResult[]>();

    // Computed properties
    protected hasResults = computed(() => this.results().length > 0);
    protected showResults = computed(() => this.isLoading() || this.hasSearched());

    ngOnDestroy(): void {
        if (this.overlay.isOpen()) {
            this.overlay.close();
        }
    }

    constructor() {
        // Reset selection whenever the query changes; reading searchQuery() registers it as a reactive dependency.
        effect(() => {
            this.searchQuery();
            this.selectedIndex.set(-1);
        });

        // Search pipeline: switchMap + timer acts as a cancellable debounce.
        // Emitting null (reset) cancels any pending debounce timer via switchMap unsubscription.
        this.searchSubject
            .pipe(
                switchMap((event) => {
                    // Null (reset) — execute synchronously, cancel any pending debounce
                    if (event === null) {
                        this.results.set([]);
                        this.hasSearched.set(false);
                        this.isLoading.set(false);
                        this.searchError.set(undefined);
                        return EMPTY;
                    }

                    const { query, filters } = event;
                    const hasFilter = filters.length > 0;
                    const hasCourseFilter = this.activeCourseId() !== undefined;
                    const trimmedQuery = query?.trim() || '';
                    const hasValidQuery = trimmedQuery.length >= 2;

                    // No valid query and no filter (type or course) — clear results synchronously
                    if (!hasValidQuery && !hasFilter && !hasCourseFilter) {
                        this.results.set([]);
                        this.hasSearched.set(false);
                        this.isLoading.set(false);
                        this.searchError.set(undefined);
                        return EMPTY;
                    }

                    this.searchError.set(undefined);
                    const typeFilter = hasFilter ? filters.join(',') : undefined;
                    const searchQuery = hasValidQuery ? trimmedQuery : '';
                    const courseId = this.activeCourseId();
                    const cacheKey = `${typeFilter ?? ''}_${courseId ?? ''}`;

                    // Empty query with filter — serve from cache synchronously if available
                    if (!hasValidQuery && (hasFilter || hasCourseFilter)) {
                        const cached = this.placeholderCache.get(cacheKey);
                        if (cached) {
                            this.isLoading.set(false);
                            return of(cached);
                        }
                    }

                    // Network search — debounce, then fire HTTP request
                    this.isLoading.set(true);
                    return timer(SEARCH_DEBOUNCE_MS).pipe(
                        switchMap(() =>
                            this.searchService.globalSearch(searchQuery, typeFilter, courseId).pipe(
                                tap((results) => {
                                    if (!hasValidQuery && (hasFilter || hasCourseFilter)) {
                                        this.placeholderCache.set(cacheKey, results);
                                    }
                                }),
                                catchError(() => {
                                    this.isLoading.set(false);
                                    this.searchError.set('global.search.searchFailed');
                                    return of([]);
                                }),
                            ),
                        ),
                    );
                }),
                takeUntilDestroyed(),
            )
            .subscribe((results) => {
                this.results.set(results);
                this.selectedIndex.set(-1);
                this.isLoading.set(false);
                this.hasSearched.set(true);
            });

        this.router.events
            .pipe(
                filter((e) => e instanceof NavigationEnd),
                takeUntilDestroyed(),
            )
            .subscribe(() => {
                if (this.overlay.isOpen()) {
                    this.overlay.close();
                }
            });

        // Reset state when modal is closed; apply context filters when opened.
        // untracked() prevents signals read inside applyContextFilters/resetSearch
        // (e.g. activeCourseId, activeFilters) from becoming reactive dependencies
        // of this effect — only overlay.isOpen() should trigger it.
        effect(() => {
            const isOpen = this.overlay.isOpen();
            untracked(() => {
                if (isOpen) {
                    this.applyContextFilters();
                } else {
                    this.resetSearch();
                }
            });
        });
    }

    /**
     * Maps route segments (e.g. 'exercises') to search filter tags (e.g. 'exercise').
     */
    private static readonly ROUTE_TO_FILTER_TAG: Record<string, SearchEntityType> = {
        exercises: 'exercise',
        lectures: 'lecture',
        exams: 'exam',
        communication: 'channel',
        faq: 'faq',
    };

    /**
     * Parses the current URL to detect course context and tab,
     * then pre-populates the course filter and type filter accordingly
     * and triggers a search so results are displayed immediately.
     */
    private applyContextFilters(): void {
        const url = this.router.url;
        // Match /courses/:courseId and optionally /:tab
        const match = url.match(/\/courses\/(\d+)(?:\/([^/?#]+))?/);
        if (!match) {
            return;
        }

        const courseId = Number(match[1]);
        const tabSegment = match[2];

        // Set course filter
        const course = this.courseStorageService.getCourse(courseId);
        const courseLabel = course?.title ?? this.translateService.instant('global.search.courseFallbackLabel', { id: courseId });
        this.activeCourseId.set(courseId);
        this.activeCourseLabel.set(courseLabel);

        // Set type filter based on the active tab
        const newFilters: SearchEntityType[] = [];
        if (tabSegment) {
            const filterTag = GlobalSearchModalComponent.ROUTE_TO_FILTER_TAG[tabSegment];
            if (filterTag) {
                newFilters.push(filterTag);
            }
        }
        this.activeFilters.set(newFilters);

        // Use local variable instead of reading the signal to avoid making activeFilters
        // a reactive dependency of the enclosing effect — which would cause the effect to
        // re-run (and re-apply context filters) every time the user removes a filter chip.
        this.searchSubject.next({ query: '', filters: newFilters });
    }

    protected onSearchInput(query: string): void {
        this.searchQuery.set(query);
        this.searchError.set(undefined);

        // Show skeleton immediately while debounce waits, for a responsive feel
        const trimmedQuery = query?.trim() || '';
        const hasFilter = this.activeFilters().length > 0 || this.activeCourseId() !== undefined;
        if (trimmedQuery.length >= 2 || hasFilter) {
            this.isLoading.set(true);
        }

        this.searchSubject.next({ query, filters: this.activeFilters() });
    }

    protected onSearchKeyDown(event: KeyboardEvent) {
        this.handleKeyboardEvent(event);
    }

    /**
     * Called by the search-input component when Backspace is pressed on an empty input.
     * Removes filters in reverse order: type filters first, then course filter.
     */
    protected onBackspaceRemoveFilter() {
        const filters = this.activeFilters();
        if (filters.length > 0) {
            this.removeFilter(filters[filters.length - 1]);
        } else if (this.activeCourseId() !== undefined) {
            this.removeCourseFilter();
        }
    }

    protected addFilter(filterTypes: SearchEntityType[]) {
        // For now, only one filter group at a time (can be extended later)
        const current = this.activeFilters();
        if (filterTypes.length !== current.length || filterTypes.some((t) => !current.includes(t))) {
            this.activeFilters.set(filterTypes);

            // Only show loading if we don't have cached placeholder results
            const query = this.searchQuery()?.trim() || '';
            const cacheKey = `${filterTypes.join(',')}_${this.activeCourseId() ?? ''}`;
            const hasCached = !query && this.placeholderCache.has(cacheKey);
            if (!hasCached) {
                this.isLoading.set(true);
            }

            // Re-trigger search with new filter
            this.searchSubject.next({ query: this.searchQuery(), filters: filterTypes });
        }
    }

    protected removeFilter(filterType: SearchEntityType) {
        const newFilters = this.activeFilters().filter((f) => f !== filterType);
        this.activeFilters.set(newFilters);
        this.searchSubject.next({ query: this.searchQuery(), filters: newFilters });
    }

    protected removeCourseFilter() {
        this.activeCourseId.set(undefined);
        this.activeCourseLabel.set(undefined);
        this.placeholderCache.clear();
        this.searchSubject.next({ query: this.searchQuery(), filters: this.activeFilters() });
    }

    protected onEntityClick(entity: SearchableEntity) {
        if (!entity.enabled) {
            return;
        }

        // Add the filter — this pushes through the main debounced pipeline
        if (entity.filterTags?.length) {
            this.addFilter(entity.filterTags);
        }

        // Keep search input focused so user can start typing immediately
        this.focusInput();
    }

    private resetSearch() {
        this.searchSubject.next(null);
        this.searchQuery.set('');
        this.activeFilters.set([]);
        this.activeCourseId.set(undefined);
        this.activeCourseLabel.set(undefined);
        this.results.set([]);
        this.selectedIndex.set(-1);
        this.hasSearched.set(false);
        this.isLoading.set(false);
        this.searchError.set(undefined);
        this.currentView.set(SearchView.Navigation);
        this.placeholderCache.clear();
    }

    protected focusInput() {
        // setTimeout(0) defers focus until after PrimeNG's dialog focus trap has run
        setTimeout(() => {
            this.searchInputComponent()?.focusInput();
        }, 0);
    }

    @HostListener('window:keydown', ['$event'])
    handleKeyboardEvent(event: KeyboardEvent) {
        if (this.isToggleShortcut(event)) {
            event.preventDefault();
            this.overlay.toggle();
            return;
        }
        if (!this.overlay.isOpen()) return;

        switch (event.key) {
            case 'Escape':
                event.preventDefault();
                if (this.currentView() !== SearchView.Navigation) {
                    this.navigateTo(SearchView.Navigation);
                } else {
                    this.overlay.close();
                }
                break;
            case 'ArrowDown':
                event.preventDefault();
                this.selectedIndex.update((i) => Math.min(i + 1, this.maxIndex()));
                break;
            case 'ArrowUp':
                event.preventDefault();
                this.selectedIndex.update((i) => Math.max(i - 1, -1));
                break;
        }
    }

    private isToggleShortcut(event: KeyboardEvent): boolean {
        return event.key.toLowerCase() === 'k' && this.osDetector.isActionKey(event) && this.accountService.isAuthenticated() && !event.repeat;
    }

    protected navigateTo(view: SearchView) {
        this.currentView.set(view);
        this.selectedIndex.set(-1);
    }
}
