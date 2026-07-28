import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronUp } from '@fortawesome/free-solid-svg-icons';

let nextPanelId = 0;

@Component({
    selector: 'tum-ui-panel',
    templateUrl: './tum-ui-panel.component.html',
    styleUrl: './tum-ui-panel.component.scss',
    imports: [FaIconComponent],
    host: {
        '[class]': 'hostClasses()',
        '[attr.data-collapsed]': 'toggleable() && collapsed()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiPanelComponent {
    readonly header = input<string>('');

    readonly toggleable = input(false);

    readonly collapsed = model(false);

    readonly styleClass = input<string>('');

    protected readonly contentId = `tum-ui-panel-content-${nextPanelId++}`;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faChevronUp = faChevronUp;

    protected readonly hostClasses = computed(() =>
        `tum-ui-panel border border-tum-ui-border rounded-md bg-tum-ui-surface-0 dark:bg-tum-ui-surface-900 text-tum-ui-text ${this.styleClass()}`.trim(),
    );

    protected toggle(): void {
        if (this.toggleable()) {
            this.collapsed.update((collapsed) => !collapsed);
        }
    }
}
