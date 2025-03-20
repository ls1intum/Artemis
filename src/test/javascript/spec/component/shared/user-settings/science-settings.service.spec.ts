import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SettingId } from 'app/shared/constants/user-settings.constants';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { LocalStorageService } from 'ngx-webstorage';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { ProfileService } from '../../../../../../main/webapp/app/core/layouts/profiles/shared/profile.service';
import { PROFILE_ATLAS } from '../../../../../../main/webapp/app/app.constants';
import { ScienceSetting } from 'app/core/user/settings/science-settings/science-settings-structure';
import { ScienceSettingsService } from 'app/core/user/settings/science-settings/science-settings.service';
import { UserSettingsService } from 'app/core/user/settings/user-settings.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

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
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: LocalStorageService, useClass: MockLocalStorageService }],
        })
            .compileComponents()
            .then(() => {
                scienceSettingsService = TestBed.inject(ScienceSettingsService);
                userSettingsService = TestBed.inject(UserSettingsService);

                const profileService = TestBed.inject(ProfileService);
                const profileInfo = new ProfileInfo();
                profileInfo.activeProfiles = [PROFILE_ATLAS];
                jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of(profileInfo));
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
        expect(spy).toHaveBeenCalled();

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
