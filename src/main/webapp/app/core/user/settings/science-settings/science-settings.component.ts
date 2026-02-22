import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { faInfoCircle } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { Subscription } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { UserSettingsDirective } from 'app/core/user/settings/directive/user-settings.directive';
import { ScienceSettingsService } from 'app/core/user/settings/science-settings/science-settings.service';
import { UserSettingsStructure } from 'app/core/user/settings/user-settings.model';
import { ScienceSetting } from 'app/core/user/settings/science-settings/science-settings-structure';

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
    ],
})
export class ScienceSettingsComponent extends UserSettingsDirective implements OnInit, OnDestroy {
    private scienceSettingsService = inject(ScienceSettingsService);
    private featureToggleService = inject(FeatureToggleService);

    faInfoCircle = faInfoCircle;

    private featureToggleActiveSubscription?: Subscription;
    private saveSubscription?: Subscription;
    featureToggleActive = false;
    private lastConfirmedValues = new Map<string, boolean>();

    declare userSettings: UserSettingsStructure<ScienceSetting>;
    declare settings: Array<ScienceSetting>;

    ngOnInit(): void {
        this.userSettingsCategory = UserSettingsCategory.SCIENCE_SETTINGS;

        // check if settings are already loaded
        const newestScienceSettings: ScienceSetting[] = this.scienceSettingsService.getScienceSettings();
        if (newestScienceSettings.length === 0) {
            // if no settings are already available load them from the server
            super.ngOnInit();
        } else {
            // else reuse the already available/loaded ones
            this.userSettings = this.userSettingsService.loadSettingsSuccessAsSettingsStructure(newestScienceSettings, this.userSettingsCategory);
            this.settings = this.userSettingsService.extractIndividualSettingsFromSettingsStructure(this.userSettings);
            this.storeConfirmedValues();
            this.changeDetector.detectChanges();
        }

        // subscribe to feature toggle changes
        this.featureToggleActiveSubscription = this.featureToggleService.getFeatureToggleActive(FeatureToggle.Science).subscribe((active) => {
            this.featureToggleActive = active;
        });
    }

    ngOnDestroy(): void {
        this.featureToggleActiveSubscription?.unsubscribe();
        this.saveSubscription?.unsubscribe();
    }

    /**
     * Catches the toggle event from a user click
     * Toggles the respective setting and saves it immediately
     */
    toggleSetting(event: MouseEvent) {
        const settingId = (event.currentTarget as HTMLElement | undefined)?.id;
        const settingToUpdate = this.settings.find((setting) => setting.settingId === settingId);
        if (!settingToUpdate) {
            return;
        }
        const confirmedValue = this.lastConfirmedValues.get(settingToUpdate.settingId) ?? settingToUpdate.active;
        settingToUpdate.active = !settingToUpdate.active;
        settingToUpdate.changed = true;

        // Cancel any in-flight save to prevent race conditions on rapid toggles
        this.saveSubscription?.unsubscribe();
        this.saveSubscription = this.userSettingsService.saveSettings(this.settings, this.userSettingsCategory).subscribe({
            next: (res) => {
                if (!res.body) {
                    return;
                }
                this.userSettings = this.userSettingsService.saveSettingsSuccess(this.userSettings, res.body);
                this.settings = this.userSettingsService.extractIndividualSettingsFromSettingsStructure(this.userSettings);
                this.storeConfirmedValues();
                this.finishSaving();
            },
            error: (res) => {
                // Revert to the last server-confirmed value
                settingToUpdate.active = confirmedValue;
                settingToUpdate.changed = false;
                this.onError(res);
            },
        });
    }

    private storeConfirmedValues(): void {
        if (!this.settings) {
            return;
        }
        for (const setting of this.settings) {
            this.lastConfirmedValues.set(setting.settingId, !!setting.active);
        }
    }
}
