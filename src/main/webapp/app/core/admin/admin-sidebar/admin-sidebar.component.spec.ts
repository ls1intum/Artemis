import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AdminSidebarComponent } from './admin-sidebar.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Component } from '@angular/core';

@Component({ template: '', standalone: true })
class MockEmptyComponent {}

describe('AdminSidebarComponent', () => {
    let component: AdminSidebarComponent;
    let fixture: ComponentFixture<AdminSidebarComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AdminSidebarComponent, TranslateModule.forRoot()],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: '**', component: MockEmptyComponent }])],
        }).compileComponents();

        fixture = TestBed.createComponent(AdminSidebarComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should have default input values as false', () => {
        expect(component.isNavbarCollapsed()).toBeFalse();
        expect(component.localCIActive()).toBeFalse();
        expect(component.irisEnabled()).toBeFalse();
        expect(component.ltiEnabled()).toBeFalse();
        expect(component.standardizedCompetenciesEnabled()).toBeFalse();
        expect(component.atlasEnabled()).toBeFalse();
        expect(component.examEnabled()).toBeFalse();
    });

    it('should generate sidebar groups', () => {
        const groups = component.sidebarGroups();
        expect(groups.length).toBeGreaterThan(0);

        // First group should be Users & Organizations
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

    it('should include IRIS in Content & Learning group when irisEnabled is true', () => {
        fixture.componentRef.setInput('irisEnabled', true);
        fixture.detectChanges();

        const groups = component.sidebarGroups();
        const contentGroup = groups.find((g) => g.translation === 'global.menu.admin.groups.contentAndLearning');
        expect(contentGroup).toBeTruthy();
        const irisItem = contentGroup!.items.find((i) => i.routerLink === '/admin/iris');
        expect(irisItem).toBeTruthy();
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
        const toggleSpy = jest.spyOn(component.toggleCollapseState, 'emit');
        component.toggleCollapseState.emit();
        expect(toggleSpy).toHaveBeenCalled();
    });
});
