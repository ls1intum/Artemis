import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

@Component({
    selector: 'jhi-global-search-action-item',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { class: 'd-block' },
    imports: [],
    templateUrl: './global-search-action-item.component.html',
    styleUrls: ['./global-search-action-item.component.scss'],
})
export class GlobalSearchActionItemComponent {
    readonly title = input.required<string>();
    readonly description = input.required<string>();
    readonly accentColor = input.required<string>();
    readonly secondaryAccentColor = input<string>();
    readonly selected = input<boolean>(false);
    readonly clicked = output<void>();
}
