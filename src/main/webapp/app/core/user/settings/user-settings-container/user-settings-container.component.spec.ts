import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
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
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { ExternalDataGuard } from 'app/core/user/settings/external-data.guard';
import { of } from 'rxjs';
import { Component } from '@angular/core';

@Component({ template: '', standalone: true })
class MockEmptyComponent {}

describe('UserSettingsContainerComponent', () => {
    let fixture: ComponentFixture<UserSettingsContainerComponent>;
    let component: UserSettingsContainerComponent;

    let translateService: TranslateService;
    let layoutService: LayoutService;

    const router = new MockRouter();
    router.setUrl('');

    const mockLayoutService = {
        subscribeToLayoutChanges: jest.fn().mockReturnValue(of([])),
        isBreakpointActive: jest.fn().mockReturnValue(true),
    };

    const mockExternalDataGuard = {
        isUsingExternalLLM: jest.fn().mockReturnValue(false),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UserSettingsContainerComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: Router, useValue: router },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: LayoutService, useValue: mockLayoutService },
                { provide: ExternalDataGuard, useValue: mockExternalDataGuard },
                provideRouter([{ path: '**', component: MockEmptyComponent }]),
            ],
        })
            .overrideTemplate(UserSettingsContainerComponent, '')
            .compileComponents();
        fixture = TestBed.createComponent(UserSettingsContainerComponent);
        component = fixture.componentInstance;
        translateService = TestBed.inject(TranslateService);
        layoutService = TestBed.inject(LayoutService);
        translateService.use('en');
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should initialize', async () => {
        component.ngOnInit();
        expect(component.currentUser).toBeDefined();
        expect(component.isAtLeastTutor()).toBeTrue();
    });

    it('should set isPasskeyEnabled to false when the module feature is inactive', () => {
        jest.spyOn(component['profileService'], 'isModuleFeatureActive').mockReturnValue(false);
        component.ngOnInit();
        expect(component.isPasskeyEnabled()).toBeFalse();
    });

    it('should set isPasskeyEnabled to true when the module feature is active', () => {
        jest.spyOn(component['profileService'], 'isModuleFeatureActive').mockReturnValue(true);
        component.ngOnInit();
        expect(component.isPasskeyEnabled()).toBeTrue();
    });

    describe('toggle collapse state', () => {
        it('should toggle collapse state', () => {
            const initialState = component.isNavbarCollapsed();
            component.toggleCollapseState();
            expect(component.isNavbarCollapsed()).toBe(!initialState);
            component.toggleCollapseState();
            expect(component.isNavbarCollapsed()).toBe(initialState);
        });
    });

    describe('onResize', () => {
        it('should call updateCollapseState when window resizes', () => {
            jest.spyOn(layoutService, 'isBreakpointActive').mockReturnValue(false);

            component.onResize();

            expect(component.isNavbarCollapsed()).toBeTrue();
        });

        it('should not collapse navbar when breakpoint is active on resize', () => {
            jest.spyOn(layoutService, 'isBreakpointActive').mockReturnValue(true);
            component.isNavbarCollapsed.set(false);

            component.onResize();

            // When breakpoint is active, updateCollapseState doesn't change the state
            expect(component.isNavbarCollapsed()).toBeFalse();
        });
    });

    describe('onKeyDown', () => {
        it('should toggle collapse state when Ctrl+M is pressed and breakpoint is active', () => {
            jest.spyOn(layoutService, 'isBreakpointActive').mockReturnValue(true);
            const initialState = component.isNavbarCollapsed();
            const event = new KeyboardEvent('keydown', { ctrlKey: true, key: 'm' });
            const preventDefaultSpy = jest.spyOn(event, 'preventDefault');

            component.onKeyDown(event);

            expect(preventDefaultSpy).toHaveBeenCalled();
            expect(component.isNavbarCollapsed()).toBe(!initialState);
        });

        it('should not toggle collapse state when breakpoint is not active', () => {
            jest.spyOn(layoutService, 'isBreakpointActive').mockReturnValue(false);
            const initialState = component.isNavbarCollapsed();
            const event = new KeyboardEvent('keydown', { ctrlKey: true, key: 'm' });
            const preventDefaultSpy = jest.spyOn(event, 'preventDefault');

            component.onKeyDown(event);

            expect(preventDefaultSpy).not.toHaveBeenCalled();
            expect(component.isNavbarCollapsed()).toBe(initialState);
        });
    });

    describe('isUsingExternalLLM behavior', () => {
        it('should set isUsingExternalLLM to false when neither athena nor iris is active', () => {
            mockExternalDataGuard.isUsingExternalLLM.mockReturnValue(false);
            component.ngOnInit();
            expect(component.isUsingExternalLLM()).toBeFalse();
        });

        it('should set isUsingExternalLLM to true when athena is active', () => {
            mockExternalDataGuard.isUsingExternalLLM.mockReturnValue(true);
            component.ngOnInit();
            expect(component.isUsingExternalLLM()).toBeTrue();
        });

        it('should set isUsingExternalLLM to true when iris is active', () => {
            mockExternalDataGuard.isUsingExternalLLM.mockReturnValue(true);
            component.ngOnInit();
            expect(component.isUsingExternalLLM()).toBeTrue();
        });
    });

    describe('ngOnDestroy', () => {
        it('should unsubscribe from subscriptions on destroy', () => {
            component.ngOnInit();
            expect(() => component.ngOnDestroy()).not.toThrow();
        });
    });
});
