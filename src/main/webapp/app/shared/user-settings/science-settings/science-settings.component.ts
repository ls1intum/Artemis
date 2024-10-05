import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { UserSettingsDirective } from 'app/shared/user-settings/user-settings.directive';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { UserSettingsStructure } from 'app/shared/user-settings/user-settings.model';
import { faInfoCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { ScienceSetting } from 'app/shared/user-settings/science-settings/science-settings-structure';
import { ScienceSettingsService } from 'app/shared/user-settings/science-settings/science-settings.service';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-science-settings',
    templateUrl: 'science-settings.component.html',
    styleUrls: ['../user-settings.scss'],
})
export class ScienceSettingsComponent extends UserSettingsDirective implements OnInit, OnDestroy {
    private scienceSettingsService = inject(ScienceSettingsService);
    private featureToggleService = inject(FeatureToggleService);

    // Icons
    faSave = faSave;
    faInfoCircle = faInfoCircle;

    private featureToggleActiveSubscription: Subscription;
    featureToggleActive = false;

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
            this.changeDetector.detectChanges();
        }

        // subscribe to feature toggle changes
        this.featureToggleService.getFeatureToggleActive(FeatureToggle.Science).subscribe((active) => {
            this.featureToggleActive = active;
        });
    }

    ngOnDestroy(): void {
        if (this.featureToggleActiveSubscription) {
            this.featureToggleActiveSubscription.unsubscribe();
        }
    }

    /**
     * Catches the toggle event from a user click
     * Toggles the respective setting and mark it as changed (only changed settings will be send to the server for saving)
     */
    toggleSetting(event: any) {
        this.settingsChanged = true;
        const settingId = event.currentTarget.id;
        const settingToUpdate = this.settings.find((setting) => setting.settingId === settingId);
        if (!settingToUpdate) {
            return;
        }
        // toggle/inverts previous setting
        settingToUpdate.active = !settingToUpdate.active;
        settingToUpdate.changed = true;
    }
}
