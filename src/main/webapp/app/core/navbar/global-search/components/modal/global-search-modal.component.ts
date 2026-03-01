import { Component, HostListener, OnDestroy, computed, effect, inject, signal, viewChild } from '@angular/core';
import { SearchOverlayService } from '../../services/search-overlay.service';
import { OsDetectorService } from '../../services/os-detector.service';
import { AccountService } from 'app/core/auth/account.service';
import {
    faBook,
    faCalendarAlt,
    faChartBar,
    faCheckDouble,
    faComments,
    faCube,
    faFileUpload,
    faFont,
    faHashtag,
    faKeyboard,
    faProjectDiagram,
    faQuestion,
    faSearch,
    faUsers,
} from '@fortawesome/free-solid-svg-icons';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { DialogModule } from 'primeng/dialog';
import { GlobalSearchResult, GlobalSearchService } from '../../services/global-search.service';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { SearchInputComponent } from './search-input/search-input.component';
import { SearchResultItemComponent } from './search-result-item/search-result-item.component';
import { SearchableEntityItemComponent } from './searchable-entity-item/searchable-entity-item.component';
import { KeyboardHintsComponent } from './keyboard-hints/keyboard-hints.component';
import { SearchEmptyStatesComponent } from './search-empty-states/search-empty-states.component';

export interface SearchableEntity {
    id: string;
    title: string;
    description: string;
    icon: IconDefinition;
    type: 'page' | 'feature' | 'course';
    enabled: boolean;
    filterTag?: string;
}

@Component({
    selector: 'jhi-global-search-modal',
    standalone: true,
    imports: [DialogModule, SearchInputComponent, SearchResultItemComponent, SearchableEntityItemComponent, KeyboardHintsComponent, SearchEmptyStatesComponent],
    templateUrl: './global-search-modal.component.html',
    styleUrls: ['./global-search-modal.component.scss'],
})
export class GlobalSearchModalComponent implements OnDestroy {
    protected readonly overlay = inject(SearchOverlayService);
    private readonly osDetector = inject(OsDetectorService);
    private readonly accountService = inject(AccountService);
    private readonly searchService = inject(GlobalSearchService);
    private readonly router = inject(Router);

    // Icons
    protected readonly faKeyboard = faKeyboard;
    protected readonly faProjectDiagram = faProjectDiagram;
    protected readonly faFont = faFont;
    protected readonly faFileUpload = faFileUpload;
    protected readonly faCheckDouble = faCheckDouble;
    protected readonly faQuestion = faQuestion;
    protected readonly faSearch = faSearch;

    // Search state
    protected searchQuery = signal<string>('');
    protected activeFilters = signal<string[]>([]);
    protected results = signal<GlobalSearchResult[]>([]);
    protected selectedIndex = signal<number>(0);
    protected isLoading = signal<boolean>(false);
    protected hasSearched = signal<boolean>(false);

    protected searchInputComponent = viewChild<SearchInputComponent>(SearchInputComponent);

    // Searchable entities for initial view
    protected searchableEntities: SearchableEntity[] = [
        {
            id: 'exercises',
            title: 'Exercises',
            description: 'View and complete course exercises',
            icon: faCube,
            type: 'page',
            enabled: true,
            filterTag: 'exercise',
        },
        {
            id: 'lectures',
            title: 'Lecture Details',
            description: 'View lecture content and units',
            icon: faBook,
            type: 'page',
            enabled: false,
        },
        {
            id: 'communication',
            title: 'Communication',
            description: 'Chat with classmates and instructors',
            icon: faComments,
            type: 'page',
            enabled: false,
        },
        {
            id: 'iris',
            title: 'Dashboard with Iris',
            description: 'Chat with Iris AI assistant',
            icon: faHashtag,
            type: 'page',
            enabled: false,
        },
        {
            id: 'users',
            title: 'User Management',
            description: 'Manage users and permissions',
            icon: faUsers,
            type: 'page',
            enabled: false,
        },
        {
            id: 'statistics',
            title: 'Statistics',
            description: 'View course statistics and analytics',
            icon: faChartBar,
            type: 'feature',
            enabled: false,
        },
        {
            id: 'calendar',
            title: 'Calendar',
            description: 'View your schedule and deadlines',
            icon: faCalendarAlt,
            type: 'feature',
            enabled: false,
        },
    ];

    // Search debouncing
    private searchSubject = new Subject<string>();

    // Computed properties
    protected hasResults = computed(() => this.results().length > 0);
    protected showEmptyState = computed(() => this.hasSearched() && !this.isLoading() && !this.hasResults());
    protected showInitialState = computed(() => !this.searchQuery() && !this.hasSearched() && this.activeFilters().length === 0);

    constructor() {
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
                        return of([]);
                    }

                    this.isLoading.set(true);
                    const typeFilter = hasFilter ? this.activeFilters()[0] : undefined;

                    // If no valid query but has filter, fetch recent items
                    // Otherwise perform normal search
                    const searchQuery = hasValidQuery ? trimmedQuery : '';
                    const options: any = { type: typeFilter };

                    if (!hasValidQuery) {
                        // Fetching recent items when filter is active but query is empty
                        options.sortBy = 'dueDate';
                        options.limit = 10;
                    }

