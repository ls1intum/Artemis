import { Component } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faXmark } from '@fortawesome/free-solid-svg-icons';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarEventService } from 'app/core/calendar/shared/service/calendar-event.service';
import { CalendarEventFilterOption } from 'app/core/calendar/shared/util/calendar-util';

@Component({
    selector: 'jhi-calendar-event-filter',
    imports: [NgbPopover, FaIconComponent, TranslateDirective],
    templateUrl: './calendar-event-filter.component.html',
    styleUrl: './calendar-event-filter.component.scss',
})
export class CalendarEventFilterComponent {
    includedOptions;

    readonly utils = utils;
    readonly options;
    readonly faChevronDown = faChevronDown;
    readonly faXmark = faXmark;

    constructor(private calendarEventService: CalendarEventService) {
        this.options = calendarEventService.eventFilterOptions;
        this.includedOptions = calendarEventService.includedEventFilterOptions;
    }

    toggleOption(option: CalendarEventFilterOption) {
        this.calendarEventService.toggleEventFilterOption(option);
    }

    getColorClassForFilteringOption(option: CalendarEventFilterOption): string {
        if (option === 'examEvents') {
            return 'exam-chip';
        } else if (option === 'lectureEvents') {
            return 'lecture-chip';
        } else if (option === 'tutorialEvents') {
            return 'tutorial-chip';
        } else {
            return 'exercise-chip';
        }
    }
}
