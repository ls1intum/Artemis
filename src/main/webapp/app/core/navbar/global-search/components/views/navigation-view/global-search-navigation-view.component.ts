import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, forwardRef, inject, input, output, viewChildren } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SkeletonModule } from 'primeng/skeleton';
import {
    faBook,
    faCalendarCheck,
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
    faQuestionCircle,
} from '@fortawesome/free-solid-svg-icons';
import { GlobalSearchActionItemComponent } from 'app/core/navbar/global-search/components/action-item/global-search-action-item.component';
import { SearchResultView } from 'app/core/navbar/global-search/components/views/search-result-view.directive';
import { SearchView } from 'app/core/navbar/global-search/models/search-view.model';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { MODULE_FEATURE_IRIS } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
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
    imports: [GlobalSearchActionItemComponent, FaIconComponent, SearchableEntityItemComponent, SearchResultItemComponent, SkeletonModule, ArtemisTranslatePipe, IrisLogoComponent],
    templateUrl: './global-search-navigation-view.component.html',
    styleUrls: ['./global-search-navigation-view.component.scss'],
    providers: [{ provide: SearchResultView, useExisting: forwardRef(() => GlobalSearchNavigationViewComponent) }],
})
export class GlobalSearchNavigationViewComponent extends SearchResultView {
    private readonly profileService = inject(ProfileService);

    readonly searchQuery = input.required<string>();
    readonly selectedIndex = input<number>(-1);
    readonly results = input<GlobalSearchResult[]>([]);
    readonly hasSearched = input<boolean>(false);
    readonly showResults = input<boolean>(false);
    readonly isLoading = input<boolean>(false);
    readonly searchError = input<string | undefined>(undefined);
    readonly activeFilters = input<string[]>([]);
    readonly irisOpen = input<boolean>(false);

    // Skeleton placeholder array for loading animation
    protected readonly skeletonItems = Array(5);

    // Emits when an action button is activated (click or Enter); the modal navigates to that view.
    readonly viewSelected = output<SearchView>();
    readonly entityClick = output<SearchableEntity>();

    private readonly router = inject(Router);
    private readonly overlay = inject(SearchOverlayService);

    protected readonly NAV_ACTION_COUNT = NAV_ACTION_COUNT;

    // Query all selectable items for auto-scroll functionality
    private readonly selectableItems = viewChildren<ElementRef<HTMLElement>>('selectableItem');

    // False when artemis.iris.enabled = false in the server config; both action buttons are hidden.
    protected readonly irisEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);
    // Lecture search button is only visible when no filter is active
    protected readonly showLectureButton = computed(() => this.activeFilters().length === 0);
    // Number of action buttons currently visible (iris hidden when disabled or split view already open)
    protected readonly actionButtonCount = computed(() => {
        if (!this.irisEnabled) return 0;
        if (this.irisOpen()) return this.showLectureButton() ? 1 : 0;
        return this.showLectureButton() ? 2 : 1;
    });
    protected readonly IrisLogoSize = IrisLogoSize;
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
    protected readonly faCalendarCheck = faCalendarCheck;

    // Searchable entities for initial view
    protected searchableEntities: SearchableEntity[] = [
        {
            id: 'exercises',
            title: 'global.search.entities.exercisesTitle',
            description: 'global.search.entities.exercisesDescription',
            icon: faCube,
            type: 'page',
            enabled: true,
            filterTag: 'exercise',
        },
        {
            id: 'lectures',
            title: 'global.search.entities.lecturesTitle',
            description: 'global.search.entities.lecturesDescription',
            icon: faBook,
            type: 'page',
            enabled: true,
            filterTag: 'lecture',
        },
        {
            id: 'communication',
            title: 'global.search.entities.communicationTitle',
            description: 'global.search.entities.communicationDescription',
            icon: faComments,
            type: 'page',
            enabled: true,
            filterTag: 'channel',
        },
        {
            id: 'faqs',
            title: 'global.search.entities.faqsTitle',
            description: 'global.search.entities.faqsDescription',
            icon: faQuestionCircle,
            type: 'page',
            enabled: true,
            filterTag: 'faq',
        },
        {
            id: 'exams',
            title: 'global.search.entities.examsTitle',
            description: 'global.search.entities.examsDescription',
            icon: faCalendarCheck,
            type: 'page',
            enabled: true,
            filterTag: 'exam',
        },
    ];

    // Total selectable items reported to the modal to bound ArrowDown/ArrowUp.
    readonly itemCount = computed(() => {
        const buttonCount = this.actionButtonCount();
        if (this.showResults()) {
            // When showing results, action buttons may be visible + results
            return buttonCount + this.results().length;
        } else {
            // When showing entities, count action buttons + entities
            return buttonCount + this.searchableEntities.length;
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
        if (type === 'lecture' || type === 'lecture_unit') {
            return faBook;
        }
        if (type === 'channel') {
            return faHashtag;
        }
        if (type === 'faq') {
            return faQuestionCircle;
        }
        if (type === 'exam') {
            return this.faCalendarCheck;
        }
        return this.faQuestion;
    }

    protected navigateToResult(result: GlobalSearchResult) {
        const courseId = result.metadata['courseId'];
        if (!courseId) {
            this.overlay.close();
            return;
        }
        if (result.type === 'exercise' && result.id) {
            this.router.navigate(['/courses', courseId, 'exercises', result.id]);
        } else if (result.type === 'lecture' && result.id) {
            this.router.navigate(['/courses', courseId, 'lectures', result.id]);
        } else if (result.type === 'lecture_unit' && result.id) {
            const lectureId = result.metadata['lectureId'];
            if (lectureId) {
                this.router.navigate(['/courses', courseId, 'lectures', lectureId]);
            }
        } else if (result.type === 'exam' && result.id) {
            this.router.navigate(['/courses', courseId, 'exams', result.id]);
        } else if (result.type === 'faq') {
            this.router.navigate(['/courses', courseId, 'faq']);
        } else if (result.type === 'channel' && result.id) {
            this.router.navigate(['/courses', courseId, 'communication'], { queryParams: { conversationId: result.id } });
        }
        this.overlay.close();
    }

    @HostListener('window:keydown', ['$event'])
    handleKeydown(event: KeyboardEvent): void {
        if (event.key !== 'Enter') return;
        const idx = this.selectedIndex();
        if (idx < 0) return;
        const buttonCount = this.actionButtonCount();

        // Iris button at index 0 when enabled and split view not already open
        if (this.irisEnabled && !this.irisOpen() && idx === 0) {
            event.preventDefault();
            this.viewSelected.emit(SearchView.Iris);
            return;
        }
        // Lecture button: index 1 normally, shifts to index 0 when iris button is hidden (irisOpen)
        if (this.showLectureButton() && this.irisEnabled && idx === (this.irisOpen() ? 0 : 1)) {
            event.preventDefault();
            this.viewSelected.emit(SearchView.Lecture);
            return;
        }

        // Handle items after action buttons
        const itemIndex = idx - buttonCount;

        if (this.showResults()) {
            // When showing results
            event.preventDefault();
            const result = this.results()[itemIndex];
            if (result) {
                this.navigateToResult(result);
            }
        } else {
            // When showing entities
            event.preventDefault();
            const entity = this.searchableEntities[itemIndex];
            if (entity && entity.enabled) {
                this.entityClick.emit(entity);
            }
        }
    }
}
