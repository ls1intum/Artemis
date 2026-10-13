import { Component, input } from '@angular/core';
import { faAngleRight } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-collapsible-card',
    templateUrl: './collapsible-card.component.html',
    imports: [FaIconComponent],
})
export class CollapsibleCardComponent {
    readonly isCardContentCollapsed = input<boolean>(undefined!);
    readonly toggleCollapse = input<() => void>(undefined!);

    faAngleRight = faAngleRight;
}
