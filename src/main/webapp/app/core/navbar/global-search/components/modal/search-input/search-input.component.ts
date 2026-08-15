import { Component, ElementRef, computed, input, output, viewChild } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSearch, faSliders, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ParsedOperator } from '../../../models/search-operator.util';
import { FilterChipView, FilterMenuOption } from '../../../models/search-menu.model';

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
    /** Options for the value menu shown while a `facet:` operator is being typed. */
    menuOptions = input<FilterMenuOption[]>([]);
    /** Highlighted option index in the value menu. */
    menuActiveIndex = input<number>(0);
    /** Whether the menu panel (value menu or guided filter picker) is shown. */
    menuVisible = input<boolean>(false);
    /** i18n key for the menu header, e.g. "Choose type" / "Filter by". */
    menuHeaderKey = input<string>('global.search.chooseType');
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
    /** Emitted with the option index when a value-menu option is chosen. */
    optionSelected = output<number>();
    /** Emitted with the option index when a value-menu option is hovered. */
    optionHovered = output<number>();
    /** Emitted when the Filter button (or Cmd/Ctrl+F) requests the guided filter picker. */
    filterTrigger = output<void>();

    protected searchInputElement = viewChild<ElementRef<HTMLInputElement>>('searchInput');

    protected hasChips = computed(() => this.chips().length > 0);
    /** Whether a `facet:` operator is active (drives the coloured overlay + transparent input). */
    protected operatorActive = computed(() => !!this.operator());

    /** Stable id of the value-menu listbox, referenced by the input's aria-controls. */
    protected readonly FILTER_MENU_ID = 'global-search-filter-menu';
    /** DOM id for a menu option, so aria-activedescendant on the input can point at the highlighted option. */
    protected optionDomId(optionId: string): string {
        return `gs-filter-option-${optionId}`;
    }
    /** aria-activedescendant target: the DOM id of the currently highlighted menu option, if any. */
    protected readonly activeOptionId = computed<string | undefined>(() => {
        const option = this.menuOptions()[this.menuActiveIndex()];
        return option ? this.optionDomId(option.id) : undefined;
    });

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

    protected onOptionClick(index: number) {
        this.optionSelected.emit(index);
    }
}
