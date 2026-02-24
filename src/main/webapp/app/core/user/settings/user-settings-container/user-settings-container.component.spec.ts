import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { UserSettingsContainerComponent } from 'app/core/user/settings/user-settings-container/user-settings-container.component';
import { MODULE_FEATURE_IRIS, PROFILE_ATHENA } from 'app/app.constants';

describe('UserSettingsContainerComponent', () => {
    let fixture: ComponentFixture<UserSettingsContainerComponent>;
    let component: UserSettingsContainerComponent;

    let translateService: TranslateService;

    const router = new MockRouter();
    router.setUrl('');

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UserSettingsContainerComponent, RouterModule],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: Router, useValue: router },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(UserSettingsContainerComponent);
        component = fixture.componentInstance;
        translateService = TestBed.inject(TranslateService);
        translateService.use('en');
    });

    it('should initialize', async () => {
        component.ngOnInit();
        expect(component.currentUser).toBeDefined();
        expect(component.isAtLeastTutor).toBeTrue();
    });

    it('should set isPasskeyEnabled to false when the module feature is inactive', () => {
        jest.spyOn(component['profileService'], 'isModuleFeatureActive').mockReturnValue(false);
        component.ngOnInit();
        expect(component.isPasskeyEnabled).toBeFalse();
    });

    describe('isAiEnabled behavior', () => {
        /**
         * @param activeProfiles for which true should be returned when calling isProfileActive
         * @param activeModuleFeatures for which true should be returned when calling isModuleFeatureActive
         */
        const spyOnProfileService = (activeProfiles: string[], activeModuleFeatures: string[] = []) => {
            jest.spyOn(component['profileService'], 'isProfileActive').mockImplementation((profile) => activeProfiles.includes(profile));
            jest.spyOn(component['profileService'], 'isModuleFeatureActive').mockImplementation((feature) => activeModuleFeatures.includes(feature));
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
            spyOnProfileService([PROFILE_ATHENA], []);
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
            spyOnProfileService([PROFILE_ATHENA], [MODULE_FEATURE_IRIS]);
            const aiLink = queryAiExperienceLink();
            expect(aiLink).toBeTruthy();
            expect(aiLink?.getAttribute('jhiTranslate')).toBe('artemisApp.userSettings.aiExperience');
        });
    });
});
