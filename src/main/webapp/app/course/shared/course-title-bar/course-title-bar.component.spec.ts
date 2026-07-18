import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockDirective } from 'ng-mocks';
import { CourseTitleBarComponent } from 'app/course/shared/course-title-bar/course-title-bar.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CourseSidebarToggleButtonComponent } from 'app/course/shared/course-sidebar-toggle-button/course-sidebar-toggle-button.component';

describe('CourseTitleBarComponent', () => {
    let component: CourseTitleBarComponent;
    let fixture: ComponentFixture<CourseTitleBarComponent>;
    let toggleSidebarSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockDirective(TranslateDirective)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseTitleBarComponent);
        component = fixture.componentInstance;

        // Set default input values
        fixture.componentRef.setInput('hasSidebar', false);
        fixture.componentRef.setInput('isSidebarCollapsed', false);
        fixture.componentRef.setInput('isExamStarted', false);

        toggleSidebarSpy = vi.spyOn(component.toggleSidebar, 'emit');

        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should hide component when exam is started', () => {
        // Initially visible
        const titleBar = fixture.debugElement.query(By.css('#course-title-bar'));
        expect(titleBar.styles['display']).not.toBe('none');

        // Set exam started to true
        fixture.componentRef.setInput('isExamStarted', true);
        fixture.detectChanges();

        expect(titleBar.styles['display']).toBe('none');
    });

    it('should not show sidebar toggle button when hasSidebar is false', () => {
        fixture.componentRef.setInput('hasSidebar', false);
        fixture.detectChanges();

        const sidebarToggleBtn = fixture.debugElement.query(By.css('.btn-sidebar-collapse'));
        expect(sidebarToggleBtn).toBeNull();
    });

    it('should show sidebar toggle button when hasSidebar is true', () => {
        fixture.componentRef.setInput('hasSidebar', true);
        fixture.detectChanges();

        const sidebarToggleBtn = fixture.debugElement.query(By.css('.btn-sidebar-collapse'));
        expect(sidebarToggleBtn).toBeTruthy();
    });

    it('should apply is-collapsed class when sidebar is collapsed', () => {
        fixture.componentRef.setInput('hasSidebar', true);
        fixture.componentRef.setInput('isSidebarCollapsed', true);
        fixture.detectChanges();

        const sidebarToggleBtn = fixture.debugElement.query(By.css('.btn-sidebar-collapse'));
        expect(sidebarToggleBtn.classes['is-collapsed']).toBeTruthy();
    });

    it('should apply is-communication-module class when pageTitle is "communication"', () => {
        fixture.componentRef.setInput('pageTitle', 'communication');
        fixture.componentRef.setInput('hasSidebar', true);
        fixture.detectChanges();

        const sidebarToggleBtn = fixture.debugElement.query(By.css('.btn-sidebar-collapse'));
        expect(sidebarToggleBtn.classes['is-communication-module']).toBeTruthy();
    });

    it('should emit toggleSidebar event when sidebar button is clicked', () => {
        fixture.componentRef.setInput('hasSidebar', true);
        fixture.detectChanges();

        const sidebarToggleBtn = fixture.debugElement.query(By.css('.btn-sidebar-collapse'));
        sidebarToggleBtn.triggerEventHandler('click', null);

        expect(toggleSidebarSpy).toHaveBeenCalled();
    });

    it('should display the page title', () => {
        fixture.componentRef.setInput('pageTitle', 'Custom Title');
        fixture.detectChanges();

        const titleElement = fixture.debugElement.query(By.css('h5'));
        expect(titleElement.nativeElement.textContent.trim()).toBe('Custom Title');
    });

    it('should set jhiTranslate directive with pageTitle value', () => {
        fixture.componentRef.setInput('pageTitle', 'custom.translation.key');
        fixture.detectChanges();

        // we want to get a very specific translate directive, so we cannot use TestBed.inject
        const titleElement = fixture.debugElement.query(By.css('h5'));
        const translateDirective = titleElement.injector.get(TranslateDirective);

        expect(translateDirective.jhiTranslate()).toBe('custom.translation.key');
    });

    it('should render content through ng-content', () => {
        // Add content to simulate ng-content
        const contentElement = document.createElement('div');
        contentElement.className = 'test-content';
        contentElement.textContent = 'Test Content';

        // Get the div where ng-content would be rendered
        const contentContainer = fixture.debugElement.query(By.css('.d-flex.gap-2')).nativeElement;
        contentContainer.appendChild(contentElement);

        fixture.detectChanges();

        const renderedContent = fixture.debugElement.query(By.css('.test-content'));
        expect(renderedContent).toBeTruthy();
        expect(renderedContent.nativeElement.textContent).toBe('Test Content');
    });

    it('should render the shared sidebar toggle button', () => {
        fixture.componentRef.setInput('hasSidebar', true);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.directive(CourseSidebarToggleButtonComponent))).toBeTruthy();
    });

    it('should have the correct styling classes on the title bar', () => {
        const titleBar = fixture.debugElement.query(By.css('#course-title-bar'));
        expect(titleBar.classes['module-bg']).toBeTruthy();
        expect(titleBar.classes['rounded']).toBeTruthy();
        expect(titleBar.classes['rounded-3']).toBeTruthy();
        expect(titleBar.classes['sticky-top']).toBeTruthy();
    });
});
