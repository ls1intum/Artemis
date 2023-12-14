import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SettingId } from 'app/shared/constants/user-settings.constants';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Setting } from 'app/shared/user-settings/user-settings.model';
import { ScienceSetting } from 'app/shared/user-settings/science-settings/science-settings-structure';
import { ScienceSettingsService } from 'app/shared/user-settings/science-settings/science-settings.service';

const scienceSetting: ScienceSetting = {
    settingId: SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING,
    active: false,
};

const scienceSettingsForTesting: ScienceSetting[] = [scienceSetting];

describe('ScienceSettingsService', () => {
    let scienceSettingsService: ScienceSettingsService;
    let userSettingsService: UserSettingsService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        })
            .compileComponents()
            .then(() => {
                scienceSettingsService = TestBed.inject(ScienceSettingsService);
                userSettingsService = TestBed.inject(UserSettingsService);
            });
    });

    it('should refresh settings after user settings changed', () => {
        userSettingsService.userSettingsChangeEvent.subscribe = jest.fn().mockImplementation((callback) => {
            callback();
        });

        const spy = jest.spyOn(userSettingsService, 'loadSettings').mockReturnValue(of(new HttpResponse<Setting[]>({ body: scienceSettingsForTesting })));

        scienceSettingsService['listenForScienceSettingsChanges']();
        expect(spy).toHaveBeenCalled();
    });

    it('should provide getters for science settings and updates to it', () => {
        jest.spyOn(userSettingsService, 'loadSettings').mockReturnValue(of(new HttpResponse<Setting[]>({ body: scienceSettingsForTesting })));
        scienceSettingsService.refreshScienceSettings();

        const settings = scienceSettingsService.getScienceSettings();
        expect(settings).not.toBeEmpty();

        // Subscribing to the updates
        scienceSettingsService.getScienceSettingsUpdates().subscribe((updatedSettings) => {
            expect(updatedSettings).toEqual(settings);
        });
    });
});