                    return this.searchService.search(searchQuery, options).pipe(
                        catchError(() => {
                            this.isLoading.set(false);
                            return of([]);
                        }),
                    );
                }),
                takeUntilDestroyed(),
            )
            .subscribe((results) => {
                this.results.set(results);
                this.selectedIndex.set(0);
                this.isLoading.set(false);
                this.hasSearched.set(true);
            });

        // Reset state when modal is closed
        effect(() => {
            if (!this.overlay.isOpen()) {
                this.resetSearch();
            }
        });
    }

    protected focusInput() {
        this.searchInputComponent()?.focusInput();
    }

    protected onSearchInput(query: string) {
        this.searchQuery.set(query);
        this.searchSubject.next(query);
    }

    protected onSearchKeyDown(event: KeyboardEvent) {
        // If backspace is pressed and input is empty, remove the rightmost filter
        if (event.key === 'Backspace') {
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
            if (this.searchQuery()) {
                this.searchSubject.next(this.searchQuery());
            }
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
        // Search with empty query but with filter to get recent items
        this.searchService
            .search('', { type, sortBy: 'dueDate', limit: 10 })
            .pipe(
                catchError(() => {
                    this.isLoading.set(false);
                    return of([]);
                }),
            )
            .subscribe((results) => {
                this.results.set(results);
                this.selectedIndex.set(0);
                this.isLoading.set(false);
                this.hasSearched.set(true);
            });
    }

    protected navigateToResult(result: GlobalSearchResult) {
        if (result.type === 'exercise' && result.id) {
            // Navigate to exercise detail page
            // You may need to adjust this based on your routing structure
            const courseId = result.metadata['courseId'];
            if (courseId) {
                this.router.navigate(['/courses', courseId, 'exercises', result.id]);
            }
        }
        this.overlay.close();
    }

    protected selectResult(index: number) {
        const result = this.results()[index];
        if (result) {
            this.navigateToResult(result);
        }
    }

    protected moveSelection(direction: 'up' | 'down') {
        // Determine if we're navigating entities or results
        const isInitialState = this.showInitialState();
        const itemCount = isInitialState ? this.searchableEntities.length : this.results().length;

        if (itemCount === 0) return;

        const currentIndex = this.selectedIndex();

        if (direction === 'down') {
            this.selectedIndex.set((currentIndex + 1) % itemCount);
        } else {
            this.selectedIndex.set((currentIndex - 1 + itemCount) % itemCount);
        }

        // Scroll selected item into view
        this.scrollSelectedIntoView(isInitialState);
    }

    private scrollSelectedIntoView(isEntityList = false) {
        setTimeout(() => {
            const selector = isEntityList ? '.entity-item.selected' : '.search-result-item.selected';
            const selectedElement = document.querySelector(selector);
            if (selectedElement) {
                selectedElement.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            }
        }, 0);
    }

    protected selectEntity(index: number) {
        const entity = this.searchableEntities[index];
        if (entity && entity.enabled) {
            this.onEntityClick(entity);
        }
    }

    protected getIconForType(type: string, badge?: string) {
        if (type === 'exercise') {
            if (badge === 'Programming') return this.faKeyboard;
            if (badge === 'Modeling') return this.faProjectDiagram;
            if (badge === 'Text') return this.faFont;
            if (badge === 'File Upload') return this.faFileUpload;
            if (badge === 'Quiz') return this.faCheckDouble;
            return this.faQuestion;
        }
        return this.faSearch;
    }

    private resetSearch() {
        this.searchQuery.set('');
        this.activeFilters.set([]);
        this.results.set([]);
        this.selectedIndex.set(0);
        this.hasSearched.set(false);
        this.isLoading.set(false);
    }

    @HostListener('window:keydown', ['$event'])
    handleKeyboardEvent(event: KeyboardEvent) {
        // Global keyboard shortcuts
        if (this.isToggleShortcut(event)) {
            event.preventDefault();
            this.overlay.toggle();
            return;
        }

        // Modal is not open, ignore other shortcuts
        if (!this.overlay.isOpen()) {
            return;
        }

        // ESC to close
        if (event.key === 'Escape') {
            event.preventDefault();
            this.overlay.close();
            return;
        }

        // Arrow navigation
        if (event.key === 'ArrowDown') {
            event.preventDefault();
            this.moveSelection('down');
            return;
        }

        if (event.key === 'ArrowUp') {
            event.preventDefault();
            this.moveSelection('up');
            return;
        }

        // Enter to select
        if (event.key === 'Enter') {
            event.preventDefault();
            if (this.showInitialState()) {
                // Select entity in initial state
                this.selectEntity(this.selectedIndex());
            } else if (this.hasResults()) {
                // Select result in search results
                this.selectResult(this.selectedIndex());
            }
            return;
        }

        // # to add exercise filter
        if (event.key === '#') {
            event.preventDefault();
            if (this.showInitialState()) {
                // If in initial state, select exercises entity
                const exerciseEntity = this.searchableEntities.find((e) => e.id === 'exercises');
                if (exerciseEntity) {
                    this.onEntityClick(exerciseEntity);
                }
            } else if (!this.activeFilters().includes('exercise')) {
                // Otherwise, just add the filter
                this.addFilter('exercise');
            }
        }
    }

    private isToggleShortcut(event: KeyboardEvent): boolean {
        return event.key.toLowerCase() === 'k' && this.osDetector.isActionKey(event) && this.accountService.isAuthenticated() && !event.repeat;
    }

    ngOnDestroy() {
        if (this.overlay.isOpen()) {
            this.overlay.close();
        }
    }
}
