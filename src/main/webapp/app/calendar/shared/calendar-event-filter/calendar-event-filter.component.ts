import { Component, HostListener, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faXmark } from '@fortawesome/free-solid-svg-icons';
import { CalendarEventFilterOption } from 'app/calendar/shared/util/calendar-util';
import { CalendarEventService } from 'app/calendar/shared/service/calendar-event.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-calendar-event-filter',
    imports: [FaIconComponent, TranslateDirective],
    templateUrl: './calendar-event-filter.component.html',
    styleUrl: './calendar-event-filter.component.scss',
})
export class CalendarEventFilterComponent {
    isOpen = signal(false);
    includedOptions;

    readonly options;
    readonly faChevronDown = faChevronDown;
    readonly faXmark = faXmark;

    constructor(private calendarEventService: CalendarEventService) {
        this.options = calendarEventService.eventFilterOptions;
        this.includedOptions = calendarEventService.includedEventFilterOptions;
    }

    toggleOpen() {
        this.isOpen.update((currentState) => !currentState);
    }

    toggleOption(option: CalendarEventFilterOption) {
        this.calendarEventService.toggleEventFilterOption(option);
    }

    getColorClassForFilteringOption(option: CalendarEventFilterOption): string {
        if (option === 'examEvents') {
            return 'exam';
        } else if (option === 'lectureEvents') {
            return 'lecture';
        } else if (option === 'tutorialEvents') {
            return 'tutorial';
        } else {
            return 'exercise';
        }
    }

    getFilterOptionString(option: CalendarEventFilterOption): string {
        switch (option) {
            case 'exerciseEvents':
                return 'artemisApp.calendar.exerciseFilterOption';
            case 'lectureEvents':
                return 'artemisApp.calendar.lectureFilterOption';
            case 'tutorialEvents':
                return 'artemisApp.calendar.tutorialFilterOption';
            case 'examEvents':
                return 'artemisApp.calendar.examFilterOption';
        }
    }

    @HostListener('document:click', ['$event'])
    onDocumentClick(event: MouseEvent) {
        const target = event.target as HTMLElement;
        if (!target.closest('.multi-select')) {
            this.isOpen.set(false);
        }
    }
}
