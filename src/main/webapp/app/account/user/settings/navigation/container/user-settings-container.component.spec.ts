import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { provideRouter } from '@angular/router';
import { UserSettingsContainerComponent } from './user-settings-container.component';
import { DataGuard } from 'app/account/user/settings/data-guard.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';

describe('UserSettingsContainerComponent', () => {
    let fixture: ComponentFixture<UserSettingsContainerComponent>;
    let component: UserSettingsContainerComponent;
    let profileService: { isModuleFeatureActive: ReturnType<typeof vi.fn> };
    let dataGuard: { isUsingLLM: ReturnType<typeof vi.fn> };

    beforeEach(async () => {
        profileService = { isModuleFeatureActive: vi.fn().mockReturnValue(false) };
        dataGuard = { isUsingLLM: vi.fn().mockReturnValue(false) };

        await TestBed.configureTestingModule({
            imports: [UserSettingsContainerComponent],
            providers: [provideRouter([]), { provide: ProfileService, useValue: profileService }, { provide: DataGuard, useValue: dataGuard }],
        })
            .overrideTemplate(UserSettingsContainerComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(UserSettingsContainerComponent);
        component = fixture.componentInstance;
    });

    it('reads the conditional tab availability from the existing feature checks', () => {
        profileService.isModuleFeatureActive.mockImplementation((feature) => feature === MODULE_FEATURE_ATLAS);
        dataGuard.isUsingLLM.mockReturnValue(true);

        fixture.detectChanges();

        expect(component.isLearningAvailable()).toBe(true);
        expect(component.isAiExperienceAvailable()).toBe(true);
    });

    it('toggles the sidebar on expandable screens', () => {
        fixture.detectChanges();

        expect(component.isSidebarCollapsed()).toBe(false);
        component.toggleSidebar();
        expect(component.isSidebarCollapsed()).toBe(true);
    });
});
