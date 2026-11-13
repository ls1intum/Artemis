import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** One icon of a rail: the part of a collapsed panel it opens (when the panel has several) and its accessible name. */
export interface CollapsedPanelRailItem {
    id?: string;
    icon: IconProp;
    /** Translation key of the name; the rail has no visible text, so this is the accessible name and the tooltip. */
    labelKey: string;
}

/**
 * What stands in for a collapsed panel: only the icon of the panel, which opens it again, set off by a border like the icons of the
 * collapsed right panel of the exercise details, so every collapsible panel of the exercise and assessment pages collapses alike.
 * A panel that holds several parts (the build output and the general feedback) shows one icon for each, and `itemSelected` tells
 * which one was clicked so the page can open the panel at that part.
 *
 * The rail fills the space its host gives it. `[orientation]` is `vertical` for a panel that collapses sideways (a narrow column,
 * the icons stacked) and `horizontal` for one that collapses downwards (a short strip, the icons in a row).
 */
@Component({
    selector: 'jhi-collapsed-panel-rail',
    templateUrl: './collapsed-panel-rail.component.html',
    styleUrls: ['./collapsed-panel-rail.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { '[class.collapsed-panel-rail--horizontal]': "orientation() === 'horizontal'" },
    imports: [FaIconComponent, ArtemisTranslatePipe],
})
export class CollapsedPanelRailComponent {
    /** The icon and name of a panel with a single part; a panel with several parts passes `[items]` instead. */
    readonly icon = input<IconProp>();
    readonly labelKey = input<string>();
    readonly items = input<CollapsedPanelRailItem[]>();
    readonly orientation = input<'vertical' | 'horizontal'>('vertical');
    /** Identifies the rail in tests; with several items each button's id follows it. */
    readonly testId = input('collapsed-panel-rail');

    /** The user clicked an icon to open the panel again. */
    readonly expand = output<MouseEvent>();
    /** The id of the part whose icon was clicked, sent before `expand`. */
    readonly itemSelected = output<string>();

    protected readonly entries = computed<CollapsedPanelRailItem[]>(() => {
        const items = this.items();
        const icon = this.icon();
        const labelKey = this.labelKey();
        return items ?? (icon && labelKey ? [{ icon, labelKey }] : []);
    });

    protected select(item: CollapsedPanelRailItem, event: MouseEvent): void {
        if (item.id) {
            this.itemSelected.emit(item.id);
        }
        this.expand.emit(event);
    }

    protected buttonTestId(item: CollapsedPanelRailItem): string {
        return this.entries().length > 1 && item.id ? `${this.testId()}-${item.id}` : this.testId();
    }
}
