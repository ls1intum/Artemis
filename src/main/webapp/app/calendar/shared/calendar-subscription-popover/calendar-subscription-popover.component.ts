import { Component, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiCheckboxComponent } from '@tumaet/ui-angular/checkbox';
import { TumAetUiPopoverComponent } from '@tumaet/ui-angular/popover';
import { TumAetUiSelectButtonComponent } from '@tumaet/ui-angular/select-button';
import { TextFieldModule } from '@angular/cdk/text-field';
import { FormsModule } from '@angular/forms';
import { faCheck } from '@fortawesome/free-solid-svg-icons/faCheck';
import { faCopy } from '@fortawesome/free-solid-svg-icons/faCopy';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateService } from '@ngx-translate/core';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';

@Component({
    selector: 'jhi-calendar-subscription-popover',
    imports: [
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiPopoverComponent,
        TumAetUiSelectButtonComponent,
        TextFieldModule,
        FormsModule,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
    templateUrl: './calendar-subscription-popover.component.html',
})
export class CalendarSubscriptionPopoverComponent {
    private translateService = inject(TranslateService);
    private isOnlyOneEventTypeSelected = computed<boolean>(() => this.computeIsOnlyOneEventTypeSelected());
    private currentLocale = getCurrentLocaleSignal(this.translateService);

    readonly faCopy = faCopy;
    readonly faCheck = faCheck;

    calendarSubscriptionPopover = viewChild<TumAetUiPopoverComponent>('calendarSubscriptionPopover');
    subscriptionToken = input.required<string>();
    courseId = input.required<number>();
    isMobile = input<boolean>(false);
    /** Whether a replacement of the subscription link is running, during which the old link is not offered. */
    resetting = input<boolean>(false);
    /** Emitted when the user confirms that the subscription link is replaced by a new one. */
    subscriptionResetRequested = output<void>();
    confirmingReset = signal(false);
    /** The link that was copied last, for the short time that the check mark says so. */
    copiedUrl = signal<string | undefined>(undefined);
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
    /** The check mark is only for the link that is shown: after a reset or a change of the options, the copied link is not the one on display. */
    copyConfirmed = computed(() => this.copiedUrl() !== undefined && this.copiedUrl() === this.subscriptionUrl());

    constructor() {
        // The callback of an effect registers a cleanup only through onCleanup, not by returning a function: it cancels the timer of an earlier copy when another link is copied,
        // and when the component is destroyed
        effect((onCleanup) => {
            if (this.copiedUrl() !== undefined) {
                onCleanup(this.setTimerToToggleBackCopiedUrl());
            }
        });
    }

    copySubscriptionUrlToClipboard() {
        const url = this.subscriptionUrl();
        if (url && !this.resetting()) {
            this.copiedUrl.set(url);
            void navigator.clipboard.writeText(url);
        }
    }

    resetSubscriptionLink() {
        this.confirmingReset.set(false);
        this.subscriptionResetRequested.emit();
    }

    /** Opens the popover next to the element that received the click. */
    open(event: Event): void {
        const origin = event.currentTarget;
        this.confirmingReset.set(false);
        if (origin instanceof HTMLElement) {
            this.calendarSubscriptionPopover()?.open(origin);
        }
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
        const route = `/api/calendar/courses/${courseId}/calendar-events-ics`;

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
            this.copiedUrl.set(undefined);
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
