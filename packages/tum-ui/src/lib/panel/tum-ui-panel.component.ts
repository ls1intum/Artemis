import { ChangeDetectionStrategy, Component, booleanAttribute, computed, inject, input, model } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronUp } from '@fortawesome/free-solid-svg-icons';
import { TUM_UI_TRANSLATOR } from '../i18n/tum-ui-translations';

let nextPanelId = 0;

@Component({
    selector: 'tum-ui-panel',
    templateUrl: './tum-ui-panel.component.html',
    styleUrl: './tum-ui-panel.component.scss',
    imports: [FaIconComponent],
    host: {
        class: 'tum-ui-panel tum:border tum:border-border tum:rounded-md tum:bg-content-background tum:text-text',
        '[attr.data-collapsed]': 'toggleable() && collapsed()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiPanelComponent {
    private readonly translator = inject(TUM_UI_TRANSLATOR);

    readonly header = input<string>('');

    /** Enables disclosure behavior for the projected content. */
    readonly toggleable = input(false, { transform: booleanAttribute });

    /** Overrides the toggle name; otherwise the header or package translation is used. */
    readonly toggleAriaLabel = input<string>();

    /** Controlled disclosure state, applied only when `toggleable` is enabled. */
    readonly collapsed = model(false);

    protected readonly headerId = `tum-ui-panel-header-${nextPanelId}`;
    protected readonly contentId = `tum-ui-panel-content-${nextPanelId++}`;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faChevronUp = faChevronUp;
    protected readonly isCollapsed = computed(() => this.toggleable() && this.collapsed());
    protected readonly toggleLabelledBy = computed(() => (!this.toggleAriaLabel()?.trim() && this.header().trim() ? this.headerId : null));
    protected readonly toggleLabel = computed(() => {
        const customLabel = this.toggleAriaLabel()?.trim();
        if (customLabel) {
            return customLabel;
        }
        if (this.header().trim()) {
            return null;
        }
        return this.translator.translate(this.collapsed() ? 'tumUi.panel.expand' : 'tumUi.panel.collapse');
    });

    protected toggle(): void {
        if (this.toggleable()) {
            this.collapsed.update((collapsed) => !collapsed);
        }
    }
}
