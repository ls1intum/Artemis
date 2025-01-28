import { Component, ModelSignal, OnInit, inject, model, output } from '@angular/core';
import { IrisProactiveEventDisableDuration } from 'app/entities/iris/iris-disable-proactive-events-dto.model';
import { IrisPersonalSettings } from 'app/entities/iris/settings/iris-personal-settings.model';
import { AccountService } from 'app/core/auth/account.service';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule } from '@angular/forms';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { CommonModule } from '@angular/common';
import { KeysPipe } from 'app/shared/pipes/keys.pipe';

/**
 * Component for the personal settings of Iris.
 */
@Component({
    selector: 'jhi-iris-personal-settings',
    templateUrl: './iris-personal-settings.component.html',
    styleUrls: ['./iris-personal-settings.component.scss'],
    imports: [
        TranslateDirective,
        FormsModule,
        FormDateTimePickerComponent,
        ButtonComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        FaIconComponent,
        NgbTooltip,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        NgbDropdownButtonItem,
        NgbDropdownItem,
        CommonModule,
        KeysPipe,
    ],
})
export class IrisPersonalSettingsComponent implements OnInit {
    private accountService = inject(AccountService);
    public settingsResult = output<IrisPersonalSettings>();

    protected currentDate: dayjs.Dayjs;
    protected proactiveDisableOptions: string[];
    protected durationTranslationsMap: { [key: string]: string } = {
        ONE_HOUR: 'oneHour',
        ONE_DAY: 'oneDay',
        THIRTY_MINUTES: 'thirtyMinutes',
        FOREVER: 'forever',
        CUSTOM: 'custom',
    };

    public personalChatSettings: ModelSignal<IrisPersonalSettings> = model<IrisPersonalSettings>({
        proactivitySettings: {
            duration: null,
            endTime: null,
        },
    });

    protected readonly faXmark = faXmark;
    constructor() {}

    public ngOnInit(): void {
        this.currentDate = dayjs();
        this.updatePersonalSettings('proactivitySettings.endTime', this.accountService.userIdentity?.irisProactiveEventsDisabled);
        this.proactiveDisableOptions = Object.keys(IrisProactiveEventDisableDuration).filter((key) => isNaN(Number(key)));
    }

    /**
     * Update the personal chat settings.
     * @param path The path to the setting.
     * @param value The new value of the setting.
     */
    updatePersonalSettings(path: string, value: any) {
        const keys = path.split('.');
        this.personalChatSettings.update((settings) => {
            let current = { ...settings };
            for (let i = 0; i < keys.length - 1; i++) {
                current = { ...current, [keys[i]]: { ...(current as any)[keys[i]] } };
            }
            (current as any)[keys[keys.length - 1]] = value;
            return { ...settings, ...current };
        });
    }

    /**
     * Closes the settings window.
     */
    public close(): void {
        this.settingsResult.emit(this.personalChatSettings());
    }
}
