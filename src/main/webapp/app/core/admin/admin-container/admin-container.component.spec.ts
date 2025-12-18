import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AdminContainerComponent } from './admin-container.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Component } from '@angular/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Build, CompatibleVersions, Git, Java, ProfileInfo, SentryConfig } from 'app/core/layouts/profiles/profile-info.model';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { of } from 'rxjs';

@Component({ template: '', standalone: true })
class MockEmptyComponent {}

describe('AdminContainerComponent', () => {
    let component: AdminContainerComponent;
    let fixture: ComponentFixture<AdminContainerComponent>;
    let profileService: ProfileService;

    const mockGit: Git = {
        branch: '',
        commit: {
            id: { abbrev: '' },
            user: { name: '', email: '' },
            time: '',
        },
    };

    const mockProfileInfo: ProfileInfo = {
        activeProfiles: [],
        activeModuleFeatures: [],
        programmingLanguageFeatures: [],
        build: new Build(),
        buildTimeoutDefault: 0,
        buildTimeoutMax: 0,
        buildTimeoutMin: 0,
        compatibleVersions: new CompatibleVersions(),
        contact: '',
        continuousIntegrationName: '',
        defaultContainerCpuCount: 0,
        defaultContainerMemoryLimitInMB: 0,
        defaultContainerMemorySwapLimitInMB: 0,
        externalCredentialProvider: '',
        externalPasswordResetLinkMap: {},
        features: [],
        git: mockGit,
        java: new Java(),
        operatorAdminName: '',
        operatorName: '',
        repositoryAuthenticationMechanisms: [],
        sentry: new SentryConfig(),
        sshCloneURLTemplate: '',
        studentExamStoreSessionData: false,
        testServer: false,
        textAssessmentAnalyticsEnabled: false,
        useExternal: false,
        versionControlName: '',
        versionControlUrl: '',
        localLLMDeploymentEnabled: false,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AdminContainerComponent, TranslateModule.forRoot()],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([{ path: '**', component: MockEmptyComponent }]),
                {
                    provide: ProfileService,
                    useValue: {
                        getProfileInfo: jest.fn().mockReturnValue(mockProfileInfo),
                    },
                },
                {
                    provide: FeatureToggleService,
                    useValue: {
                        getFeatureToggleActive: jest.fn().mockReturnValue(of(false)),
                    },
                },
                {
                    provide: LayoutService,
                    useValue: {
                        subscribeToLayoutChanges: jest.fn().mockReturnValue(of([])),
                        isBreakpointActive: jest.fn().mockReturnValue(true), // Assume sidebar can expand
                    },
                },
            ],
        }).compileComponents();

        profileService = TestBed.inject(ProfileService);
        fixture = TestBed.createComponent(AdminContainerComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should toggle collapse state', () => {
        const initialState = component.isNavbarCollapsed();
        component.toggleCollapseState();
        expect(component.isNavbarCollapsed()).toBe(!initialState);
        component.toggleCollapseState();
        expect(component.isNavbarCollapsed()).toBe(initialState);
    });

    it('should have all feature flags as false by default', () => {
        expect(component.localCIActive).toBeFalse();
        expect(component.ltiEnabled).toBeFalse();
        expect(component.atlasEnabled).toBeFalse();
        expect(component.examEnabled).toBeFalse();
        expect(component.standardizedCompetenciesEnabled).toBeFalse();
    });

    it('should detect feature flags from profile info', () => {
        const profileInfoWithFeatures: ProfileInfo = {
            ...mockProfileInfo,
            activeProfiles: ['localci', 'lti'],
            activeModuleFeatures: ['atlas', 'exam'],
        };

        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfoWithFeatures);

        const newFixture = TestBed.createComponent(AdminContainerComponent);
        const newComponent = newFixture.componentInstance;
        newFixture.detectChanges();

        expect(newComponent.localCIActive).toBeTrue();
        expect(newComponent.ltiEnabled).toBeTrue();
        expect(newComponent.atlasEnabled).toBeTrue();
        expect(newComponent.examEnabled).toBeTrue();
    });
});
