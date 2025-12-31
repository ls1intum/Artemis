/**
 * Vitest tests for AdminContainerComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { provideRouter } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Component } from '@angular/core';
import { of } from 'rxjs';

import { AdminContainerComponent } from './admin-container.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Build, CompatibleVersions, Git, Java, ProfileInfo, SentryConfig } from 'app/core/layouts/profiles/profile-info.model';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { LayoutService } from 'app/shared/breakpoints/layout.service';

@Component({ template: '', standalone: true })
class MockEmptyComponent {}

describe('AdminContainerComponent', () => {
    setupTestBed({ zoneless: true });

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
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AdminContainerComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([{ path: '**', component: MockEmptyComponent }]),
                {
                    provide: ProfileService,
                    useValue: {
                        getProfileInfo: vi.fn().mockReturnValue(mockProfileInfo),
                    },
                },
                {
                    provide: FeatureToggleService,
                    useValue: {
                        getFeatureToggleActive: vi.fn().mockReturnValue(of(false)),
                    },
                },
                {
                    provide: LayoutService,
                    useValue: {
                        subscribeToLayoutChanges: vi.fn().mockReturnValue(of([])),
                        isBreakpointActive: vi.fn().mockReturnValue(true),
                    },
                },
            ],
        })
            .overrideTemplate(AdminContainerComponent, '')
            .compileComponents();

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
        expect(component.localCIActive()).toBe(false);
        expect(component.ltiEnabled()).toBe(false);
        expect(component.atlasEnabled()).toBe(false);
        expect(component.examEnabled()).toBe(false);
        expect(component.standardizedCompetenciesEnabled()).toBe(false);
    });

    it('should detect feature flags from profile info', () => {
        const profileInfoWithFeatures: ProfileInfo = {
            ...mockProfileInfo,
            activeProfiles: ['localci', 'lti'],
            activeModuleFeatures: ['atlas', 'exam'],
        };

        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfoWithFeatures);

        const newFixture = TestBed.createComponent(AdminContainerComponent);
        const newComponent = newFixture.componentInstance;
        newFixture.detectChanges();

        expect(newComponent.localCIActive()).toBe(true);
        expect(newComponent.ltiEnabled()).toBe(true);
        expect(newComponent.atlasEnabled()).toBe(true);
        expect(newComponent.examEnabled()).toBe(true);
    });

    describe('onResize', () => {
        it('should call updateCollapseState when window resizes', () => {
            const layoutService = TestBed.inject(LayoutService);
            vi.spyOn(layoutService, 'isBreakpointActive').mockReturnValue(false);

            component.onResize();

            expect(component.isNavbarCollapsed()).toBe(true);
        });

        it('should not collapse navbar when breakpoint is active on resize', () => {
            const layoutService = TestBed.inject(LayoutService);
            vi.spyOn(layoutService, 'isBreakpointActive').mockReturnValue(true);
            component.isNavbarCollapsed.set(false);

            component.onResize();

            // When breakpoint is active, updateCollapseState doesn't change the state
            expect(component.isNavbarCollapsed()).toBe(false);
        });
    });

    describe('onKeyDown', () => {
        it('should toggle collapse state when Ctrl+M is pressed and breakpoint is active', () => {
            const layoutService = TestBed.inject(LayoutService);
            vi.spyOn(layoutService, 'isBreakpointActive').mockReturnValue(true);
            const initialState = component.isNavbarCollapsed();
            const event = new KeyboardEvent('keydown', { ctrlKey: true, key: 'm' });
            const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

            component.onKeyDown(event);

            expect(preventDefaultSpy).toHaveBeenCalled();
            expect(component.isNavbarCollapsed()).toBe(!initialState);
        });

        it('should not toggle collapse state when breakpoint is not active', () => {
            const layoutService = TestBed.inject(LayoutService);
            vi.spyOn(layoutService, 'isBreakpointActive').mockReturnValue(false);
            const initialState = component.isNavbarCollapsed();
            const event = new KeyboardEvent('keydown', { ctrlKey: true, key: 'm' });
            const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

            component.onKeyDown(event);

            expect(preventDefaultSpy).not.toHaveBeenCalled();
            expect(component.isNavbarCollapsed()).toBe(initialState);
        });
    });

    describe('updateCollapseState', () => {
        it('should auto-collapse navbar on smaller screens', () => {
            const layoutService = TestBed.inject(LayoutService);
            vi.spyOn(layoutService, 'isBreakpointActive').mockReturnValue(false);
            component.isNavbarCollapsed.set(false);

            // Trigger updateCollapseState via onResize
            component.onResize();

            expect(component.isNavbarCollapsed()).toBe(true);
        });

        it('should not change collapsed state when breakpoint is active', () => {
            const layoutService = TestBed.inject(LayoutService);
            vi.spyOn(layoutService, 'isBreakpointActive').mockReturnValue(true);
            component.isNavbarCollapsed.set(false);

            // Trigger updateCollapseState via onResize
            component.onResize();

            expect(component.isNavbarCollapsed()).toBe(false);
        });
    });

    describe('ngOnDestroy', () => {
        it('should unsubscribe from subscriptions on destroy', () => {
            component.ngOnInit();
            expect(() => component.ngOnDestroy()).not.toThrow();
        });
    });
});
