import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AdminContainerComponent } from './admin-container.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Component } from '@angular/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { of } from 'rxjs';

@Component({ template: '', standalone: true })
class MockEmptyComponent {}

describe('AdminContainerComponent', () => {
    let component: AdminContainerComponent;
    let fixture: ComponentFixture<AdminContainerComponent>;
    let profileService: ProfileService;

    const mockProfileInfo = {
        activeProfiles: [],
        activeModuleFeatures: [],
        sshCloneURLTemplate: '',
        buildPlanURLTemplate: '',
        registrationEnabled: false,
        needsToAcceptTerms: false,
        externalPasswordResetLinkMap: {},
        git: {
            branch: '',
            commit: {
                id: { abbrev: '' },
                user: { name: '', email: '' },
                time: '',
            },
        },
        theiaPortalURL: '',
        contact: '',
        studentExamStoreSessionData: false,
        testServer: false,
        accountName: '',
    } as ProfileInfo;

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
        expect(component.irisEnabled).toBeFalse();
        expect(component.ltiEnabled).toBeFalse();
        expect(component.atlasEnabled).toBeFalse();
        expect(component.examEnabled).toBeFalse();
        expect(component.standardizedCompetenciesEnabled).toBeFalse();
    });

    it('should detect feature flags from profile info', () => {
        const profileInfoWithFeatures: ProfileInfo = {
            ...mockProfileInfo,
            activeProfiles: ['iris', 'localci', 'lti'],
            activeModuleFeatures: ['atlas', 'exam'],
        };

        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfoWithFeatures);

        const newFixture = TestBed.createComponent(AdminContainerComponent);
        const newComponent = newFixture.componentInstance;
        newFixture.detectChanges();

        expect(newComponent.irisEnabled).toBeTrue();
        expect(newComponent.localCIActive).toBeTrue();
        expect(newComponent.ltiEnabled).toBeTrue();
        expect(newComponent.atlasEnabled).toBeTrue();
        expect(newComponent.examEnabled).toBeTrue();
    });
});
