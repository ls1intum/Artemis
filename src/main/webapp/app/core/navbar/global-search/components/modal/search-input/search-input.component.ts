import { Component, ElementRef, computed, input, output, viewChild } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSearch, faSliders, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ParsedOperator } from '../../../models/search-operator.util';
import { FILTER_MENU_LISTBOX_ID, FilterChipView } from '../../../models/search-menu.model';

@Component({
    selector: 'jhi-global-search-input',
    standalone: true,
    imports: [FaIconComponent, ArtemisTranslatePipe],
    templateUrl: './search-input.component.html',
    styleUrls: ['./search-input.component.scss'],
})
export class SearchInputComponent {
    protected readonly faSearch = faSearch;
    protected readonly faTimes = faTimes;
    protected readonly faSliders = faSliders;

    searchQuery = input.required<string>();
    chips = input.required<FilterChipView[]>();
    isLoading = input.required<boolean>();
    /** The active `facet:` operator being typed, if any (drives the value menu + operator colouring). */
    operator = input<ParsedOperator | undefined>(undefined);
    /** True when the typed operator value is a recognised type / course, so it is coloured as confirmed. */
    operatorValueValid = input<boolean>(false);
    /** True when the typed operator value matches nothing, so it is marked as not a filter (dotted underline). */
    operatorUnknown = input<boolean>(false);
    /** Whether the filter menu (value menu or guided picker) is open — drives the combobox aria-expanded/controls. */
    menuVisible = input<boolean>(false);
    /** DOM id of the highlighted filter-menu option, for aria-activedescendant (the menu renders in the results pane). */
    activeOptionId = input<string | undefined>(undefined);
    /** OS-aware label for the filter-picker shortcut, e.g. "⌘F" on Mac / "Ctrl+F" elsewhere. */
    filterShortcutLabel = input<string>('⌘F');

    searchInput = output<string>();
    searchKeyDown = output<KeyboardEvent>();
    /** Emitted with the chip index when its remove button is clicked. */
    chipRemoved = output<number>();
    /** Emitted with the chip index when the chip is clicked, to select (focus) it. */
    chipSelected = output<number>();
    /** Emitted when Backspace is pressed while the cursor is at the beginning of the input. */
    backspaceOnEmpty = output<void>();
    /** Emitted when the Filter button (or Cmd/Ctrl+F) requests the guided filter picker. */
    filterTrigger = output<void>();

    protected searchInputElement = viewChild<ElementRef<HTMLInputElement>>('searchInput');

    protected hasChips = computed(() => this.chips().length > 0);
    /**
     * The search text in front of the operator. The operator is only the trailing token, so the overlay has to
     * paint the query in ordinary ink before it and colour only the operator itself.
     */
    protected leadingText = computed(() => this.searchQuery().slice(0, this.operator()?.start ?? 0));
    /** Whether a `facet:` operator is active (drives the coloured overlay + transparent input). */
    protected operatorActive = computed(() => !!this.operator());

    /** Shared id of the filter-menu listbox (rendered in the results pane), for the input's aria-controls. */
    protected readonly listboxId = FILTER_MENU_LISTBOX_ID;

    focusInput() {
        setTimeout(() => {
            this.searchInputElement()?.nativeElement.focus();
        }, 0);
    }

    protected onInput(event: Event) {
        const query = (event.target as HTMLInputElement).value;
        this.searchInput.emit(query);
    }

    protected onKeyDown(event: KeyboardEvent) {
        // Detect backspace at the beginning of the input directly from the DOM element,
        // which is always up-to-date (unlike the signal that may lag during keydown).
        const el = this.searchInputElement()?.nativeElement;
        if (event.key === 'Backspace' && el && el.selectionStart === 0 && el.selectionEnd === 0) {
            this.backspaceOnEmpty.emit();
        }
        this.searchKeyDown.emit(event);
    }

    protected onChipRemove(index: number, event?: Event) {
        event?.stopPropagation();
        this.chipRemoved.emit(index);
    }

    protected onChipClick(index: number) {
        this.chipSelected.emit(index);
    }

    /** Activates chip re-pick from the keyboard (chip is a role="button"); handled here so the modal keydown does not also react. */
    protected onChipKeydown(index: number, event: KeyboardEvent) {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            event.stopPropagation();
            this.chipSelected.emit(index);
        }
    }
}
