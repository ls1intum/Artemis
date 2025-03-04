import { Component, computed, input } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

import { faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { SortingOrder } from 'app/shared/table/pageable-table';

@Component({
    selector: 'jhi-sort-icon',
    templateUrl: './sort-icon.component.html',
    styleUrls: ['./sort-icon.component.scss'],
    imports: [FontAwesomeModule],
})
export class SortIconComponent {
    direction = input.required<SortingOrder.ASCENDING | SortingOrder.DESCENDING | 'none'>();

    faSortUp = faSortUp;
    faSortDown = faSortDown;

    isAscending = computed(() => this.direction() === SortingOrder.ASCENDING);
    isDescending = computed(() => this.direction() === SortingOrder.DESCENDING);
}
