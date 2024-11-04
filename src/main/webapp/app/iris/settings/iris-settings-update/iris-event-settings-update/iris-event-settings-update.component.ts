import { Component, EventEmitter, Output, computed, input, signal } from '@angular/core';
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
    private settingsSignal = input.required<IrisEventSettings>({});

    proactivityDisabled = input.required<boolean>();
    settingsType = input.required<IrisSettingsType>();
    parentEventSettings = input<IrisEventSettings>();

    @Output() eventSettingsChange = new EventEmitter<IrisEventSettings>();

    inheritDisabled = computed(() => (this.parentEventSettings() !== undefined ? !this.parentEventSettings()!.enabled : false));
    isSettingsSwitchDisabled = computed(() => (!this.isAdmin() && this.settingsType() !== IrisSettingsType.EXERCISE) || this.inheritDisabled());

    // Computed properties
    enabled = computed(() => this.settingsSignal().enabled);
    isAdmin = signal(false);

    // Constants
    readonly WARNING = ButtonType.WARNING;

    constructor(private readonly accountService: AccountService) {
        this.isAdmin.set(this.accountService.isAdmin());
    }

    updateSetting(key: keyof IrisEventSettings, value: any) {
        const updatedSettings = { ...this.settingsSignal(), [key]: value };
        this.eventSettingsChange.emit(updatedSettings);
    }
}
