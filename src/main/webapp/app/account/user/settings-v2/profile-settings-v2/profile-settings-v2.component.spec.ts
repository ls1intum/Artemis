import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';
import { AccountInformationComponent } from 'app/account/user/settings/account-information/account-information.component';
import { GlobalNotificationsSettingsComponent } from 'app/account/user/settings/global-notifications-settings/global-notifications-settings.component';
import { ScienceSettingsComponent } from 'app/account/user/settings/science-settings/science-settings.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProfileSettingsV2Component } from './profile-settings-v2.component';

@Component({ selector: 'jhi-account-information', template: '' })
class MockAccountInformationComponent {}

@Component({ selector: 'jhi-email-notifications-settings', template: '' })
class MockGlobalNotificationsSettingsComponent {}

@Component({ selector: 'jhi-science-settings', template: '' })
class MockScienceSettingsComponent {}

describe('ProfileSettingsV2Component', () => {
    let fixture: ComponentFixture<ProfileSettingsV2Component>;
    let profileService: { isModuleFeatureActive: ReturnType<typeof vi.fn> };

    beforeEach(async () => {
        profileService = { isModuleFeatureActive: vi.fn().mockReturnValue(false) };

        await TestBed.configureTestingModule({
            imports: [ProfileSettingsV2Component],
            providers: [{ provide: ProfileService, useValue: profileService }],
        })
            .overrideComponent(ProfileSettingsV2Component, {
                remove: { imports: [AccountInformationComponent, GlobalNotificationsSettingsComponent, ScienceSettingsComponent] },
                add: { imports: [MockAccountInformationComponent, MockGlobalNotificationsSettingsComponent, MockScienceSettingsComponent] },
            })
            .compileComponents();
    });

    const createComponent = (): void => {
        fixture = TestBed.createComponent(ProfileSettingsV2Component);
        fixture.detectChanges();
    };

    it('always renders Account Information and Notifications', () => {
        createComponent();

        expect(fixture.nativeElement.querySelector('jhi-account-information')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('jhi-email-notifications-settings')).toBeTruthy();
    });

    it('does not instantiate Data Sharing when Atlas is unavailable', () => {
        createComponent();

        expect(fixture.nativeElement.querySelector('jhi-science-settings')).toBeFalsy();
    });

    it('renders Data Sharing when Atlas is available', () => {
        profileService.isModuleFeatureActive.mockImplementation((feature) => feature === MODULE_FEATURE_ATLAS);

        createComponent();

        expect(fixture.nativeElement.querySelector('jhi-science-settings')).toBeTruthy();
    });
});
