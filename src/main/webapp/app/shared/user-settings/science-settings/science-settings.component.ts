import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { UserSettingsDirective } from 'app/shared/user-settings/user-settings.directive';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { UserSettingsStructure } from 'app/shared/user-settings/user-settings.model';
import { AlertService } from 'app/core/util/alert.service';
import { faInfoCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { ScienceSetting } from 'app/shared/user-settings/science-settings/science-settings-structure';
import { ScienceSettingsService } from 'app/shared/user-settings/science-settings/science-settings.service';

@Component({
    selector: 'jhi-science-settings',
    templateUrl: 'science-settings.component.html',
    styleUrls: ['../user-settings.scss'],
})
export class ScienceSettingsComponent extends UserSettingsDirective implements OnInit {
    // Icons
    faSave = faSave;
    faInfoCircle = faInfoCircle;

    constructor(
        userSettingsService: UserSettingsService,
        changeDetector: ChangeDetectorRef,
        alertService: AlertService,
        private scienceSettingsService: ScienceSettingsService,
    ) {
        super(userSettingsService, alertService, changeDetector);
    }

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
