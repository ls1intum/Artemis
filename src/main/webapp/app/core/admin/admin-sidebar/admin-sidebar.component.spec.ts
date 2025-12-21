/**
 * Vitest tests for AdminSidebarComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { provideRouter } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Component } from '@angular/core';

import { AdminSidebarComponent } from './admin-sidebar.component';

@Component({ template: '', standalone: true })
class MockEmptyComponent {}

describe('AdminSidebarComponent', () => {
    setupTestBed({ zoneless: true });

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
        expect(component.atlasEnabled()).toBe(false);
        expect(component.examEnabled()).toBe(false);
    });

    it('should generate sidebar groups', () => {
        const groups = component.sidebarGroups();
        expect(groups.length).toBeGreaterThan(0);

        expect(groups[0].translation).toBe('global.menu.admin.groups.usersAndOrganizations');
        expect(groups[0].items).toHaveLength(2);
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

    it('should include Exam Rooms in System Configuration when examEnabled is true', () => {
        fixture.componentRef.setInput('examEnabled', true);
        fixture.detectChanges();

        const groups = component.sidebarGroups();
        const systemConfigGroup = groups.find((g) => g.translation === 'global.menu.admin.groups.systemConfiguration');
        expect(systemConfigGroup).toBeTruthy();
        const examRoomsItem = systemConfigGroup!.items.find((i) => i.routerLink === '/admin/exam-rooms');
        expect(examRoomsItem).toBeTruthy();
    });

    it('should emit toggleCollapseState when called', () => {
        const toggleSpy = vi.spyOn(component.toggleCollapseState, 'emit');
        component.toggleCollapseState.emit();
        expect(toggleSpy).toHaveBeenCalled();
    });
});
