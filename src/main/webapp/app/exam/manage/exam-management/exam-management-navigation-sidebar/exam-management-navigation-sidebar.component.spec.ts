import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamManagementNavigationSidebarComponent } from './exam-management-navigation-sidebar.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { By } from '@angular/platform-browser';

describe('ExamManagementNavigationSidebarComponent', () => {
    let component: ExamManagementNavigationSidebarComponent;
    let fixture: ComponentFixture<ExamManagementNavigationSidebarComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamManagementNavigationSidebarComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamManagementNavigationSidebarComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render the sidebar wrapper with standard classes', () => {
        fixture.detectChanges();
        const sidebarWrap = fixture.debugElement.query(By.css('.sidebar-wrap'));
        expect(sidebarWrap).toBeTruthy();
        const sidebar = fixture.debugElement.query(By.css('.sidebar'));
        expect(sidebar).toBeTruthy();
        const sidebarWidth = fixture.debugElement.query(By.css('.sidebar-width'));
        expect(sidebarWidth).toBeTruthy();
    });

    it('should render the documentation button with Exams type', () => {
        fixture.detectChanges();
        const docButton = fixture.debugElement.query(By.css('jhi-documentation-button'));
        expect(docButton).toBeTruthy();
    });

    it('should render the title when pageTitle is set', () => {
        fixture.componentRef.setInput('pageTitle', 'artemisApp.examManagement.title');
        fixture.detectChanges();
        const titleElement = fixture.debugElement.query(By.css('jhi-course-title-bar-title'));
        expect(titleElement).toBeTruthy();
    });

    it('should not render the title when pageTitle is empty', () => {
        fixture.componentRef.setInput('pageTitle', '');
        fixture.detectChanges();
        const titleElement = fixture.debugElement.query(By.css('jhi-course-title-bar-title'));
        expect(titleElement).toBeFalsy();
    });

    it('should render the toggle button when expanded', () => {
        fixture.componentRef.setInput('isCollapsed', false);
        fixture.detectChanges();
        const toggleButton = fixture.debugElement.query(By.css('jhi-course-sidebar-toggle-button'));
        expect(toggleButton).toBeTruthy();
    });

    it('should hide the toggle button when collapsed', () => {
        fixture.componentRef.setInput('isCollapsed', true);
        fixture.detectChanges();
        const toggleButton = fixture.debugElement.query(By.css('jhi-course-sidebar-toggle-button'));
        expect(toggleButton).toBeFalsy();
    });

    it('should emit toggleSidebar when the toggle button is clicked', () => {
        fixture.componentRef.setInput('isCollapsed', false);
        fixture.detectChanges();
        let emitted = false;
        component.toggleSidebar.subscribe(() => {
            emitted = true;
        });
        const toggleButton = fixture.debugElement.query(By.css('jhi-course-sidebar-toggle-button'));
        toggleButton.triggerEventHandler('toggleSidebar', undefined);
        expect(emitted).toBe(true);
    });

    it('should have documentationType set to Exams', () => {
        expect(component.documentationType).toBe('Exams');
    });
});
