import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { UserSettingsService } from './user-settings.service';
import { SettingId, UserSettingsCategory } from 'app/foundation/constants/user-settings.constants';
import { Setting, UserSettingsStructure } from 'app/account/user/settings/user-settings.model';
import { ScienceSetting } from 'app/account/user/settings/profile/science-settings/science-settings-structure';
import { User } from 'app/openapi/model/user';

describe('UserSettingsService', () => {
    let service: UserSettingsService;
    let httpMock: HttpTestingController;

    const mockScienceSettings: ScienceSetting[] = [
        {
            settingId: SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING,
            key: 'activity',
            descriptionKey: 'activityDescription',
            active: true,
            changed: false,
        },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), UserSettingsService],
        });
        service = TestBed.inject(UserSettingsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('loadSettings', () => {
        it('should load science settings', () => {
            service.loadSettings(UserSettingsCategory.SCIENCE_SETTINGS).subscribe((response) => {
                expect(response.body).toEqual(mockScienceSettings);
            });

            const req = httpMock.expectOne(service.scienceSettingsResourceUrl);
            expect(req.request.method).toBe('GET');
            req.flush(mockScienceSettings);
        });

        it('should use default case for unknown category', () => {
            // Even for an unrecognized category, it should fall through to the default case (SCIENCE_SETTINGS)
            service.loadSettings('UNKNOWN_CATEGORY' as UserSettingsCategory).subscribe((response) => {
                expect(response.body).toEqual(mockScienceSettings);
            });

            const req = httpMock.expectOne(service.scienceSettingsResourceUrl);
            expect(req.request.method).toBe('GET');
            req.flush(mockScienceSettings);
        });
    });

    describe('saveSettings', () => {
        it('should save science settings', () => {
            service.saveSettings(mockScienceSettings, UserSettingsCategory.SCIENCE_SETTINGS).subscribe((response) => {
                expect(response.body).toEqual(mockScienceSettings);
            });

            const req = httpMock.expectOne(service.scienceSettingsResourceUrl);
            expect(req.request.method).toBe('PUT');
            expect(req.request.body).toEqual(mockScienceSettings);
            req.flush(mockScienceSettings);
        });

        it('should use default case for unknown category when saving', () => {
            service.saveSettings(mockScienceSettings, 'UNKNOWN_CATEGORY' as UserSettingsCategory).subscribe((response) => {
                expect(response.body).toEqual(mockScienceSettings);
            });

            const req = httpMock.expectOne(service.scienceSettingsResourceUrl);
            expect(req.request.method).toBe('PUT');
            req.flush(mockScienceSettings);
        });
    });

    describe('loadSettingsSuccessAsSettingsStructure', () => {
        it('should return updated settings structure', () => {
            const receivedSettings: ScienceSetting[] = [
                {
                    settingId: SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING,
                    key: 'activity',
                    descriptionKey: 'activityDescription',
                    active: false, // Changed from default
                    changed: false,
                },
            ];

            const result = service.loadSettingsSuccessAsSettingsStructure(receivedSettings, UserSettingsCategory.SCIENCE_SETTINGS);

            expect(result).toBeDefined();
            expect(result.category).toBe(UserSettingsCategory.SCIENCE_SETTINGS);
            expect(result.groups).toBeDefined();
            expect(result.groups.length).toBeGreaterThan(0);
            // The settings in the structure should be updated with the received values
            const setting = result.groups[0].settings[0] as ScienceSetting;
            expect(setting.settingId).toBe(SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING);
            expect(setting.active).toBe(false);
        });
    });

    describe('loadSettingsSuccessAsIndividualSettings', () => {
        it('should return individual settings from structure', () => {
            const receivedSettings: ScienceSetting[] = [
                {
                    settingId: SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING,
                    key: 'activity',
                    descriptionKey: 'activityDescription',
                    active: true,
                    changed: true,
                },
            ];

            const result = service.loadSettingsSuccessAsIndividualSettings(receivedSettings, UserSettingsCategory.SCIENCE_SETTINGS);

            expect(result).toBeDefined();
            expect(result.length).toBeGreaterThan(0);
            // The changed flag should be reset to false
            expect(result[0].changed).toBe(false);
        });
    });

    describe('saveSettingsSuccess', () => {
        it('should update settings structure after successful save', () => {
            const settingsStructure: UserSettingsStructure<Setting> = {
                category: UserSettingsCategory.SCIENCE_SETTINGS,
                groups: [
                    {
                        key: 'general',
                        restrictionLevels: [],
                        settings: [
                            {
                                settingId: SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING,
                                key: 'activity',
                                descriptionKey: 'activityDescription',
                            } as ScienceSetting,
                        ],
                    },
                ],
            };

            const receivedSettings: ScienceSetting[] = [
                {
                    settingId: SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING,
                    key: 'activity',
                    descriptionKey: 'activityDescription',
                    active: false,
                },
            ];

            const result = service.saveSettingsSuccess(settingsStructure, receivedSettings);

            expect(result).toBe(settingsStructure); // Returns the same object, mutated
            const updatedSetting = result.groups[0].settings[0] as ScienceSetting;
            expect(updatedSetting.active).toBe(false);
        });
    });

    describe('extractIndividualSettingsFromSettingsStructure', () => {
        it('should extract all settings from structure and reset changed flag', () => {
            const settingsStructure: UserSettingsStructure<Setting> = {
                category: UserSettingsCategory.SCIENCE_SETTINGS,
                groups: [
                    {
                        key: 'group1',
                        restrictionLevels: [],
                        settings: [
                            {
                                settingId: SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING,
                                key: 'activity',
                                changed: true,
                            } as Setting,
                        ],
                    },
                    {
                        key: 'group2',
                        restrictionLevels: [],
                        settings: [
                            {
                                settingId: SettingId.NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY,
                                key: 'weekly',
                                changed: true,
                            } as Setting,
                        ],
                    },
                ],
            };

            const result = service.extractIndividualSettingsFromSettingsStructure(settingsStructure);

            expect(result.length).toBe(2);
            expect(result[0].settingId).toBe(SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING);
            expect(result[0].changed).toBe(false);
            expect(result[1].settingId).toBe(SettingId.NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY);
            expect(result[1].changed).toBe(false);
        });

        it('should handle empty groups', () => {
            const settingsStructure: UserSettingsStructure<Setting> = {
                category: UserSettingsCategory.SCIENCE_SETTINGS,
                groups: [],
            };

            const result = service.extractIndividualSettingsFromSettingsStructure(settingsStructure);

            expect(result).toEqual([]);
        });
    });

    describe('sendApplyChangesEvent', () => {
        it('should emit event to subscribers', () => {
            const receivedMessages: string[] = [];
            service.userSettingsChangeEvent.subscribe((message) => {
                receivedMessages.push(message);
            });

            service.sendApplyChangesEvent('test-message');

            expect(receivedMessages).toContain('test-message');
        });

        it('should emit multiple events', () => {
            const receivedMessages: string[] = [];
            service.userSettingsChangeEvent.subscribe((message) => {
                receivedMessages.push(message);
            });

            service.sendApplyChangesEvent('message1');
            service.sendApplyChangesEvent('message2');

            expect(receivedMessages).toEqual(['message1', 'message2']);
        });
    });

    it('uploads a single JPEG file with the original bytes and lets the browser set the multipart boundary', async () => {
        const bytes = new Uint8Array([255, 216, 255, 224]);
        const blob = new Blob([bytes], { type: 'image/jpeg' });
        const user: User = { id: 1, login: 'testuser', imageUrl: 'profile.jpeg' };
        const next = vi.fn();

        service.updateProfilePicture(blob).subscribe({ next });

        const req = httpMock.expectOne({ method: 'PUT', url: '/api/account/profile-picture' });
        expect(req.request.headers.has('Content-Type')).toBe(false);
        expect(req.request.body).toBeInstanceOf(FormData);
        const body: FormData = req.request.body;
        expect(Array.from(body.keys())).toEqual(['file']);
        const file = body.get('file');
        if (!(file instanceof File)) {
            throw new Error('Expected a multipart file');
        }
        expect(file.name).toBe('placeholderName.jpeg');
        expect(file.type).toBe('image/jpeg');
        const uploadedBytes = await new Promise<ArrayBuffer>((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result as ArrayBuffer);
            reader.onerror = () => reject(reader.error);
            reader.readAsArrayBuffer(file);
        });
        expect(new Uint8Array(uploadedBytes)).toEqual(bytes);

        req.flush(user);
        expect(next).toHaveBeenCalledExactlyOnceWith(user);
    });

    it('emits on a bodyless 200 when deleting the profile picture', () => {
        const next = vi.fn();
        service.removeProfilePicture().subscribe({ next });

        const req = httpMock.expectOne({ method: 'DELETE', url: '/api/account/profile-picture' });
        req.flush(null, { status: 200, statusText: 'OK' });

        expect(next).toHaveBeenCalledExactlyOnceWith(null);
    });

    describe('updateSettingsStructure (private method via public API)', () => {
        it('should not update settings when no matching settingId is found', () => {
            const receivedSettings: ScienceSetting[] = [
                {
                    settingId: SettingId.NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY,
                    key: 'different',
                    descriptionKey: 'different',
                    active: true,
                },
            ];

            // Using loadSettingsSuccessAsSettingsStructure to indirectly test updateSettingsStructure
            const result = service.loadSettingsSuccessAsSettingsStructure(receivedSettings, UserSettingsCategory.SCIENCE_SETTINGS);

            // The original setting should remain unchanged since no match was found
            const setting = result.groups[0].settings[0] as ScienceSetting;
            expect(setting.settingId).toBe(SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING);
            // The setting should keep its default value since no matching setting was received
            expect(setting.active).toBe(true); // default value from structure
        });

        it('should update matching settings and leave non-matching unchanged', () => {
            const receivedSettings: ScienceSetting[] = [
                {
                    settingId: SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING,
                    key: 'activity',
                    descriptionKey: 'activityDescription',
                    active: false,
                    changed: false,
                },
            ];

            const result = service.loadSettingsSuccessAsSettingsStructure(receivedSettings, UserSettingsCategory.SCIENCE_SETTINGS);

            const setting = result.groups[0].settings[0] as ScienceSetting;
            expect(setting.active).toBe(false); // Updated
        });
    });
});
