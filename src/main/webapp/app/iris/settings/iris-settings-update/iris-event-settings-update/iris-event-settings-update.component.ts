import { Component, computed, input, model, signal } from '@angular/core';
import { IrisEventSettings } from 'app/entities/iris/settings/iris-event-settings.model';
import { ButtonType } from 'app/shared/components/button.component';
import { AccountService } from 'app/core/auth/account.service';
import { IrisSettingsType } from 'app/entities/iris/settings/iris-settings.model';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-iris-event-settings-update',
    standalone: true,
    templateUrl: './iris-event-settings-update.component.html',
    imports: [ArtemisSharedCommonModule],
})
export class IrisEventSettingsUpdateComponent {
    eventSettings = model.required<IrisEventSettings>();

    proactivityDisabled = input.required<boolean>();
    settingsType = input.required<IrisSettingsType>();
    parentEventSettings = input<IrisEventSettings>();

    isAdmin = signal(false);
    inheritDisabled = computed(() => (this.parentEventSettings() !== undefined ? !this.parentEventSettings()!.enabled : false));
    isSettingsSwitchDisabled = computed(() => (!this.isAdmin() && this.settingsType() !== IrisSettingsType.EXERCISE) || this.inheritDisabled());

    // Computed properties
    enabled = computed(() => this.eventSettings().enabled);

    // Constants
    readonly WARNING = ButtonType.WARNING;

    constructor(private readonly accountService: AccountService) {
        this.isAdmin.set(this.accountService.isAdmin());
    }

    updateSetting(key: keyof IrisEventSettings, value: any) {
        this.eventSettings.update((curr) => ({ ...curr, [key]: value }));
    }
}
