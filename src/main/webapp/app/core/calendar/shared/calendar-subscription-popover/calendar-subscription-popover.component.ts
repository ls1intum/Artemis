import { Component, OnDestroy, OnInit, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { NgClass } from '@angular/common';
import { Subscription } from 'rxjs';
import { Popover } from 'primeng/popover';
import { Checkbox } from 'primeng/checkbox';
import { SelectButton } from 'primeng/selectbutton';
import { TextFieldModule } from '@angular/cdk/text-field';
import { FormsModule } from '@angular/forms';
import { faCheck, faCopy } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-calendar-subscription-popover',
    imports: [NgClass, Popover, Checkbox, SelectButton, TextFieldModule, FormsModule, FaIconComponent, TranslateDirective],
    templateUrl: './calendar-subscription-popover.component.html',
    styleUrl: './calendar-subscription-popover.component.scss',
})
export class CalendarSubscriptionPopoverComponent implements OnDestroy, OnInit {
    private translateService = inject(TranslateService);
    private languageChangeSubscription?: Subscription;
    private buildLanguageOptions = () => [
        {
            label: this.translateService.instant('artemisApp.calendar.subscriptionPopover.languageOption.english'),
            value: 'ENGLISH' as const,
        },
        {
            label: this.translateService.instant('artemisApp.calendar.subscriptionPopover.languageOption.german'),
            value: 'GERMAN' as const,
        },
    ];
    private onlyOneEventTypeSelected = computed<boolean>(() => {
        return [this.includeLectureEvents(), this.includeExerciseEvents(), this.includeTutorialEvents(), this.includeExamEvents()].filter(Boolean).length === 1;
    });

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
    lecturesIsLastSelectedEventType = computed<boolean>(() => this.onlyOneEventTypeSelected() && this.includeLectureEvents());
    exercisesIsLastSelectedEventType = computed<boolean>(() => this.onlyOneEventTypeSelected() && this.includeExerciseEvents());
    tutorialsIsLastSelectedEventType = computed<boolean>(() => this.onlyOneEventTypeSelected() && this.includeTutorialEvents());
    examsIsLastSelectedEventType = computed<boolean>(() => this.onlyOneEventTypeSelected() && this.includeExamEvents());
    selectedLanguage = signal<'ENGLISH' | 'GERMAN'>('ENGLISH');
    languageOptions = signal<{ label: string; value: 'ENGLISH' | 'GERMAN' }[]>(this.buildLanguageOptions());
    subscriptionUrl = computed<string>(() =>
        this.buildCalendarSubscriptionURL(
            this.courseId(),
            this.subscriptionToken(),
            this.includeLectureEvents(),
            this.includeExerciseEvents(),
            this.includeTutorialEvents(),
            this.includeExamEvents(),
            this.selectedLanguage(),
        ),
    );

    constructor() {
        effect(() => {
            if (this.copiedUrl()) {
                return this.setTimerToToggleBackCopiedUrl();
            }
        });
    }

    ngOnInit() {
        this.languageChangeSubscription = this.translateService.onLangChange.subscribe(() => {
            this.languageOptions.set(this.buildLanguageOptions());
        });
    }

    ngOnDestroy(): void {
        this.languageChangeSubscription?.unsubscribe();
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

    private buildCalendarSubscriptionURL(
        courseId: number,
        subscriptionToken: string,
        includeLectureEvents: boolean,
        includeExerciseEvents: boolean,
        includeTutorialEvents: boolean,
        includeExamEvents: boolean,
        language: 'ENGLISH' | 'GERMAN',
    ): string {
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
        queryParameters.set('language', language);

        return origin + route + '?' + queryParameters.toString();
    }

    private setTimerToToggleBackCopiedUrl(): () => void {
        const id = window.setTimeout(() => {
            this.copiedUrl.set(false);
        }, 1500);
        return () => clearTimeout(id);
    }
}
