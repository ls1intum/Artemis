import { ChangeDetectionStrategy, Component, ElementRef, booleanAttribute, computed, input, model, viewChild } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faMagnifyingGlass, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiInputDirective } from '../input/tumaet-ui-input.directive';
import { TumAetUiInputSize } from '../input/tumaet-ui-input.variants';
import { TumAetUiTranslatePipe } from '../i18n/tumaet-ui-translate.pipe';

/** Text field for filtering a view: a leading magnifier and a clear control that appears once there is a term. */
@Component({
    selector: 'tumaet-ui-search-field',
    templateUrl: './tumaet-ui-search-field.component.html',
    styleUrl: './tumaet-ui-search-field.component.scss',
    imports: [FaIconComponent, TumAetUiInputDirective, TumAetUiTranslatePipe],
    host: { class: 'tumaet-ui-search-field' },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiSearchFieldComponent {
    /** Two-way bindable term. Emits on every keystroke; debounce in the consumer if the term drives a request. */
    readonly value = model('');

    /** Translation key, resolved through the configured translator. */
    readonly placeholder = input('tumAetUi.searchField.placeholder');

    /** Translation key for the accessible name. Falls back to the placeholder. */
    readonly ariaLabel = input<string | undefined>(undefined);

    readonly disabled = input(false, { transform: booleanAttribute });

    readonly size = input<TumAetUiInputSize | undefined>(undefined);

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
