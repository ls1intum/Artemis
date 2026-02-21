import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SettingId } from 'app/shared/constants/user-settings.constants';
import { Subject, of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProfileService } from '../../../layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';
import { ScienceSetting } from 'app/core/user/settings/science-settings/science-settings-structure';
import { SCIENCE_SETTING_LOCAL_STORAGE_KEY, ScienceSettingsService } from 'app/core/user/settings/science-settings/science-settings.service';
import { UserSettingsService } from 'app/core/user/settings/directive/user-settings.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { Setting } from 'app/core/user/settings/user-settings.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

const scienceSetting: ScienceSetting = {
    settingId: SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING,
    changed: false,
    descriptionKey: 'activityDescription',
    key: 'activity',
    active: false,
};

const scienceSettingActive: ScienceSetting = {
    settingId: SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING,
    changed: false,
    descriptionKey: 'activityDescription',
    key: 'activity',
    active: true,
};

const scienceSettingsForTesting: ScienceSetting[] = [scienceSetting];

describe('ScienceSettingsService', () => {
    setupTestBed({ zoneless: true });

    let scienceSettingsService: ScienceSettingsService;
    let userSettingsService: UserSettingsService;
    let localStorageService: LocalStorageService;
    let profileService: ProfileService;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: ProfileService, useClass: MockProfileService }],
        });
        await TestBed.compileComponents();
        scienceSettingsService = TestBed.inject(ScienceSettingsService);
        userSettingsService = TestBed.inject(UserSettingsService);
        localStorageService = TestBed.inject(LocalStorageService);
        profileService = TestBed.inject(ProfileService);

        const profileInfo = new ProfileInfo();
        profileInfo.activeModuleFeatures = [MODULE_FEATURE_ATLAS];
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        localStorageService.remove(SCIENCE_SETTING_LOCAL_STORAGE_KEY);
    });

    it('should refresh settings after user settings changed', () => {
        const changes$ = new Subject<string>();
        // Point the service's observable to our controllable subject
        userSettingsService.userSettingsChangeEvent = changes$.asObservable();

        const spy = vi.spyOn(userSettingsService, 'loadSettings').mockReturnValue(of(new HttpResponse<Setting[]>({ body: scienceSettingsForTesting })));

        scienceSettingsService['listenForScienceSettingsChanges']();

        expect(spy).not.toHaveBeenCalled();

        // Simulate the user settings change
        changes$.next('');

        expect(spy).toHaveBeenCalledOnce();
        expect(scienceSettingsService.getScienceSettings()).toEqual(scienceSettingsForTesting);
    });

    it('should provide getters for science settings and updates to it', () => {
        vi.spyOn(userSettingsService, 'loadSettings').mockReturnValue(of(new HttpResponse<Setting[]>({ body: scienceSettingsForTesting })));
        scienceSettingsService.refreshScienceSettings();

        const settings = scienceSettingsService.getScienceSettings();
        expect(settings.length).toBeGreaterThan(0);
        expect(settings).toEqual(scienceSettingsForTesting);

        // Subscribing to the updates
        scienceSettingsService.getScienceSettingsUpdates().subscribe((updatedSettings) => {
            expect(updatedSettings).toEqual(settings);
        });
    });

    it('should not refresh settings when ATLAS module is not active', () => {
        const profileInfo = new ProfileInfo();
        profileInfo.activeModuleFeatures = [];
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

        const spy = vi.spyOn(userSettingsService, 'loadSettings');

        scienceSettingsService.refreshScienceSettings();

        expect(spy).not.toHaveBeenCalled();
    });

    it('should return true for eventLoggingAllowed when no settings exist', () => {
        vi.spyOn(localStorageService, 'retrieve').mockReturnValue(undefined);

        const result = scienceSettingsService.eventLoggingAllowed();

        expect(result).toBe(true);
    });

    it('should return true for eventLoggingAllowed when activity setting is active', () => {
        vi.spyOn(localStorageService, 'retrieve').mockReturnValue([scienceSettingActive]);

        const result = scienceSettingsService.eventLoggingAllowed();

        expect(result).toBe(true);
    });

    it('should return false for eventLoggingAllowed when activity setting is not active', () => {
        vi.spyOn(localStorageService, 'retrieve').mockReturnValue([scienceSetting]);

        const result = scienceSettingsService.eventLoggingAllowed();

        expect(result).toBe(false);
    });

    it('should return true for eventLoggingAllowed when activity setting is missing', () => {
        const settingWithDifferentKey: ScienceSetting = {
            settingId: SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING,
            changed: false,
            descriptionKey: 'otherDescription',
            key: 'other',
            active: false,
        };
        vi.spyOn(localStorageService, 'retrieve').mockReturnValue([settingWithDifferentKey]);

        const result = scienceSettingsService.eventLoggingAllowed();

        expect(result).toBe(true);
    });

    it('should initialize and set up storage event listener', () => {
        const addEventListenerSpy = vi.spyOn(globalThis, 'addEventListener');
        vi.spyOn(localStorageService, 'retrieve').mockReturnValue(scienceSettingsForTesting);

        scienceSettingsService.initialize();

        expect(addEventListenerSpy).toHaveBeenCalledWith('storage', expect.any(Function));
    });

    it('should handle storage event for science settings key', async () => {
        vi.spyOn(localStorageService, 'retrieve').mockReturnValue(scienceSettingsForTesting);

        scienceSettingsService.initialize();

        // Simulate storage event
        const event = new StorageEvent('storage', {
            key: 'jhi-' + SCIENCE_SETTING_LOCAL_STORAGE_KEY,
        });
        window.dispatchEvent(event);

        // Give time for the subject to emit
        await new Promise((resolve) => setTimeout(resolve, 10));

        // The subject should have emitted the settings
        let receivedSettings: ScienceSetting[] = [];
        scienceSettingsService.getScienceSettingsUpdates().subscribe((settings) => {
            receivedSettings = settings;
        });

        expect(receivedSettings).toEqual(scienceSettingsForTesting);
    });

    it('should not update subject for storage event with different key', async () => {
        const retrieveSpy = vi.spyOn(localStorageService, 'retrieve').mockReturnValue(scienceSettingsForTesting);

        scienceSettingsService.initialize();

        // Clear the call count after initialize
        retrieveSpy.mockClear();

        // Simulate storage event with different key
        const event = new StorageEvent('storage', {
            key: 'some-other-key',
        });
        window.dispatchEvent(event);

        await new Promise((resolve) => setTimeout(resolve, 10));

        // Should not have called retrieve again for different key
        expect(retrieveSpy).not.toHaveBeenCalled();
    });

    it('should store settings when provided', () => {
        const storeSpy = vi.spyOn(localStorageService, 'store');
        vi.spyOn(localStorageService, 'retrieve').mockReturnValue(scienceSettingsForTesting);

        scienceSettingsService['storeScienceSettings'](scienceSettingsForTesting);

        expect(storeSpy).toHaveBeenCalledWith(SCIENCE_SETTING_LOCAL_STORAGE_KEY, scienceSettingsForTesting);
    });

    it('should remove settings when undefined is provided', () => {
        const removeSpy = vi.spyOn(localStorageService, 'remove');
        vi.spyOn(localStorageService, 'retrieve').mockReturnValue([]);

        scienceSettingsService['storeScienceSettings'](undefined);

        expect(removeSpy).toHaveBeenCalledWith(SCIENCE_SETTING_LOCAL_STORAGE_KEY);
    });

    it('should return empty array when no stored settings exist', () => {
        vi.spyOn(localStorageService, 'retrieve').mockReturnValue(undefined);

        const result = scienceSettingsService.getScienceSettings();

        expect(result).toEqual([]);
    });
});
