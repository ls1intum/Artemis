import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, forwardRef, inject, input, output, viewChildren } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    faBook,
    faCalendarAlt,
    faCalendarCheck,
    faChartBar,
    faCheckDouble,
    faComments,
    faCube,
    faFileLines,
    faFileUpload,
    faFont,
    faHashtag,
    faKeyboard,
    faProjectDiagram,
    faQuestion,
    faTrophy,
    faUsers,
} from '@fortawesome/free-solid-svg-icons';
import { GlobalSearchActionItemComponent } from 'app/core/navbar/global-search/components/action-item/global-search-action-item.component';
import { SearchResultView } from 'app/core/navbar/global-search/components/views/search-result-view.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SearchView } from 'app/core/navbar/global-search/models/search-view.model';
import { SearchableEntity } from 'app/core/navbar/global-search/models/searchable-entity.model';
import { SearchableEntityItemComponent } from 'app/core/navbar/global-search/components/modal/searchable-entity-item/searchable-entity-item.component';
import { GlobalSearchResult } from 'app/core/navbar/global-search/services/global-search.service';
import { SearchResultItemComponent } from 'app/core/navbar/global-search/components/modal/search-result-item/search-result-item.component';
import { Router } from '@angular/router';
import { SearchOverlayService } from 'app/core/navbar/global-search/services/search-overlay.service';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';

// Number of fixed action buttons rendered above the search results.
// Arrow-key indices 0..NAV_ACTION_COUNT-1 map to these buttons in template order.
// Increment this constant when adding a new action button.
export const NAV_ACTION_COUNT = 2;

@Component({
    selector: 'jhi-global-search-navigation-view',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [GlobalSearchActionItemComponent, FaIconComponent, SearchableEntityItemComponent, SearchResultItemComponent, ArtemisTranslatePipe],
    templateUrl: './global-search-navigation-view.component.html',
    styleUrls: ['./global-search-navigation-view.component.scss'],
    providers: [{ provide: SearchResultView, useExisting: forwardRef(() => GlobalSearchNavigationViewComponent) }],
})
export class GlobalSearchNavigationViewComponent extends SearchResultView {
    readonly searchQuery = input.required<string>();
    readonly selectedIndex = input<number>(-1);
    readonly results = input<GlobalSearchResult[]>([]);
    readonly hasSearched = input<boolean>(false);
    readonly showResults = input<boolean>(false);

    // Emits when an action button is activated (click or Enter); the modal navigates to that view.
    readonly viewSelected = output<SearchView>();
    readonly entityClick = output<SearchableEntity>();

    private readonly router = inject(Router);
    private readonly overlay = inject(SearchOverlayService);

    protected readonly NAV_ACTION_COUNT = NAV_ACTION_COUNT;

    // Query all selectable items for auto-scroll functionality
    private readonly selectableItems = viewChildren<ElementRef<HTMLElement>>('selectableItem');

    // Auto-scroll selected item into view when selection changes
    constructor() {
        super();
        effect(() => {
            const idx = this.selectedIndex();
            const items = this.selectableItems();
            if (idx >= 0 && idx < items.length) {
                const element = items[idx]?.nativeElement;
                if (element) {
                    element.scrollIntoView({
                        behavior: 'smooth',
                        block: 'nearest',
                        inline: 'nearest',
                    });
                }
            }
        });
    }

    // Icons
    protected readonly faKeyboard = faKeyboard;
    protected readonly faProjectDiagram = faProjectDiagram;
    protected readonly faFont = faFont;
    protected readonly faFileUpload = faFileUpload;
    protected readonly faCheckDouble = faCheckDouble;
    protected readonly faQuestion = faQuestion;
    protected readonly faTrophy = faTrophy;
    protected readonly faCalendarCheck = faCalendarCheck;

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

    // Total selectable items reported to the modal to bound ArrowDown/ArrowUp.
    readonly itemCount = computed(() => {
        if (this.showResults()) {
            // When showing results, action buttons are hidden, only count results
            return this.results().length;
        } else {
            // When showing entities, count action buttons + entities
            return NAV_ACTION_COUNT + this.searchableEntities.length;
        }
    });

    protected readonly SearchView = SearchView;
    protected readonly faFileLines = faFileLines;
    protected readonly faHashtag = faHashtag;

    protected onEntityItemClick(entity: SearchableEntity) {
        this.entityClick.emit(entity);
    }

    protected getIconForType(type: string, badge?: string): IconDefinition {
        if (type === 'exercise') {
            if (badge === 'Programming') return this.faKeyboard;
            if (badge === 'Modeling') return this.faProjectDiagram;
            if (badge === 'Text') return this.faFont;
            if (badge === 'File Upload') return this.faFileUpload;
            if (badge === 'Quiz') return this.faCheckDouble;
            return this.faQuestion;
        }
        return this.faQuestion;
    }

    protected navigateToResult(result: GlobalSearchResult) {
        if (result.type === 'exercise' && result.id) {
            const courseId = result.metadata['courseId'];
            if (courseId) {
                this.router.navigate(['/courses', courseId, 'exercises', result.id]);
            }
        }
        this.overlay.close();
    }

    @HostListener('window:keydown', ['$event'])
    handleKeydown(event: KeyboardEvent): void {
        if (event.key !== 'Enter') return;
        const idx = this.selectedIndex();

        if (this.showResults()) {
            // When showing results, no action buttons are present
            event.preventDefault();
            const result = this.results()[idx];
            if (result) {
                this.navigateToResult(result);
            }
        } else {
            // When showing entities, action buttons are present
            if (idx === 0) {
                event.preventDefault();
                this.viewSelected.emit(SearchView.Iris);
            } else if (idx === 1) {
                event.preventDefault();
                this.viewSelected.emit(SearchView.Lecture);
            } else if (idx >= NAV_ACTION_COUNT) {
                event.preventDefault();
                const entityIndex = idx - NAV_ACTION_COUNT;
                const entity = this.searchableEntities[entityIndex];
                if (entity && entity.enabled) {
                    this.entityClick.emit(entity);
                }
            }
        }
    }
}
