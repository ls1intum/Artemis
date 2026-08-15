import { ChangeDetectionStrategy, Component, HostListener, OnDestroy, computed, effect, inject, signal, untracked, viewChild, viewChildren } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, filter, of, switchMap, tap, timer } from 'rxjs';
import { SearchOverlayService } from '../../services/search-overlay.service';
import { OsDetectorService } from '../../services/os-detector.service';
import { GlobalSearchFilterService } from '../../services/global-search-filter.service';
import { AccountService } from 'app/core/auth/account.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowDown, faArrowUp } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DialogModule } from 'primeng/dialog';
import { SearchView } from 'app/core/navbar/global-search/models/search-view.model';
import { GlobalSearchNavigationViewComponent } from 'app/core/navbar/global-search/components/views/navigation-view/global-search-navigation-view.component';
import { MIN_SEARCH_QUERY_LENGTH, SEARCH_DEBOUNCE_MS, SearchResultView } from 'app/core/navbar/global-search/components/views/search-result-view.directive';
import { GlobalSearchResult } from 'app/openapi/model/global-search-result';
import { GlobalSearchApi } from 'app/openapi/api/global-search-api';
import { SearchInputComponent } from './search-input/search-input.component';
import { SearchEntityType, SearchableEntity } from '../../models/searchable-entity.model';
import { FilterToken } from '../../models/search-token.model';
import { removeTokenAt } from '../../models/search-token.util';
import { parseOperator } from '../../models/search-operator.util';
import { GlobalSearchLectureResultsComponent } from 'app/core/navbar/global-search/components/views/lecture-results/global-search-lecture-results.component';

interface SearchState {
    query: string;
}

@Component({
    selector: 'jhi-global-search-modal',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [DialogModule, FaIconComponent, ArtemisTranslatePipe, GlobalSearchNavigationViewComponent, GlobalSearchLectureResultsComponent, SearchInputComponent],
    providers: [GlobalSearchFilterService],
    templateUrl: './global-search-modal.component.html',
    styleUrls: ['./global-search-modal.component.scss'],
})
export class GlobalSearchModalComponent implements OnDestroy {
    protected readonly overlay = inject(SearchOverlayService);
    private readonly osDetector = inject(OsDetectorService);
    private readonly accountService = inject(AccountService);
    private readonly router = inject(Router);
    private readonly searchService = inject(GlobalSearchApi);
    // Owns the filter composition (tokens, value menu, guided picker, chip edit). The component keeps the search
    // pipeline, keyboard navigation, results, view, and overlay; it wires the two side-effects the store needs.
    protected readonly filter = inject(GlobalSearchFilterService);

    protected readonly faArrowUp = faArrowUp;
    protected readonly faArrowDown = faArrowDown;
    protected readonly searchInputComponent = viewChild<SearchInputComponent>(SearchInputComponent);
    protected readonly currentView = signal(SearchView.Navigation);
    protected readonly SearchView = SearchView;

    // Filter composition state + derived views, owned by GlobalSearchFilterService and re-exposed by reference so the
    // template and existing tests address them on the component unchanged while the logic lives in the store.
    protected readonly searchQuery = this.filter.searchQuery;
    protected readonly tokens = this.filter.tokens;
    protected readonly typesParam = this.filter.typesParam;
    protected readonly courseIdsParam = this.filter.courseIdsParam;
    protected readonly excludeCourseIdsParam = this.filter.excludeCourseIdsParam;
    protected readonly activeFilters = this.filter.activeFilters;
    protected readonly selectedChip = this.filter.selectedChip;
    protected readonly chips = this.filter.chips;
    protected readonly operator = this.filter.operator;
    protected readonly menuActiveIndex = this.filter.menuActiveIndex;
    protected readonly menuOptions = this.filter.menuOptions;
    protected readonly operatorValueValid = this.filter.operatorValueValid;
    protected readonly filterPickerOpen = this.filter.filterPickerOpen;
    protected readonly editingChip = this.filter.editingChip;
    protected readonly filterMenuOpen = this.filter.filterMenuOpen;
    protected readonly menuHeaderKey = this.filter.menuHeaderKey;

