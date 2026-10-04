import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { UserSettingsContainerComponent } from 'app/account/user/settings/user-settings-container/user-settings-container.component';
import { MODULE_FEATURE_ATHENA, MODULE_FEATURE_ATLAS, MODULE_FEATURE_IRIS } from 'app/app.constants';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from 'test/helpers/mocks/service/mock-feature-toggle.service';
import { User } from 'app/account/user/user.model';
import { deepClone } from 'app/foundation/util/deep-clone.util';

describe('UserSettingsContainerComponent', () => {
    let fixture: ComponentFixture<UserSettingsContainerComponent>;
    let component: UserSettingsContainerComponent;

    let translateService: TranslateService;
    let featureToggleService: FeatureToggleService;
    let accountService: AccountService;

    const router = new MockRouter();
    router.setUrl('');

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UserSettingsContainerComponent, RouterModule],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useValue: router },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(UserSettingsContainerComponent);
        component = fixture.componentInstance;
        translateService = TestBed.inject(TranslateService);
        translateService.use('en');
        featureToggleService = TestBed.inject(FeatureToggleService);
        accountService = TestBed.inject(AccountService);
        // The sidebar reads the identity signal, so the logged-in user has to be on it.
        accountService.userIdentity.set({ id: 99, login: 'admin', imageUrl: 'profile-pictures/first.png' } as User);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', async () => {
        component.ngOnInit();
        expect(component.currentUser()).toBeDefined();
        expect(component.isAtLeastTutor()).toBe(true);
    });

    it('follows the profile picture when it changes while the user stays logged in', () => {
        component.ngOnInit();
        expect(component.currentUser()?.imageUrl).toBe('profile-pictures/first.png');

        // What AccountService.setImageUrl does: replace the identity, without a log-in or log-out. The
        // sidebar used to snapshot the authentication state observable, which does not emit here, so the
        // old picture stayed on screen until the next full page load.
        accountService.userIdentity.update((user) => {
            const updated = deepClone(user!);
            updated.imageUrl = 'profile-pictures/second.png';
            return updated;
        });

        expect(component.currentUser()?.imageUrl).toBe('profile-pictures/second.png');
    });

    it('follows the profile picture being removed', () => {
        component.ngOnInit();
        // Asserting the picture is there first is what makes this a guard: without it the expectation
        // below also holds for an identity that never had one.
        expect(component.currentUser()?.imageUrl).toBe('profile-pictures/first.png');

        accountService.userIdentity.update((user) => {
            const updated = deepClone(user!);
            updated.imageUrl = undefined;
            return updated;
        });

        expect(component.currentUser()?.imageUrl).toBeUndefined();
    });

    it('should set isPasskeyEnabled to false when the module feature is inactive', () => {
        vi.spyOn(component['profileService'], 'isModuleFeatureActive').mockReturnValue(false);
        component.ngOnInit();
        expect(component.isPasskeyEnabled()).toBe(false);
    });

    describe('science tab visibility', () => {
        const queryScienceLink = (): HTMLElement | null => {
            fixture.detectChanges();
            return fixture.nativeElement.querySelector('a[routerLink="science"]');
        };

        it('should hide the science tab when the atlas module is inactive (issue #13173)', () => {
            vi.spyOn(component['profileService'], 'isModuleFeatureActive').mockImplementation((feature) => feature !== MODULE_FEATURE_ATLAS);
            component.ngOnInit();
            expect(component.isAtlasEnabled()).toBe(false);
            expect(queryScienceLink()).toBeFalsy();
        });

        it('should show the science tab when the atlas module is active', () => {
            vi.spyOn(component['profileService'], 'isModuleFeatureActive').mockImplementation((feature) => feature === MODULE_FEATURE_ATLAS);
            component.ngOnInit();
            expect(component.isAtlasEnabled()).toBe(true);
            const scienceLink = queryScienceLink();
            expect(scienceLink).toBeTruthy();
            expect(scienceLink?.getAttribute('jhiTranslate')).toBe('artemisApp.userSettings.scienceSettings');
        });
    });

    describe('learner profile tab visibility', () => {
        const queryLearnerProfileLink = (): HTMLElement | null => {
            fixture.detectChanges();
            return fixture.nativeElement.querySelector('a[routerLink="profile"]');
        };

        /** Marks exactly the given module features active, so a test states the whole module configuration it means. */
        const setActiveModules = (features: string[]) => {
            vi.spyOn(component['profileService'], 'isModuleFeatureActive').mockImplementation((feature) => features.includes(feature));
        };

        it('should hide the learner profile tab when no module backs any of its sections', () => {
            setActiveModules([]);
            featureToggleService.setFeatureToggleState(FeatureToggle.Memiris, false);
            component.ngOnInit();
            expect(component.isLearnerProfileEnabled()).toBe(false);
            expect(queryLearnerProfileLink()).toBeFalsy();
        });

        it('should show the learner profile tab when the atlas module is active', () => {
            setActiveModules([MODULE_FEATURE_ATLAS]);
            featureToggleService.setFeatureToggleState(FeatureToggle.Memiris, false);
            component.ngOnInit();
            expect(component.isLearnerProfileEnabled()).toBe(true);
            const learnerProfileLink = queryLearnerProfileLink();
            expect(learnerProfileLink).toBeTruthy();
            expect(learnerProfileLink?.getAttribute('jhiTranslate')).toBe('artemisApp.userSettings.learnerProfile');
        });

        it('should show the learner profile tab for the insights section once Memiris is switched on', () => {
            setActiveModules([MODULE_FEATURE_IRIS]);
            featureToggleService.setFeatureToggleState(FeatureToggle.Memiris, false);
            component.ngOnInit();
            expect(component.isLearnerProfileEnabled()).toBe(false);
            expect(queryLearnerProfileLink()).toBeFalsy();

            featureToggleService.setFeatureToggleState(FeatureToggle.Memiris, true);

            expect(component.isLearnerProfileEnabled()).toBe(true);
            expect(queryLearnerProfileLink()).toBeTruthy();
        });

        it('should hide the learner profile tab when Memiris is on but the iris module serving it is not', () => {
            // IrisMemoryResource is @Conditional(IrisEnabled) as well as @FeatureToggle(Memiris), and the toggle is
            // seeded independently of the module, so the toggle alone must not show a tab that would answer 404
            setActiveModules([]);
            featureToggleService.setFeatureToggleState(FeatureToggle.Memiris, true);
            component.ngOnInit();
            expect(component.isLearnerProfileEnabled()).toBe(false);
            expect(queryLearnerProfileLink()).toBeFalsy();
        });
    });

    describe('isAiEnabled behavior', () => {
        /**
         * @param activeProfiles for which true should be returned when calling isProfileActive
         * @param activeModuleFeatures for which true should be returned when calling isModuleFeatureActive
         */
        const spyOnProfileService = (activeProfiles: string[], activeModuleFeatures: string[] = []) => {
            vi.spyOn(component['profileService'], 'isProfileActive').mockImplementation((profile) => activeProfiles.includes(profile));
            vi.spyOn(component['profileService'], 'isModuleFeatureActive').mockImplementation((feature) => activeModuleFeatures.includes(feature));
        };

        /**
         * Queries the AI Experience link HTML from the component's template.
         */
        const queryAiExperienceLink = (): HTMLElement | null => {
            fixture.detectChanges();
            return fixture.nativeElement.querySelector('a[routerLink="ai-experience"]');
        };

        it('should not display the AI Experience link when neither athena nor iris is active', () => {
            spyOnProfileService([], []);
            const aiLink = queryAiExperienceLink();
            expect(aiLink).toBeFalsy();
        });

        it('should display the AI Experience link when athena is active', () => {
            spyOnProfileService([], [MODULE_FEATURE_ATHENA]);
            const aiLink = queryAiExperienceLink();
            expect(aiLink).toBeTruthy();
            expect(aiLink?.getAttribute('jhiTranslate')).toBe('artemisApp.userSettings.aiExperience');
        });

        it('should display the AI Experience link when iris is active', () => {
            spyOnProfileService([], [MODULE_FEATURE_IRIS]);
            const aiLink = queryAiExperienceLink();
            expect(aiLink).toBeTruthy();
            expect(aiLink?.getAttribute('jhiTranslate')).toBe('artemisApp.userSettings.aiExperience');
        });

        it('should display the AI Experience link when athena and iris are active', () => {
            spyOnProfileService([], [MODULE_FEATURE_ATHENA, MODULE_FEATURE_IRIS]);
            const aiLink = queryAiExperienceLink();
            expect(aiLink).toBeTruthy();
            expect(aiLink?.getAttribute('jhiTranslate')).toBe('artemisApp.userSettings.aiExperience');
        });
    });
});
