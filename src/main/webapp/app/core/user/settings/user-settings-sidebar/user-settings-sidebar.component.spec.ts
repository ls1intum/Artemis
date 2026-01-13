import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Component } from '@angular/core';
import { of } from 'rxjs';

import { UserSettingsSidebarComponent } from './user-settings-sidebar.component';
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { User } from 'app/core/user/user.model';

@Component({ template: '', standalone: true })
class MockEmptyComponent {}

describe('UserSettingsSidebarComponent', () => {
    let component: UserSettingsSidebarComponent;
    let fixture: ComponentFixture<UserSettingsSidebarComponent>;

    const mockLayoutService = {
        subscribeToLayoutChanges: jest.fn().mockReturnValue(of([])),
        isBreakpointActive: jest.fn().mockReturnValue(true),
    };

    const mockUser: User = {
        id: 1,
        login: 'testuser',
        firstName: 'Test',
        lastName: 'User',
        email: 'test@example.com',
        imageUrl: undefined,
        langKey: 'en',
        internal: true,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UserSettingsSidebarComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LayoutService, useValue: mockLayoutService },
                provideRouter([{ path: '**', component: MockEmptyComponent }]),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(UserSettingsSidebarComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('sidebarGroups', () => {
        it('should contain Profile & Account group with 2 items', () => {
            fixture.componentRef.setInput('isNavbarCollapsed', false);
            fixture.componentRef.setInput('isPasskeyEnabled', false);
            fixture.componentRef.setInput('isAtLeastTutor', false);
            fixture.componentRef.setInput('isUsingExternalLLM', false);
            fixture.componentRef.setInput('currentUser', mockUser);
            fixture.detectChanges();

            const groups = component.sidebarGroups();
            const profileGroup = groups.find((g) => g.translation === 'artemisApp.userSettings.groups.profileAndAccount');
            expect(profileGroup).toBeDefined();
            expect(profileGroup?.items).toHaveLength(2);
            expect(profileGroup?.items.map((i) => i.routerLink)).toEqual(['/user-settings/account', '/user-settings/profile']);
        });

        it('should contain Security group with SSH key by default', () => {
            fixture.componentRef.setInput('isNavbarCollapsed', false);
            fixture.componentRef.setInput('isPasskeyEnabled', false);
            fixture.componentRef.setInput('isAtLeastTutor', false);
            fixture.componentRef.setInput('isUsingExternalLLM', false);
            fixture.componentRef.setInput('currentUser', mockUser);
            fixture.detectChanges();

            const groups = component.sidebarGroups();
            const securityGroup = groups.find((g) => g.translation === 'artemisApp.userSettings.groups.security');
            expect(securityGroup).toBeDefined();
            expect(securityGroup?.items.map((i) => i.routerLink)).toContain('/user-settings/ssh');
        });

        it('should include VCS Token in Security group when user is at least tutor', () => {
            fixture.componentRef.setInput('isNavbarCollapsed', false);
            fixture.componentRef.setInput('isPasskeyEnabled', false);
            fixture.componentRef.setInput('isAtLeastTutor', true);
            fixture.componentRef.setInput('isUsingExternalLLM', false);
            fixture.componentRef.setInput('currentUser', mockUser);
            fixture.detectChanges();

            const groups = component.sidebarGroups();
            const securityGroup = groups.find((g) => g.translation === 'artemisApp.userSettings.groups.security');
            expect(securityGroup?.items.map((i) => i.routerLink)).toContain('/user-settings/vcs-token');
        });

        it('should not include VCS Token in Security group when user is not tutor', () => {
            fixture.componentRef.setInput('isNavbarCollapsed', false);
            fixture.componentRef.setInput('isPasskeyEnabled', false);
            fixture.componentRef.setInput('isAtLeastTutor', false);
            fixture.componentRef.setInput('isUsingExternalLLM', false);
            fixture.componentRef.setInput('currentUser', mockUser);
            fixture.detectChanges();

            const groups = component.sidebarGroups();
            const securityGroup = groups.find((g) => g.translation === 'artemisApp.userSettings.groups.security');
            expect(securityGroup?.items.map((i) => i.routerLink)).not.toContain('/user-settings/vcs-token');
        });

        it('should include Passkeys in Security group when passkey is enabled', () => {
            fixture.componentRef.setInput('isNavbarCollapsed', false);
            fixture.componentRef.setInput('isPasskeyEnabled', true);
            fixture.componentRef.setInput('isAtLeastTutor', false);
            fixture.componentRef.setInput('isUsingExternalLLM', false);
            fixture.componentRef.setInput('currentUser', mockUser);
            fixture.detectChanges();

            const groups = component.sidebarGroups();
            const securityGroup = groups.find((g) => g.translation === 'artemisApp.userSettings.groups.security');
            expect(securityGroup?.items.map((i) => i.routerLink)).toContain('/user-settings/passkeys');
        });

        it('should not include Passkeys in Security group when passkey is disabled', () => {
            fixture.componentRef.setInput('isNavbarCollapsed', false);
            fixture.componentRef.setInput('isPasskeyEnabled', false);
            fixture.componentRef.setInput('isAtLeastTutor', false);
            fixture.componentRef.setInput('isUsingExternalLLM', false);
            fixture.componentRef.setInput('currentUser', mockUser);
            fixture.detectChanges();

            const groups = component.sidebarGroups();
            const securityGroup = groups.find((g) => g.translation === 'artemisApp.userSettings.groups.security');
            expect(securityGroup?.items.map((i) => i.routerLink)).not.toContain('/user-settings/passkeys');
        });

        it('should contain Preferences group with 3 items', () => {
            fixture.componentRef.setInput('isNavbarCollapsed', false);
            fixture.componentRef.setInput('isPasskeyEnabled', false);
            fixture.componentRef.setInput('isAtLeastTutor', false);
            fixture.componentRef.setInput('isUsingExternalLLM', false);
            fixture.componentRef.setInput('currentUser', mockUser);
            fixture.detectChanges();

            const groups = component.sidebarGroups();
            const preferencesGroup = groups.find((g) => g.translation === 'artemisApp.userSettings.groups.preferences');
            expect(preferencesGroup).toBeDefined();
            expect(preferencesGroup?.items).toHaveLength(3);
            expect(preferencesGroup?.items.map((i) => i.routerLink)).toEqual(['/user-settings/ide-preferences', '/user-settings/notifications', '/user-settings/quiz-training']);
        });

        it('should include External LLM Usage in Privacy group when using external LLM', () => {
            fixture.componentRef.setInput('isNavbarCollapsed', false);
            fixture.componentRef.setInput('isPasskeyEnabled', false);
            fixture.componentRef.setInput('isAtLeastTutor', false);
            fixture.componentRef.setInput('isUsingExternalLLM', true);
            fixture.componentRef.setInput('currentUser', mockUser);
            fixture.detectChanges();

            const groups = component.sidebarGroups();
            const privacyGroup = groups.find((g) => g.translation === 'artemisApp.userSettings.groups.privacy');
            expect(privacyGroup?.items.map((i) => i.routerLink)).toContain('/user-settings/external-data');
        });

        it('should not include External LLM Usage in Privacy group when not using external LLM', () => {
            fixture.componentRef.setInput('isNavbarCollapsed', false);
            fixture.componentRef.setInput('isPasskeyEnabled', false);
            fixture.componentRef.setInput('isAtLeastTutor', false);
            fixture.componentRef.setInput('isUsingExternalLLM', false);
            fixture.componentRef.setInput('currentUser', mockUser);
            fixture.detectChanges();

            const groups = component.sidebarGroups();
            const privacyGroup = groups.find((g) => g.translation === 'artemisApp.userSettings.groups.privacy');
            expect(privacyGroup?.items.map((i) => i.routerLink)).not.toContain('/user-settings/external-data');
        });

        it('should always include Science Settings in Privacy group', () => {
            fixture.componentRef.setInput('isNavbarCollapsed', false);
            fixture.componentRef.setInput('isPasskeyEnabled', false);
            fixture.componentRef.setInput('isAtLeastTutor', false);
            fixture.componentRef.setInput('isUsingExternalLLM', false);
            fixture.componentRef.setInput('currentUser', mockUser);
            fixture.detectChanges();

            const groups = component.sidebarGroups();
            const privacyGroup = groups.find((g) => g.translation === 'artemisApp.userSettings.groups.privacy');
            expect(privacyGroup?.items.map((i) => i.routerLink)).toContain('/user-settings/science');
        });
    });

    describe('toggleCollapseState output', () => {
        it('should emit toggleCollapseState when triggered', () => {
            fixture.componentRef.setInput('isNavbarCollapsed', false);
            fixture.componentRef.setInput('isPasskeyEnabled', false);
            fixture.componentRef.setInput('isAtLeastTutor', false);
            fixture.componentRef.setInput('isUsingExternalLLM', false);
            fixture.componentRef.setInput('currentUser', mockUser);
            fixture.detectChanges();

            const emitSpy = jest.spyOn(component.toggleCollapseState, 'emit');
            component.toggleCollapseState.emit();
            expect(emitSpy).toHaveBeenCalled();
        });
    });

    describe('canExpand', () => {
        it('should return true when breakpoint is active', () => {
            mockLayoutService.isBreakpointActive.mockReturnValue(true);
            fixture.componentRef.setInput('isNavbarCollapsed', false);
            fixture.componentRef.setInput('isPasskeyEnabled', false);
            fixture.componentRef.setInput('isAtLeastTutor', false);
            fixture.componentRef.setInput('isUsingExternalLLM', false);
            fixture.componentRef.setInput('currentUser', mockUser);
            fixture.detectChanges();

            expect(component.canExpand()).toBeTrue();
        });

        it('should return false when breakpoint is not active', () => {
            mockLayoutService.isBreakpointActive.mockReturnValue(false);
            fixture.componentRef.setInput('isNavbarCollapsed', false);
            fixture.componentRef.setInput('isPasskeyEnabled', false);
            fixture.componentRef.setInput('isAtLeastTutor', false);
            fixture.componentRef.setInput('isUsingExternalLLM', false);
            fixture.componentRef.setInput('currentUser', mockUser);
            fixture.detectChanges();

            expect(component.canExpand()).toBeFalse();
        });
    });
});
