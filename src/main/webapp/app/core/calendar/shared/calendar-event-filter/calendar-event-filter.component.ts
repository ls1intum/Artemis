import { Component, computed, inject, input } from '@angular/core';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faFilter, faXmark } from '@fortawesome/free-solid-svg-icons';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { CalendarEventFilterOption } from 'app/core/calendar/shared/util/calendar-util';

export enum CalendarEventFilterComponentVariant {
    MOBILE,
    DESKTOP,
}

type IncludedOptionAndMetadata = { option: CalendarEventFilterOption; nameKey: string; colorClassName: string };

type OptionAndMetadata = { option: CalendarEventFilterOption; nameKey: string; isIncluded: boolean };

@Component({
    selector: 'jhi-calendar-event-filter',
    imports: [NgbPopover, FaIconComponent, TranslateDirective, NgClass],
    templateUrl: './calendar-event-filter.component.html',
    styleUrl: './calendar-event-filter.component.scss',
})
export class CalendarEventFilterComponent {
    private calendarService = inject(CalendarService);
    private filterOptionNameKeyMap: Record<CalendarEventFilterOption, string> = this.buildFilterOptionNameKeyMap();

    variant = input.required<CalendarEventFilterComponentVariant>();
    includedOptionsAndMetadata = computed<IncludedOptionAndMetadata[]>(() => {
        const includedOptions = this.calendarService.includedEventFilterOptions();
        return includedOptions.map((option) => ({ option: option, nameKey: this.filterOptionNameKeyMap[option], colorClassName: this.getColorClassFor(option) }));
    });
    optionsAndMetadata = computed<OptionAndMetadata[]>(() => {
        const includedOptions = this.calendarService.includedEventFilterOptions();
        return this.calendarService.eventFilterOptions.map((option) => ({
            option: option,
            nameKey: this.filterOptionNameKeyMap[option],
            isIncluded: includedOptions.includes(option),
        }));
    });

    readonly CalendarEventFilterComponentVariant = CalendarEventFilterComponentVariant;
    readonly faChevronDown = faChevronDown;
    readonly faXmark = faXmark;
    readonly faFilter = faFilter;

    toggleOption(option: CalendarEventFilterOption) {
        this.calendarService.toggleEventFilterOption(option);
    }

    private getColorClassFor(option: CalendarEventFilterOption): string {
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

    private buildFilterOptionNameKeyMap(): Record<CalendarEventFilterOption, string> {
        return {
            exerciseEvents: 'artemisApp.calendar.filterOption.exercises',
            lectureEvents: 'artemisApp.calendar.filterOption.lectures',
            tutorialEvents: 'artemisApp.calendar.filterOption.tutorials',
            examEvents: 'artemisApp.calendar.filterOption.exams',
        };
    }
}
