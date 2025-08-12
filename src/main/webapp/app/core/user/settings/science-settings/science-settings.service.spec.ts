import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SettingId } from 'app/shared/constants/user-settings.constants';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProfileService } from '../../../layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';
import { ScienceSetting } from 'app/core/user/settings/science-settings/science-settings-structure';
import { ScienceSettingsService } from 'app/core/user/settings/science-settings/science-settings.service';
import { UserSettingsService } from 'app/core/user/settings/directive/user-settings.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { Setting } from 'app/core/user/settings/user-settings.model';

const scienceSetting: ScienceSetting = {
    settingId: SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING,
    changed: false,
    descriptionKey: 'activityDescription',
    key: 'activity',
    active: false,
};

const scienceSettingsForTesting: ScienceSetting[] = [scienceSetting];

describe('ScienceSettingsService', () => {
    let scienceSettingsService: ScienceSettingsService;
    let userSettingsService: UserSettingsService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: ProfileService, useClass: MockProfileService }],
        })
            .compileComponents()
            .then(() => {
                scienceSettingsService = TestBed.inject(ScienceSettingsService);
                userSettingsService = TestBed.inject(UserSettingsService);

                const profileService = TestBed.inject(ProfileService);
                const profileInfo = new ProfileInfo();
                profileInfo.activeModuleFeatures = [MODULE_FEATURE_ATLAS];
                jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should refresh settings after user settings changed', () => {
        userSettingsService.userSettingsChangeEvent.subscribe = jest.fn().mockImplementation((callback) => {
            callback();
        });

        const spy = jest.spyOn(userSettingsService, 'loadSettings').mockReturnValue(of(new HttpResponse<Setting[]>({ body: scienceSettingsForTesting })));

        scienceSettingsService['listenForScienceSettingsChanges']();
        expect(spy).toHaveBeenCalledOnce();

        const settings = scienceSettingsService.getScienceSettings();
        expect(settings).toEqual(scienceSettingsForTesting);
    });

    it('should provide getters for science settings and updates to it', () => {
        jest.spyOn(userSettingsService, 'loadSettings').mockReturnValue(of(new HttpResponse<Setting[]>({ body: scienceSettingsForTesting })));
        scienceSettingsService.refreshScienceSettings();

        const settings = scienceSettingsService.getScienceSettings();
        expect(settings).not.toBeEmpty();
        expect(settings).toEqual(scienceSettingsForTesting);

        // Subscribing to the updates
        scienceSettingsService.getScienceSettingsUpdates().subscribe((updatedSettings) => {
            expect(updatedSettings).toEqual(settings);
        });
    });
});
