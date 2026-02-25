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
    title = input.required<string>();
    description = input.required<string>();
    accentColor = input.required<string>();
    secondaryAccentColor = input<string>();
    clicked = output<void>();
}
