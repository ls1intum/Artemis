import { ChangeDetectionStrategy, Component, booleanAttribute, computed, inject, input, model } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronUp } from '@fortawesome/free-solid-svg-icons';
import { TUM_AET_UI_TRANSLATOR } from '../i18n/tumaet-ui-translations';

let nextPanelId = 0;

@Component({
    selector: 'tumaet-ui-panel',
    templateUrl: './tumaet-ui-panel.component.html',
    styleUrl: './tumaet-ui-panel.component.scss',
    imports: [FaIconComponent],
    host: {
        class: 'tumaet-ui-panel tumaet:border tumaet:border-border tumaet:rounded-md tumaet:bg-content-background tumaet:text-text',
        '[attr.data-collapsed]': 'toggleable() && collapsed()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiPanelComponent {
    private readonly translator = inject(TUM_AET_UI_TRANSLATOR);

    /**
     * Header title text. Omit when projecting a `[tumAetUiPanelHeader]` slot instead, and set `toggleAriaLabel`
     * alongside it — projected markup does not label the toggle.
     */
    readonly header = input<string>('');

    /** Enables disclosure behavior for the projected content. */
    readonly toggleable = input(false, { transform: booleanAttribute });

    /** Overrides the toggle name; otherwise the header or package translation is used. */
    readonly toggleAriaLabel = input<string>();

    /** Controlled disclosure state, applied only when `toggleable` is enabled. */
    readonly collapsed = model(false);

    protected readonly headerId = `tumaet-ui-panel-header-${nextPanelId}`;
    protected readonly contentId = `tumaet-ui-panel-content-${nextPanelId++}`;
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
        return this.translator.translate(this.collapsed() ? 'tumAetUi.panel.expand' : 'tumAetUi.panel.collapse');
    });

    protected toggle(): void {
        if (this.toggleable()) {
            this.collapsed.update((collapsed) => !collapsed);
        }
    }
}
