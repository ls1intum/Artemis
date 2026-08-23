import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FILTER_MENU_LISTBOX_ID, FilterMenuOption, filterOptionDomId } from '../../../models/search-menu.model';

/**
 * The filter value menu / guided picker, rendered inside the results pane (not as a floating dropdown).
 * While the user is composing a filter they are not scanning results, so the palette reuses that space
 * instead of stacking another overlay. Purely presentational: the modal owns keyboard navigation and the
 * active index; this component renders the options and reports clicks / hovers.
 */
@Component({
    selector: 'jhi-global-search-filter-menu',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, ArtemisTranslatePipe],
    templateUrl: './global-search-filter-menu.component.html',
    styleUrls: ['./global-search-filter-menu.component.scss'],
})
export class GlobalSearchFilterMenuComponent {
    options = input.required<FilterMenuOption[]>();
    activeIndex = input<number>(0);
    /** i18n key for the panel header, e.g. "Choose type" / "Filter by". */
    headerKey = input<string>('global.search.chooseType');
    /** True while choosing values to exclude (a negated operator is active): tints every row's glyph red. */
    exclude = input<boolean>(false);
    /** Whether to show a back button in the header (a step back to the previous picker level is available). */
    showBack = input<boolean>(false);
    /**
     * Replaces the header when the typed value is not a known filter value, e.g. `"candle" is not a type`.
     * Nothing has gone wrong, so it is phrased and styled as a statement rather than as an error.
     */
    message = input<{ key: string; value: string } | undefined>(undefined);

    /** Emitted with the option index when a row is chosen (clicked). */
    optionSelected = output<number>();
    /** Emitted with the option index when a row is hovered. */
    optionHovered = output<number>();
    /** Emitted when the header back button is clicked. */
    back = output<void>();

    protected readonly faChevronLeft = faChevronLeft;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    /** Shared with the search input's aria-controls so the combobox references this listbox across components. */
    protected readonly listboxId = FILTER_MENU_LISTBOX_ID;

    protected optionDomId(optionId: string): string {
        return filterOptionDomId(optionId);
    }

    /**
     * Splits a hint into a fixed part and a faded placeholder wrapped in {braces}, e.g. "−{filter}:" renders as
     * "−filter:" with "filter" dimmed. Hints without braces come back as a single `before` segment.
     */
    protected hintParts(hint: string): { before: string; placeholder: string; after: string } {
        const match = hint.match(/^(.*?)\{(.+?)\}(.*)$/);
        return match ? { before: match[1], placeholder: match[2], after: match[3] } : { before: hint, placeholder: '', after: '' };
    }

    /** Exclude rows carry a "−…" hint; used to tint the syntax pill red. */
    protected isExcludeHint(hint: string): boolean {
        return hint.startsWith('−');
    }

    /**
     * Whether a row's glyph takes the exclude tint. The whole menu is in exclude mode for a negated operator,
     * but the literal-search row is the way out of it rather than part of it, and a red magnifier on a row that
     * only runs a search reads as destructive.
     */
    protected isExcludeGlyph(option: FilterMenuOption): boolean {
        return option.action.kind !== 'literal' && (this.exclude() || this.isExcludeHint(option.hint ?? ''));
    }
}
