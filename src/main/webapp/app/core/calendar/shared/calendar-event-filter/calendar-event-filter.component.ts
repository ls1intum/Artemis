import { Component, input } from '@angular/core';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faFilter } from '@fortawesome/free-solid-svg-icons';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MultiSelectModule } from 'primeng/multiselect';
import { FormsModule } from '@angular/forms';

export enum CalendarEventFilterComponentVariant {
    MOBILE,
    DESKTOP,
}

@Component({
    selector: 'jhi-calendar-event-filter',
    imports: [NgbPopover, FaIconComponent, TranslateDirective, NgClass, FormsModule, MultiSelectModule],
    templateUrl: './calendar-event-filter.component.html',
    styleUrl: './calendar-event-filter.component.scss',
})
export class CalendarEventFilterComponent {
    variant = input.required<CalendarEventFilterComponentVariant>();

    readonly faChevronDown = faChevronDown;

    readonly faFilter = faFilter;
}
