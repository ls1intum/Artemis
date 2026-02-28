import { Component, ElementRef, HostListener, OnDestroy, computed, effect, inject, signal, viewChild } from '@angular/core';
import { SearchOverlayService } from '../../services/search-overlay.service';
import { OsDetectorService } from '../../services/os-detector.service';
import { AccountService } from 'app/core/auth/account.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowDown, faArrowUp, faCalendarAlt, faCode, faCube, faFileAlt, faQuestionCircle, faSearch, faTimes, faTrophy, faUpload } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DialogModule } from 'primeng/dialog';
import { ChipModule } from 'primeng/chip';
import { GlobalSearchResult, GlobalSearchService } from '../../services/global-search.service';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';

@Component({
    selector: 'jhi-global-search-modal',
    standalone: true,
    imports: [DialogModule, FaIconComponent, ArtemisTranslatePipe, ChipModule],
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
    protected readonly faSearch = faSearch;
    protected readonly faArrowUp = faArrowUp;
    protected readonly faArrowDown = faArrowDown;
    protected readonly faCode = faCode;
    protected readonly faCube = faCube;
    protected readonly faFileAlt = faFileAlt;
    protected readonly faUpload = faUpload;
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faTimes = faTimes;
    protected readonly faCalendarAlt = faCalendarAlt;
    protected readonly faTrophy = faTrophy;

    // Search state
    protected searchQuery = signal<string>('');
    protected activeFilters = signal<string[]>([]);
    protected results = signal<GlobalSearchResult[]>([]);
    protected selectedIndex = signal<number>(0);
    protected isLoading = signal<boolean>(false);
    protected hasSearched = signal<boolean>(false);

    protected searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');

    // Search debouncing
    private searchSubject = new Subject<string>();

    // Computed properties
    protected hasResults = computed(() => this.results().length > 0);
    protected showEmptyState = computed(() => this.hasSearched() && !this.isLoading() && !this.hasResults());

    constructor() {
        // Set up search debouncing with RxJS
        this.searchSubject
            .pipe(
                debounceTime(300),
                distinctUntilChanged(),
                switchMap((query) => {
                    if (!query || query.trim().length < 2) {
                        this.results.set([]);
                        this.hasSearched.set(false);
                        this.isLoading.set(false);
                        return of([]);
                    }

                    this.isLoading.set(true);
                    const typeFilter = this.activeFilters().length > 0 ? this.activeFilters()[0] : undefined;
                    return this.searchService.search(query, { type: typeFilter }).pipe(
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
        setTimeout(() => {
            this.searchInput()?.nativeElement.focus();
        }, 0);
    }

    protected onSearchInput(event: Event) {
        const query = (event.target as HTMLInputElement).value;
        this.searchQuery.set(query);
        this.searchSubject.next(query);
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
        // Re-trigger search without filter
        if (this.searchQuery()) {
            this.searchSubject.next(this.searchQuery());
        }
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
        const currentIndex = this.selectedIndex();
        const resultsLength = this.results().length;

        if (resultsLength === 0) return;

        if (direction === 'down') {
            this.selectedIndex.set((currentIndex + 1) % resultsLength);
        } else {
            this.selectedIndex.set((currentIndex - 1 + resultsLength) % resultsLength);
        }

        // Scroll selected item into view
        this.scrollSelectedIntoView();
    }

    private scrollSelectedIntoView() {
        setTimeout(() => {
            const selectedElement = document.querySelector('.search-result-item.selected');
            if (selectedElement) {
                selectedElement.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            }
        }, 0);
    }

    protected getIconForType(type: string, badge?: string) {
        if (type === 'exercise') {
            if (badge === 'Programming') return this.faCode;
            if (badge === 'Modeling') return this.faCube;
            if (badge === 'Text') return this.faFileAlt;
            if (badge === 'File Upload') return this.faUpload;
            if (badge === 'Quiz') return this.faQuestionCircle;
            return this.faCube;
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
        if (event.key === 'Enter' && this.hasResults()) {
            event.preventDefault();
            this.selectResult(this.selectedIndex());
            return;
        }

        // # to add exercise filter
        if (event.key === '#' && !this.activeFilters().includes('exercise')) {
            event.preventDefault();
            this.addFilter('exercise');
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
