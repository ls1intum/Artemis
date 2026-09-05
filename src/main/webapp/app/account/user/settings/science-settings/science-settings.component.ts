import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { UserSettingsCategory } from 'app/foundation/constants/user-settings.constants';
import { faInfoCircle } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { Subscription } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HasAnyAuthorityDirective } from 'app/foundation/auth/has-any-authority.directive';
import { UserSettingsDirective } from 'app/account/user/settings/directive/user-settings.directive';
import { ScienceSettingsService } from 'app/account/user/settings/science-settings/science-settings.service';
import { ScienceSetting } from 'app/account/user/settings/science-settings/science-settings-structure';
import { FormsModule } from '@angular/forms';
import { TumUiListComponent, TumUiListItemDirective, TumUiToggleSwitchComponent } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-science-settings',
    templateUrl: 'science-settings.component.html',
    styleUrls: ['../user-settings.scss'],
    imports: [
        FaIconComponent,
        TranslateDirective,
        // NOTE: this is actually used in the html template, otherwise *jhiHasAnyAuthority would not work
        HasAnyAuthorityDirective,
        ArtemisTranslatePipe,
        FormsModule,
        TumUiListComponent,
        TumUiListItemDirective,
        TumUiToggleSwitchComponent,
    ],
})
export class ScienceSettingsComponent extends UserSettingsDirective implements OnInit, OnDestroy {
    private scienceSettingsService = inject(ScienceSettingsService);
    private featureToggleService = inject(FeatureToggleService);

    faInfoCircle = faInfoCircle;

    private featureToggleActiveSubscription?: Subscription;
    private saveSubscription?: Subscription;
    readonly featureToggleActive = signal(false);
    private lastConfirmedValues = new Map<string, boolean>();

    override ngOnInit(): void {
        this.userSettingsCategory = UserSettingsCategory.SCIENCE_SETTINGS;

        // check if settings are already loaded
        const newestScienceSettings: ScienceSetting[] = this.scienceSettingsService.getScienceSettings();
        if (newestScienceSettings.length === 0) {
            // if no settings are already available load them from the server
            super.ngOnInit();
        } else {
            // else reuse the already available/loaded ones
            this.userSettings.set(this.userSettingsService.loadSettingsSuccessAsSettingsStructure(newestScienceSettings, this.userSettingsCategory));
            this.settings.set(this.userSettingsService.extractIndividualSettingsFromSettingsStructure(this.userSettings()));
            this.storeConfirmedValues();
        }

        // subscribe to feature toggle changes
        this.featureToggleActiveSubscription = this.featureToggleService.getFeatureToggleActive(FeatureToggle.Science).subscribe((active) => {
            this.featureToggleActive.set(active);
        });
    }

    ngOnDestroy(): void {
        this.featureToggleActiveSubscription?.unsubscribe();
        this.saveSubscription?.unsubscribe();
    }

    /**
     * Applies a switch change to the respective setting and saves it immediately.
     */
    toggleSetting(setting: ScienceSetting, active: boolean) {
        const settingToUpdate = this.settings().find((candidate) => candidate.settingId === setting.settingId);
        if (!settingToUpdate) {
            return;
        }
        const confirmedValue = this.lastConfirmedValues.get(settingToUpdate.settingId) ?? settingToUpdate.active;
        settingToUpdate.active = active;
        settingToUpdate.changed = true;

        // Cancel any in-flight save to prevent race conditions on rapid toggles
        this.saveSubscription?.unsubscribe();
        this.saveSubscription = this.userSettingsService.saveSettings(this.settings(), this.userSettingsCategory).subscribe({
            next: (res) => {
                if (!res.body) {
                    return;
                }
                this.userSettings.set(this.userSettingsService.saveSettingsSuccess(this.userSettings(), res.body));
                this.settings.set(this.userSettingsService.extractIndividualSettingsFromSettingsStructure(this.userSettings()));
                this.storeConfirmedValues();
                this.finishSaving();
            },
            error: (res) => {
                // Revert to the last server-confirmed value. The setting is mutated in place, so re-set the
                // structure to publish the revert: the signal notifies on an unchanged reference.
                settingToUpdate.active = confirmedValue;
                settingToUpdate.changed = false;
                this.userSettings.set(this.userSettings());
                this.onError(res);
            },
        });
    }

    private storeConfirmedValues(): void {
        if (!this.settings()) {
            return;
        }
        for (const setting of this.settings()) {
            this.lastConfirmedValues.set(setting.settingId, !!setting.active);
        }
    }
}
