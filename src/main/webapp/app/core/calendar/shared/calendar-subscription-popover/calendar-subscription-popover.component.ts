import { Component, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { NgClass } from '@angular/common';
import { Popover } from 'primeng/popover';
import { Checkbox } from 'primeng/checkbox';
import { SelectButton } from 'primeng/selectbutton';
import { TextFieldModule } from '@angular/cdk/text-field';
import { FormsModule } from '@angular/forms';
import { faCheck, faCopy } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-calendar-subscription-popover',
    imports: [NgClass, Popover, Checkbox, SelectButton, TextFieldModule, FormsModule, FaIconComponent, TranslateDirective],
    templateUrl: './calendar-subscription-popover.component.html',
    styleUrl: './calendar-subscription-popover.component.scss',
})
export class CalendarSubscriptionPopoverComponent {
    private translateService = inject(TranslateService);
    private isOnlyOneEventTypeSelected = computed<boolean>(() => this.computeIsOnlyOneEventTypeSelected());
    private currentLocale = getCurrentLocaleSignal(this.translateService);

    readonly faCopy = faCopy;
    readonly faCheck = faCheck;

    calendarSubscriptionPopover = viewChild<Popover>('calendarSubscriptionPopover');
    subscriptionToken = input.required<string>();
    courseId = input.required<number>();
    isMobile = input<boolean>(false);
    copiedUrl = signal(false);
    includeLectureEvents = signal(true);
    includeExerciseEvents = signal(true);
    includeTutorialEvents = signal(true);
    includeExamEvents = signal(true);
    lecturesIsLastSelectedEventType = computed<boolean>(() => this.isOnlyOneEventTypeSelected() && this.includeLectureEvents());
    exercisesIsLastSelectedEventType = computed<boolean>(() => this.isOnlyOneEventTypeSelected() && this.includeExerciseEvents());
    tutorialsIsLastSelectedEventType = computed<boolean>(() => this.isOnlyOneEventTypeSelected() && this.includeTutorialEvents());
    examsIsLastSelectedEventType = computed<boolean>(() => this.isOnlyOneEventTypeSelected() && this.includeExamEvents());
    selectedLanguage = signal<'ENGLISH' | 'GERMAN'>('ENGLISH');
    languageOptions = computed(() => this.computeLanguageOptions());
    subscriptionUrl = computed<string>(() => this.buildCalendarSubscriptionURL());

    constructor() {
        effect(() => {
            if (this.copiedUrl()) {
                return this.setTimerToToggleBackCopiedUrl();
            }
        });
    }

    copySubscriptionUrlToClipboard() {
        const url = this.subscriptionUrl();
        if (url) {
            this.copiedUrl.set(true);
            navigator.clipboard.writeText(url);
        }
    }

    open(event: Event): void {
        this.calendarSubscriptionPopover()?.show(event);
    }

    private buildCalendarSubscriptionURL(): string {
        const courseId = this.courseId();
        const subscriptionToken = this.subscriptionToken();
        const includeLectureEvents = this.includeLectureEvents();
        const includeExerciseEvents = this.includeExerciseEvents();
        const includeTutorialEvents = this.includeTutorialEvents();
        const includeExamEvents = this.includeExamEvents();
        const selectedLanguage = this.selectedLanguage();
        const origin = window.location.origin;
        const route = `/api/core/calendar/courses/${courseId}/calendar-events-ics`;

        const filterOptions: string[] = [];
        if (includeLectureEvents) filterOptions.push('LECTURES');
        if (includeExerciseEvents) filterOptions.push('EXERCISES');
        if (includeTutorialEvents) filterOptions.push('TUTORIALS');
        if (includeExamEvents) filterOptions.push('EXAMS');
        const queryParameters = new URLSearchParams();
        queryParameters.set('token', subscriptionToken);
        if (filterOptions.length > 0) {
            queryParameters.set('filterOptions', filterOptions.join(','));
        }
        queryParameters.set('language', selectedLanguage);

        return origin + route + '?' + queryParameters.toString();
    }

    private setTimerToToggleBackCopiedUrl(): () => void {
        const id = window.setTimeout(() => {
            this.copiedUrl.set(false);
        }, 1500);
        return () => clearTimeout(id);
    }

    private computeLanguageOptions() {
        this.currentLocale();
        return [
            {
                label: this.translateService.instant('artemisApp.calendar.subscriptionPopover.languageOption.english'),
                value: 'ENGLISH' as const,
            },
            {
                label: this.translateService.instant('artemisApp.calendar.subscriptionPopover.languageOption.german'),
                value: 'GERMAN' as const,
            },
        ];
    }

    private computeIsOnlyOneEventTypeSelected(): boolean {
        return [this.includeLectureEvents(), this.includeExerciseEvents(), this.includeTutorialEvents(), this.includeExamEvents()].filter(Boolean).length === 1;
    }
}
