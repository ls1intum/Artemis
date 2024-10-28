import { Component, computed, input } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { CommonModule } from '@angular/common';
import { faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'app-sort-icon',
    templateUrl: './sort-icon.component.html',
    styleUrls: ['./sort-icon.component.scss'],
    standalone: true,
    imports: [FontAwesomeModule, CommonModule],
})
export class SortIconComponent {
    direction = input.required<'asc' | 'desc' | 'none'>();

    faSortUp = faSortUp;
    faSortDown = faSortDown;

    isAscending = computed(() => this.direction() === 'asc');
    isDescending = computed(() => this.direction() === 'desc');
}
