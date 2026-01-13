import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { faInfoCircle, faSave } from '@fortawesome/free-solid-svg-icons';
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
import { UserSettingsTitleBarTitleDirective } from 'app/core/user/settings/shared/user-settings-title-bar-title.directive';
import { UserSettingsTitleBarActionsDirective } from 'app/core/user/settings/shared/user-settings-title-bar-actions.directive';

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
        UserSettingsTitleBarTitleDirective,
        UserSettingsTitleBarActionsDirective,
    ],
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
