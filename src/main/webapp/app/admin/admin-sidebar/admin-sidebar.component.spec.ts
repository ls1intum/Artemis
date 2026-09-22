/**
 * Vitest tests for AdminSidebarComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Component } from '@angular/core';

import { AdminSidebarComponent } from './admin-sidebar.component';

@Component({ template: '', standalone: true })
class MockEmptyComponent {}

describe('AdminSidebarComponent', () => {
    let component: AdminSidebarComponent;
    let fixture: ComponentFixture<AdminSidebarComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AdminSidebarComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: '**', component: MockEmptyComponent }])],
        })
            .overrideTemplate(AdminSidebarComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(AdminSidebarComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should have default input values as false', () => {
        expect(component.isNavbarCollapsed()).toBe(false);
        expect(component.localCIActive()).toBe(false);
        expect(component.ltiEnabled()).toBe(false);
        expect(component.standardizedCompetenciesEnabled()).toBe(false);
        expect(component.scienceEnabled()).toBe(false);
        expect(component.atlasEnabled()).toBe(false);
        expect(component.examEnabled()).toBe(false);
        expect(component.passkeyEnabled()).toBe(false);
        expect(component.isSuperAdmin()).toBe(false);
    });

    it('should generate sidebar groups', () => {
        const groups = component.sidebarGroups();
        expect(groups.length).toBeGreaterThan(0);

        expect(groups[0].translation).toBe('global.menu.admin.groups.usersAndOrganizations');
        expect(groups[0].items).toHaveLength(3); // User Management, Organizations, Data Exports
    });

    it('should include Build System group when localCIActive is true', () => {
        fixture.componentRef.setInput('localCIActive', true);
        fixture.detectChanges();

        const groups = component.sidebarGroups();
        const buildSystemGroup = groups.find((g) => g.translation === 'global.menu.admin.groups.buildSystem');
        expect(buildSystemGroup).toBeTruthy();
        expect(buildSystemGroup!.items).toHaveLength(2);
    });

    it('should not include Build System group when localCIActive is false', () => {
        fixture.componentRef.setInput('localCIActive', false);
        fixture.detectChanges();

        const groups = component.sidebarGroups();
        const buildSystemGroup = groups.find((g) => g.translation === 'global.menu.admin.groups.buildSystem');
        expect(buildSystemGroup).toBeFalsy();
    });

    it('should emit toggleCollapseState when called', () => {
        const toggleSpy = vi.spyOn(component.toggleCollapseState, 'emit');
        component.toggleCollapseState.emit();
        expect(toggleSpy).toHaveBeenCalled();
    });

    it('should include passkey management link when passkeys are enabled and user is super admin', () => {
        fixture.componentRef.setInput('passkeyEnabled', true);
        fixture.componentRef.setInput('isSuperAdmin', true);
        fixture.detectChanges();

        const groups = component.sidebarGroups();
        const userManagementGroup = groups.find((g) => g.translation === 'global.menu.admin.groups.usersAndOrganizations');
        expect(userManagementGroup).toBeTruthy();
        expect(userManagementGroup!.items).toHaveLength(4); // User Management, Organizations, Data Exports, Passkey Management

        const passkeyManagementItem = userManagementGroup!.items.find((i) => i.routerLink === '/admin/passkey-management');
        expect(passkeyManagementItem).toBeTruthy();
        expect(passkeyManagementItem!.translation).toBe('global.menu.admin.sidebar.passkeyManagement');
        expect(passkeyManagementItem!.testId).toBe('admin-passkey-management');
    });

    it('should include science link when atlas and science are enabled', () => {
        fixture.componentRef.setInput('atlasEnabled', true);
        fixture.componentRef.setInput('scienceEnabled', true);
        fixture.detectChanges();

        const contentGroup = component.sidebarGroups().find((g) => g.translation === 'global.menu.admin.groups.contentAndLearning');
        const scienceItem = contentGroup!.items.find((i) => i.routerLink === '/admin/science');

        expect(scienceItem).toBeTruthy();
        expect(scienceItem!.translation).toBe('global.menu.admin.sidebar.science');
    });

    it('should not include science link when science is disabled', () => {
        fixture.componentRef.setInput('atlasEnabled', true);
        fixture.componentRef.setInput('scienceEnabled', false);
        fixture.detectChanges();

        const contentGroup = component.sidebarGroups().find((g) => g.translation === 'global.menu.admin.groups.contentAndLearning');

        expect(contentGroup!.items.find((i) => i.routerLink === '/admin/science')).toBeFalsy();
    });

    it('should not include passkey management link when passkey is not enabled', () => {
        fixture.componentRef.setInput('passkeyEnabled', false);
        fixture.componentRef.setInput('isSuperAdmin', true);
        fixture.detectChanges();

        const groups = component.sidebarGroups();
        const userManagementGroup = groups.find((g) => g.translation === 'global.menu.admin.groups.usersAndOrganizations');
        expect(userManagementGroup).toBeTruthy();
        expect(userManagementGroup!.items).toHaveLength(3); // User Management, Organizations, Data Exports only

        const passkeyManagementItem = userManagementGroup!.items.find((i) => i.routerLink === '/admin/passkey-management');
        expect(passkeyManagementItem).toBeFalsy();
    });

    it('should not include passkey management link when user is not super admin', () => {
        fixture.componentRef.setInput('passkeyEnabled', true);
        fixture.componentRef.setInput('isSuperAdmin', false);
        fixture.detectChanges();

        const groups = component.sidebarGroups();
        const userManagementGroup = groups.find((g) => g.translation === 'global.menu.admin.groups.usersAndOrganizations');
        expect(userManagementGroup).toBeTruthy();
        expect(userManagementGroup!.items).toHaveLength(3); // User Management, Organizations, Data Exports only

        const passkeyManagementItem = userManagementGroup!.items.find((i) => i.routerLink === '/admin/passkey-management');
        expect(passkeyManagementItem).toBeFalsy();
    });
});
