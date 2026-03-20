import { ChangeDetectionStrategy, Component, DestroyRef, HostListener, OnDestroy, computed, effect, inject, signal, viewChild } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, catchError, debounceTime, distinctUntilChanged, filter, of, switchMap } from 'rxjs';
import { SearchOverlayService } from '../../services/search-overlay.service';
import { OsDetectorService } from '../../services/os-detector.service';
import { AccountService } from 'app/core/auth/account.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowDown, faArrowUp } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DialogModule } from 'primeng/dialog';
import { SearchView } from 'app/core/navbar/global-search/models/search-view.model';
import { GlobalSearchNavigationViewComponent } from 'app/core/navbar/global-search/components/views/navigation-view/global-search-navigation-view.component';
import { SearchResultView } from 'app/core/navbar/global-search/components/views/search-result-view.directive';
import { GlobalSearchOptions, GlobalSearchResult, GlobalSearchService } from '../../services/global-search.service';
import { SearchInputComponent } from './search-input/search-input.component';
import { SearchableEntity } from '../../models/searchable-entity.model';

@Component({
    selector: 'jhi-global-search-modal',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [DialogModule, FaIconComponent, ArtemisTranslatePipe, GlobalSearchNavigationViewComponent, SearchInputComponent],
    templateUrl: './global-search-modal.component.html',
    styleUrls: ['./global-search-modal.component.scss'],
})
export class GlobalSearchModalComponent implements OnDestroy {
    protected readonly overlay = inject(SearchOverlayService);
    private readonly osDetector = inject(OsDetectorService);
    private readonly accountService = inject(AccountService);
    private readonly router = inject(Router);
    private readonly searchService = inject(GlobalSearchService);
    private readonly destroyRef = inject(DestroyRef);
    protected readonly faArrowUp = faArrowUp;
    protected readonly faArrowDown = faArrowDown;
    protected readonly searchInputComponent = viewChild<SearchInputComponent>(SearchInputComponent);
    protected readonly currentView = signal(SearchView.Navigation);
    protected readonly SearchView = SearchView;
    protected readonly searchQuery = signal('');
    protected readonly activeFilters = signal<string[]>([]);
    protected readonly results = signal<GlobalSearchResult[]>([]);
    protected readonly isLoading = signal<boolean>(false);
    protected readonly hasSearched = signal<boolean>(false);
    protected readonly searchError = signal<string | undefined>(undefined);
    protected readonly selectedIndex = signal(-1);
    private readonly activeView = viewChild(SearchResultView);
    private readonly maxIndex = computed(() => (this.activeView()?.itemCount() ?? 0) - 1);
    private searchSubject = new Subject<string>();

    // Computed properties
    protected hasResults = computed(() => this.results().length > 0);
    protected showResults = computed(() => this.isLoading() || (this.hasSearched() && (this.hasResults() || this.activeFilters().length > 0)));

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

        // Set up search debouncing with RxJS
        this.searchSubject
            .pipe(
                debounceTime(300),
                distinctUntilChanged(),
                switchMap((query) => {
                    const hasFilter = this.activeFilters().length > 0;
                    const trimmedQuery = query?.trim() || '';
                    const hasValidQuery = trimmedQuery.length >= 2;

                    // Clear results if no valid query and no filter
                    if (!hasValidQuery && !hasFilter) {
                        this.results.set([]);
                        this.hasSearched.set(false);
                        this.isLoading.set(false);
                        this.searchError.set(undefined);
                        return of([]);
                    }

                    this.isLoading.set(true);
                    this.searchError.set(undefined);
                    const typeFilter = hasFilter ? this.activeFilters()[0] : undefined;

                    // If no valid query but has filter, fetch recent items
                    const searchQuery = hasValidQuery ? trimmedQuery : '';
                    const options: GlobalSearchOptions = { type: typeFilter };

                    if (!hasValidQuery) {
                        // Fetching recent items when filter is active but query is empty
                        options.sortBy = 'dueDate';
                        options.limit = 10;
                    }

                    return this.searchService.search(searchQuery, options).pipe(
                        catchError(() => {
                            this.isLoading.set(false);
                            this.searchError.set('global.search.searchFailed');
                            return of([]);
                        }),
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

        // Reset state when modal is closed
        effect(() => {
            if (!this.overlay.isOpen()) {
                this.resetSearch();
            }
        });
    }

    protected onSearchInput(query: string): void {
        this.searchQuery.set(query);
        this.searchError.set(undefined);

        // Show skeleton immediately while debounce waits, for a responsive feel
        const trimmedQuery = query?.trim() || '';
        const hasFilter = this.activeFilters().length > 0;
        if (trimmedQuery.length >= 2 || hasFilter) {
            this.isLoading.set(true);
        }

        this.searchSubject.next(query);
    }

    protected onSearchKeyDown(event: KeyboardEvent) {
        // If backspace is pressed and input is empty, remove the rightmost filter
        if (event.key === 'Backspace' && this.searchQuery() === '') {
            const filters = this.activeFilters();
            if (filters.length > 0) {
                // Remove the rightmost (last) filter
                this.removeFilter(filters[filters.length - 1]);
            }
        }
    }

    protected addFilter(filterType: string) {
        // For now, only one filter at a time (can be extended later)
        if (!this.activeFilters().includes(filterType)) {
            this.activeFilters.set([filterType]);
            // Re-trigger search with new filter
            this.searchSubject.next(this.searchQuery());
        }
    }

    protected removeFilter(filterType: string) {
        this.activeFilters.set(this.activeFilters().filter((f) => f !== filterType));

        // If no filters remain and no query, reset to initial state
        if (this.activeFilters().length === 0 && !this.searchQuery()) {
            this.results.set([]);
            this.hasSearched.set(false);
            this.isLoading.set(false);
        } else {
            // Re-trigger search to update results
            this.searchSubject.next(this.searchQuery());
        }
    }

    protected onEntityClick(entity: SearchableEntity) {
        if (!entity.enabled) {
            return;
        }

        // Add the filter
        if (entity.filterTag) {
            this.addFilter(entity.filterTag);
        }

        // Load recent items for this entity type
        this.loadRecentItems(entity.filterTag);

        // Keep search input focused so user can start typing immediately
        this.focusInput();
    }

    private loadRecentItems(type?: string) {
        if (!type) return;

        this.isLoading.set(true);
        this.searchError.set(undefined);
        // Search with empty query but with filter to get recent items
        this.searchService
            .search('', { type, sortBy: 'dueDate', limit: 10 })
            .pipe(
                catchError(() => {
                    this.isLoading.set(false);
                    this.searchError.set('global.search.searchFailed');
                    return of([]);
                }),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((results) => {
                this.results.set(results);
                this.selectedIndex.set(-1);
                this.isLoading.set(false);
                this.hasSearched.set(true);
            });
    }

    private resetSearch() {
        this.searchQuery.set('');
        this.activeFilters.set([]);
        this.results.set([]);
        this.selectedIndex.set(-1);
        this.hasSearched.set(false);
        this.isLoading.set(false);
        this.searchError.set(undefined);
        this.currentView.set(SearchView.Navigation);
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
