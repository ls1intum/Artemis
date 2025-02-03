import { Component, DestroyRef, OnInit, WritableSignal, inject, output, signal } from '@angular/core';
import { IrisProactiveEventDisableDuration } from 'app/entities/iris/iris-disable-proactive-events-dto.model';
import { IrisPersonalSettings } from 'app/entities/iris/settings/iris-personal-settings.model';
import { AccountService } from 'app/core/auth/account.service';
import dayjs from 'dayjs/esm';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { CommonModule } from '@angular/common';
import { tap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

/**
 * Component for the personal settings of Iris.
 */
@Component({
    selector: 'jhi-iris-personal-settings',
    templateUrl: './iris-personal-settings.component.html',
    styleUrls: ['./iris-personal-settings.component.scss'],
    imports: [
        FormsModule,
        ReactiveFormsModule,
        FormDateTimePickerComponent,
        ArtemisTranslatePipe,
        FaIconComponent,
        NgbTooltip,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        NgbDropdownButtonItem,
        NgbDropdownItem,
        CommonModule,
        ArtemisDatePipe,
    ],
})
export class IrisPersonalSettingsComponent implements OnInit {
    private accountService = inject(AccountService);
    public settingsResult = output<IrisPersonalSettings | undefined>();
    private destroyRef = inject(DestroyRef);

    protected currentDate: dayjs.Dayjs;
    protected proactiveDisableOptions: string[];
    protected durationTranslationsMap: { [key: string]: string } = {
        ONE_HOUR: 'oneHour',
        ONE_DAY: 'oneDay',
        THIRTY_MINUTES: 'thirtyMinutes',
        FOREVER: 'forever',
        CUSTOM: 'custom',
    };
    protected customSelected: WritableSignal<boolean> = signal<boolean>(false);
    protected selectedDurationOption = signal<string | null>(null);
    protected disabledUntilDate = signal<dayjs.Dayjs | null>(null);
    protected savedEndTime: dayjs.Dayjs | null | undefined;

    public personalChatSettings: FormGroup;

    protected readonly faXmark = faXmark;
    constructor() {}

    public ngOnInit(): void {
        this.currentDate = dayjs();
        this.savedEndTime = this.accountService.userIdentity?.irisProactiveEventsDisabled;
        this.personalChatSettings = new FormGroup({
            proactivitySettings: new FormGroup({
                duration: new FormControl<string | null>(null),
                endTime: new FormControl<dayjs.Dayjs | null>(this.savedEndTime ?? null),
            }),
        });
        this.proactiveDisableOptions = Object.keys(IrisProactiveEventDisableDuration).filter((key) => isNaN(Number(key)));
        this.initFormControlListeners();
    }

    /**
     * Initializes the form control listener for the form controls.
     */
    public initFormControlListeners(): void {
        this.personalChatSettings
            .get('proactivitySettings.duration')
            ?.valueChanges.pipe(
                takeUntilDestroyed(this.destroyRef),
                tap((value) => {
                    this.selectedDurationOption.set(IrisProactiveEventDisableDuration[value]);
                    this.customSelected.set(value === IrisProactiveEventDisableDuration.CUSTOM);
                    this.setDisabledUntilDate(value);
                }),
            )
            .subscribe();
        this.personalChatSettings
            .get('proactivitySettings.endTime')
            ?.valueChanges.pipe(
                takeUntilDestroyed(this.destroyRef),
                tap((date) => this.disabledUntilDate.set(date)),
            )
            .subscribe();
    }

    /**
     * Sets the proactive disable option.
     * @param option The option to set.
     */
    public setProactiveDisableOption(option: string | undefined | null): void {
        const duration = option ? IrisProactiveEventDisableDuration[option as keyof typeof IrisProactiveEventDisableDuration] : null;
        this.personalChatSettings.get('proactivitySettings.duration')?.setValue(duration);
    }

    /**
     * Closes the settings window without saving.
     */
    public close(): void {
        this.settingsResult.emit(undefined);
    }

    /**
     * Saves the settings.
     */
    public save(): void {
        const settings = this.personalChatSettings.value;
        this.settingsResult.emit(settings);
    }

    /**
     * Resets the settings to the default values.
     */
    public reset(): void {
        this.personalChatSettings.reset();
    }

    /**
     * Sets the disabled until date based on the duration.
     * @param duration
     * @private
     */
    private setDisabledUntilDate(duration: IrisProactiveEventDisableDuration): void {
        switch (duration) {
            case IrisProactiveEventDisableDuration.ONE_HOUR:
                this.disabledUntilDate.set(dayjs().add(1, 'hour'));
                break;
            case IrisProactiveEventDisableDuration.ONE_DAY:
                this.disabledUntilDate.set(dayjs().add(1, 'day'));
                break;
            case IrisProactiveEventDisableDuration.THIRTY_MINUTES:
                this.disabledUntilDate.set(dayjs().add(30, 'minute'));
                break;
            case IrisProactiveEventDisableDuration.FOREVER:
                this.disabledUntilDate.set(null);
                break;
        }
    }
}
