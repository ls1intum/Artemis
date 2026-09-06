import { ChangeDetectionStrategy, Component, ElementRef, booleanAttribute, computed, input, model, viewChild } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faMagnifyingGlass, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumUiInputDirective } from '../input/tum-ui-input.directive';
import { TumUiInputSize } from '../input/tum-ui-input.variants';
import { TumUiTranslatePipe } from '../i18n/tum-ui-translate.pipe';

/** Text field for filtering a view: a leading magnifier and a clear control that appears once there is a term. */
@Component({
    selector: 'tum-ui-search-field',
    templateUrl: './tum-ui-search-field.component.html',
    styleUrl: './tum-ui-search-field.component.scss',
    imports: [FaIconComponent, TumUiInputDirective, TumUiTranslatePipe],
    host: { class: 'tum-ui-search-field' },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiSearchFieldComponent {
    /** Two-way bindable term. Emits on every keystroke; debounce in the consumer if the term drives a request. */
    readonly value = model('');

    /** Translation key, resolved through the configured translator. */
    readonly placeholder = input('tumUi.searchField.placeholder');

    /** Translation key for the accessible name. Falls back to the placeholder. */
    readonly ariaLabel = input<string | undefined>(undefined);

    readonly disabled = input(false, { transform: booleanAttribute });

    readonly size = input<TumUiInputSize | undefined>(undefined);

    protected readonly faMagnifyingGlass = faMagnifyingGlass;
    protected readonly faXmark = faXmark;

    protected readonly accessibleNameKey = computed(() => this.ariaLabel() ?? this.placeholder());

    private readonly inputElement = viewChild.required<ElementRef<HTMLInputElement>>('searchInput');

    protected onInput(term: string): void {
        this.value.set(term);
    }

    /** Clears the term and returns focus to the field, so the reader can keep typing without reaching for the mouse. */
    protected clear(): void {
        this.value.set('');
        this.inputElement().nativeElement.focus();
    }
}
