import { ChangeDetectionStrategy, Component, HostListener, computed, forwardRef, input, output, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFileLines } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { GlobalSearchActionItemComponent } from 'app/core/navbar/global-search/components/action-item/global-search-action-item.component';
import { SearchResultView } from 'app/core/navbar/global-search/components/views/search-result-view.directive';
import { SearchView } from 'app/core/navbar/global-search/models/search-view.model';

// Number of fixed action buttons rendered above the search results.
// Arrow-key indices 0..NAV_ACTION_COUNT-1 map to these buttons in template order.
// Increment this constant when adding a new action button.
export const NAV_ACTION_COUNT = 1;

@Component({
    selector: 'jhi-global-search-navigation-view',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [GlobalSearchActionItemComponent, ArtemisTranslatePipe, FaIconComponent],
    templateUrl: './global-search-navigation-view.component.html',
    providers: [{ provide: SearchResultView, useExisting: forwardRef(() => GlobalSearchNavigationViewComponent) }],
})
export class GlobalSearchNavigationViewComponent extends SearchResultView {
    readonly searchQuery = input.required<string>();
    readonly selectedIndex = input<number>(-1);

    // Emits when an action button is activated (click or Enter); the modal navigates to that view.
    readonly viewSelected = output<SearchView>();

    // TODO: Replace `never[]` with the real navigation result type and inject the search service.
    protected readonly navResults = signal<never[]>([]);

    // Total selectable items reported to the modal to bound ArrowDown/ArrowUp.
    readonly itemCount = computed(() => NAV_ACTION_COUNT + this.navResults().length);

    // Index relative to the results list, with the action-button offset stripped.
    // Pass this as [selectedIndex] to your results list component or use directly in @for.
    protected readonly resultSelectedIndex = computed(() => this.selectedIndex() - NAV_ACTION_COUNT);

    protected readonly SearchView = SearchView;
    protected readonly faFileLines = faFileLines;

    @HostListener('window:keydown', ['$event'])
    handleKeydown(event: KeyboardEvent): void {
        if (event.key !== 'Enter') return;
        const idx = this.selectedIndex();
        if (idx === 0) {
            event.preventDefault();
            this.viewSelected.emit(SearchView.Lecture);
        }
        // TODO: Handle Enter on navigation search results (idx >= NAV_ACTION_COUNT).
        // const result = this.navResults()[idx - NAV_ACTION_COUNT];
        // if (result) { event.preventDefault(); this.router.navigateByUrl(result.link); }
    }
}
