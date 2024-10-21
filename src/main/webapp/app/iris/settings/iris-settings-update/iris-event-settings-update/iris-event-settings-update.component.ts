import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { IrisEventSettings } from 'app/entities/iris/settings/iris-event-settings.model';
import { ButtonType } from 'app/shared/components/button.component';
import { AccountService } from 'app/core/auth/account.service';
import { IrisSettingsType } from 'app/entities/iris/settings/iris-settings.model';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-iris-event-settings-update',
    standalone: true,
    imports: [ArtemisSharedCommonModule],
    templateUrl: './iris-event-settings-update.component.html',
})
export class IrisEventSettingsUpdateComponent {
    @Input({ required: true }) set eventSettings(value: IrisEventSettings) {
        this.settingsSignal.set(value);
    }

    @Input({ required: true }) set proactivityDisabled(value: boolean) {
        this.proactivityDisabledSignal.set(value);
    }
    @Input() settingsType!: IrisSettingsType;
    @Input() parentEventSettings?: IrisEventSettings;

    @Output() eventSettingsChange = new EventEmitter<IrisEventSettings>();

    private settingsSignal = signal<IrisEventSettings>({} as IrisEventSettings);
    private proactivityDisabledSignal = signal(false);

    inheritDisabled = computed(() =>
        this.parentEventSettings !== undefined ? this.proactivityDisabledSignal() || !this.parentEventSettings.enabled : this.proactivityDisabledSignal(),
    );
    isSettingsSwitchDisabled = computed(() => (!this.isAdmin() && this.settingsType !== IrisSettingsType.EXERCISE) || this.inheritDisabled());

    // Computed properties
    settings = computed(() => this.settingsSignal());
    enabled = computed(() => this.settings().enabled);
    isAdmin = signal(false);

    // Constants
    readonly WARNING = ButtonType.WARNING;

    constructor(private readonly accountService: AccountService) {
        this.isAdmin.set(this.accountService.isAdmin());
    }

    updateSetting(key: keyof IrisEventSettings, value: any) {
        const updatedSettings = { ...this.settings(), [key]: value };
        this.settingsSignal.set(updatedSettings);
        this.eventSettingsChange.emit(updatedSettings);
    }
}