    // OS-aware label for the filter-picker shortcut shown on the Filter button (⌘F on Mac, Ctrl+F elsewhere).
    protected readonly filterShortcutLabel = computed<string>(() => (this.osDetector.isMac() ? '⌘F' : 'Ctrl+F'));
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
        // Wire the store's side-effects: a token change re-runs the search pipeline; menu/picker actions refocus input.
        this.filter.configure({
            applyTokens: (tokens) => this.applyTokens(tokens),
            requestFocus: () => this.focusInput(),
        });

        // Reset selection whenever the query changes; reading searchQuery() registers it as a reactive dependency.
        effect(() => {
            this.searchQuery();
            this.selectedIndex.set(-1);
            this.selectedChip.set(-1);
            this.menuActiveIndex.set(0);
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

                    const query = event.query;
                    const types = this.typesParam();
                    const courseIds = this.courseIdsParam();
                    const excludeCourseIds = this.excludeCourseIdsParam();
                    const hasFilter = types !== undefined || courseIds.length > 0 || excludeCourseIds.length > 0;
                    const trimmedQuery = query?.trim() || '';
                    const hasValidQuery = trimmedQuery.length >= MIN_SEARCH_QUERY_LENGTH;
                    const isTooShort = trimmedQuery.length > 0 && !hasValidQuery;

                    // No input at all and no filter — clear results synchronously
                    if (!trimmedQuery.length && !hasFilter) {
                        this.results.set([]);
                        this.hasSearched.set(false);
                        this.isLoading.set(false);
                        this.searchError.set(undefined);
                        return EMPTY;
                    }

                    // Query too short for server search (1-2 chars) — show loading skeleton
                    // while user is typing, then after debounce show the "too short" message
                    // without sending a request to the server (even when a filter is active).
                    if (isTooShort) {
                        this.isLoading.set(true);
                        this.searchError.set(undefined);
                        return timer(SEARCH_DEBOUNCE_MS).pipe(
                            switchMap(() => {
                                this.isLoading.set(false);
                                this.hasSearched.set(true);
                                return of([]);
                            }),
                        );
                    }

                    this.searchError.set(undefined);
                    const searchQuery = hasValidQuery ? trimmedQuery : '';
                    const courseIdsParam = courseIds.length ? courseIds : undefined;
                    const excludeCourseIdsParam = excludeCourseIds.length ? excludeCourseIds : undefined;
                    const cacheKey = this.filterCacheKey(types, courseIds, excludeCourseIds);

                    // Empty query with filter — serve from cache synchronously if available
                    if (!hasValidQuery && hasFilter) {
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
                            this.searchService.globalSearch(searchQuery, types, courseIdsParam, excludeCourseIdsParam).pipe(
                                tap((results) => {
                                    if (!hasValidQuery && hasFilter) {
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
        // (e.g. tokens) from becoming reactive dependencies of this effect —
        // only overlay.isOpen() should trigger it.
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
     * Parses the current URL to detect course context and tab, then pre-populates the course and type
     * tokens accordingly and triggers a search so results are displayed immediately.
     * Supports both student view (/courses/:id) and instructor view (/course-management/:id).
     */
    private applyContextFilters(): void {
        const newTokens = this.filter.deriveContextTokens(this.router.url);
        if (!newTokens) {
            return;
        }
        this.tokens.set(newTokens);
        this.searchSubject.next({ query: '' });
    }

    protected onSearchInput(query: string): void {
        this.searchQuery.set(query);
        this.searchError.set(undefined);

        // While the guided picker is open, plain typing narrows its entries instead of closing it or searching
        // (e.g. "-" shows the exclusions, "cou" shows the course actions). Typing a full operator falls through.
        if (this.filterPickerOpen() && !parseOperator(query)) {
            this.menuActiveIndex.set(0);
            return;
        }
        this.filterPickerOpen.set(false);

        // Typing a `facet:` operator opens the value menu instead of running a text search.
        if (parseOperator(query)) {
            return;
        }
        // Typing plain text leaves any facet operator, so cancel a chip that was being re-picked.
        this.editingChip.set(-1);

        // Show skeleton immediately while debounce waits, for a responsive feel
        const trimmedQuery = query?.trim() || '';
        const hasFilter = this.typesParam() !== undefined || this.courseIdsParam().length > 0 || this.excludeCourseIdsParam().length > 0;
        if (trimmedQuery.length > 0 || hasFilter) {
            this.isLoading.set(true);
        }

        this.searchSubject.next({ query });
    }

    /** Called by the search-input component when Backspace is pressed on an empty input. Removes the last token. */
    protected onBackspaceRemoveFilter() {
        this.filter.onBackspaceRemoveFilter();
    }

    /** Removes the chip at the given index (its remove button was clicked). */
    protected onChipRemoved(index: number) {
        this.filter.onChipRemoved(index);
    }

    /** Taps a chip to re-pick its value: reopens that facet's menu; choosing a value replaces the chip. */
    protected onChipSelected(index: number) {
        this.filter.onChipSelected(index);
    }

    /** Applies the chosen value-menu / picker option (delegates to the filter store). */
    protected onOptionSelected(index: number) {
        this.filter.onOptionSelected(index);
    }

    protected onOptionHovered(index: number) {
        this.filter.onOptionHovered(index);
    }

    /** Toggles the guided filter picker (used by the Filter button and Cmd/Ctrl+F). */
    protected toggleFilterPicker() {
        this.filter.toggleFilterPicker();
    }

    /** Opens the guided filter picker (facet chooser). */
    protected openFilterPicker() {
        this.filter.openFilterPicker();
    }

    protected removeCourseFilter() {
        this.placeholderCache.clear();
        this.applyTokens(this.filter.tokensWithoutCourseFilter());
    }

    protected onEntityClick(entity: SearchableEntity) {
        if (!entity.enabled) {
            return;
        }

        if (entity.filterTags?.length) {
            this.addFilter(entity.filterTags);
        }

        // Keep search input focused so user can start typing immediately
        this.focusInput();
    }

    /** Adds (or toggles off) the `type` token matching the given tags (delegates to the filter store). */
    protected addFilter(filterTypes: SearchEntityType[]) {
        this.filter.addFilter(filterTypes);
    }

    /** Stable cache key for placeholder (empty-query) results, keyed by the active filter set. */
    private filterCacheKey(types: string | undefined, courseIds: number[], excludeCourseIds: number[]): string {
        const ids = [...courseIds].sort((a, b) => a - b).join('.');
        const excludeIds = [...excludeCourseIds].sort((a, b) => a - b).join('.');
        return `${types ?? ''}_${ids}_x${excludeIds}`;
    }

    /**
     * Commits a new token set: updates the signal, optimistically shows the skeleton unless the
     * placeholder result is cached, and re-triggers the search.
     */
    private applyTokens(tokens: FilterToken[]) {
        this.tokens.set(tokens);
        const query = this.searchQuery()?.trim() || '';
        const cacheKey = this.filterCacheKey(this.typesParam(), this.courseIdsParam(), this.excludeCourseIdsParam());
        const hasCached = !query && this.placeholderCache.has(cacheKey);
        if (!hasCached) {
            this.isLoading.set(true);
        }
        this.searchSubject.next({ query: this.searchQuery() });
    }

    private resetSearch() {
        this.searchSubject.next(null);
        this.filter.reset();
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

        // Cmd/Ctrl+F toggles the guided filter picker (OS-appropriate modifier, consistent with Cmd/Ctrl+K).
        // Always preventDefault to block the browser find bar, but only toggle on the initial press so
        // holding the keys does not flicker the picker open/closed on auto-repeat.
        if (event.key.toLowerCase() === 'f' && this.osDetector.isActionKey(event)) {
            event.preventDefault();
            if (!event.repeat) {
                this.toggleFilterPicker();
            }
            return;
        }

        // Menu mode: while a value menu or the filter picker is open, arrows/enter/tab/escape drive it.
        if (this.filterMenuOpen()) {
            this.filter.handleMenuKey(event);
            return;
        }

        switch (event.key) {
            case 'Escape':
                event.preventDefault();
                if (this.selectedChip() >= 0) {
                    this.exitChips();
                } else if (this.currentView() !== SearchView.Navigation) {
                    this.navigateTo(SearchView.Navigation);
                } else {
                    this.overlay.close();
                }
                break;
            case 'ArrowDown':
                event.preventDefault();
                this.moveDown();
                break;
            case 'ArrowUp':
                event.preventDefault();
                this.moveUp();
                break;
            case 'ArrowLeft':
                // Only meaningful once inside the chips row.
                if (this.selectedChip() >= 0) {
                    event.preventDefault();
                    this.selectedChip.update((i) => Math.max(0, i - 1));
                }
                break;
            case 'ArrowRight':
                if (this.selectedChip() >= 0) {
                    event.preventDefault();
                    this.selectedChip.update((i) => Math.min(this.tokens().length - 1, i + 1));
                }
                break;
            case 'Enter':
                // Enter on a keyboard-selected chip re-picks it, the same as clicking it.
                if (this.selectedChip() >= 0) {
                    event.preventDefault();
                    this.onChipSelected(this.selectedChip());
                }
                break;
            case 'Backspace':
            case 'Delete':
                if (this.selectedChip() >= 0) {
                    event.preventDefault();
                    this.removeSelectedChip();
                }
                break;
        }
    }

    /** Down walks the zones: search input -> chips row -> results list. */
    private moveDown() {
        if (this.selectedChip() >= 0) {
            // Chips -> results.
            this.selectedChip.set(-1);
            this.selectedIndex.set(this.maxIndex() >= 0 ? 0 : -1);
            return;
        }
        if (this.selectedIndex() < 0) {
            // Search input -> chips (if any), otherwise straight to results.
            if (this.tokens().length > 0) {
                this.selectedChip.set(0);
            } else {
                this.selectedIndex.set(this.maxIndex() >= 0 ? 0 : -1);
            }
            return;
        }
        // Within results.
        this.selectedIndex.update((i) => Math.min(i + 1, this.maxIndex()));
    }

    /** Up walks the zones: results list -> chips row -> search input. */
    private moveUp() {
        if (this.selectedChip() >= 0) {
            // Chips -> search input.
            this.exitChips();
            return;
        }
        if (this.selectedIndex() > 0) {
            this.selectedIndex.update((i) => i - 1);
            return;
        }
        if (this.selectedIndex() === 0) {
            // Top of results -> chips (if any), otherwise back to the search input.
            this.selectedIndex.set(-1);
            if (this.tokens().length > 0) {
                this.selectedChip.set(this.tokens().length - 1);
            } else {
                this.focusInput();
            }
        }
    }

    private exitChips() {
        this.selectedChip.set(-1);
        this.focusInput();
    }

    private removeSelectedChip() {
        const index = this.selectedChip();
        this.applyTokens(removeTokenAt(this.tokens(), index));
        const remaining = this.tokens().length;
        if (remaining === 0) {
            this.exitChips();
        } else {
            this.selectedChip.set(Math.min(index, remaining - 1));
        }
    }

    private isToggleShortcut(event: KeyboardEvent): boolean {
        return event.key.toLowerCase() === 'k' && this.osDetector.isActionKey(event) && this.accountService.isAuthenticated() && !event.repeat;
    }

    protected navigateTo(view: SearchView) {
        if (view === SearchView.Lecture) {
            // TODO lecture search should support filters aswell
            this.removeCourseFilter();
        }
        this.currentView.set(view);
        this.selectedIndex.set(-1);
    }
}
