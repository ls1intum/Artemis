import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamManagementNavigationSidebarComponent } from './exam-management-navigation-sidebar.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Event, NavigationEnd, Router, convertToParamMap } from '@angular/router';
import { Subject } from 'rxjs';

describe('ExamManagementNavigationSidebarComponent', () => {
    let component: ExamManagementNavigationSidebarComponent;
    let fixture: ComponentFixture<ExamManagementNavigationSidebarComponent>;
    let routerEventsSubject: Subject<Event>;
    let mockRouterState: any;

    beforeEach(async () => {
        routerEventsSubject = new Subject<Event>();
        mockRouterState = {
            root: {
                snapshot: {
                    paramMap: convertToParamMap({}),
                    firstChild: null,
                },
            },
        };

        await TestBed.configureTestingModule({
            imports: [ExamManagementNavigationSidebarComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: Router,
                    useValue: {
                        events: routerEventsSubject.asObservable(),
                        routerState: mockRouterState,
                    },
                },
                { provide: ActivatedRoute, useValue: { snapshot: {} } },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamManagementNavigationSidebarComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('course', { id: 1 } as any);
        fixture.componentRef.setInput('exams', [
            { id: 1, title: 'Exam 1' },
            { id: 2, title: 'Exam 2' },
        ]);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render the sidebar wrapper with standard classes', () => {
        fixture.detectChanges();
        const sidebarWrap = fixture.debugElement.query(By.css('.sidebar-wrap'));
        expect(sidebarWrap).not.toBeNull();
        const sidebar = fixture.debugElement.query(By.css('.sidebar'));
        expect(sidebar).not.toBeNull();
        const sidebarWidth = fixture.debugElement.query(By.css('.sidebar-width'));
        expect(sidebarWidth).not.toBeNull();
    });

    it('should render the documentation button with Exams type', () => {
        fixture.detectChanges();
        const docButton = fixture.debugElement.query(By.css('jhi-documentation-button'));
        expect(docButton).not.toBeNull();
    });

    it('should render the title when pageTitle is set', () => {
        fixture.componentRef.setInput('pageTitle', 'artemisApp.examManagement.title');
        fixture.detectChanges();
        const titleElement = fixture.debugElement.query(By.css('jhi-course-title-bar-title'));
        expect(titleElement).not.toBeNull();
    });

    it('should not render the title when pageTitle is empty', () => {
        fixture.componentRef.setInput('pageTitle', '');
        fixture.detectChanges();
        const titleElement = fixture.debugElement.query(By.css('jhi-course-title-bar-title'));
        expect(titleElement).toBeNull();
    });

    it('should render the toggle button when expanded', () => {
        fixture.componentRef.setInput('isCollapsed', false);
        fixture.detectChanges();
        const toggleButton = fixture.debugElement.query(By.css('jhi-course-sidebar-toggle-button'));
        expect(toggleButton).not.toBeNull();
    });

    it('should hide the toggle button when collapsed', () => {
        fixture.componentRef.setInput('isCollapsed', true);
        fixture.detectChanges();
        const toggleButton = fixture.debugElement.query(By.css('jhi-course-sidebar-toggle-button'));
        expect(toggleButton).toBeNull();
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

    it('should toggle exam expansion state', () => {
        expect(component.expandedExams().has(1)).toBe(false);
        component.toggleExam(1);
        expect(component.expandedExams().has(1)).toBe(true);
        component.toggleExam(1);
        expect(component.expandedExams().has(1)).toBe(false);
    });

    it('should expand active exam on route change and scroll into view', () => {
        vi.useFakeTimers();
        const dummyElement = document.createElement('div');
        dummyElement.id = 'exam-2';
        dummyElement.scrollIntoView = vi.fn();
        document.body.appendChild(dummyElement);

        mockRouterState.root.snapshot.firstChild = {
            paramMap: convertToParamMap({ examId: '2' }),
            firstChild: null,
        };

        routerEventsSubject.next(new NavigationEnd(1, '/course/1/exams/2', '/course/1/exams/2'));

        expect(component.expandedExams().has(2)).toBe(true);

        vi.advanceTimersByTime(100);
        expect(dummyElement.scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'nearest' });

        document.body.removeChild(dummyElement);
        vi.useRealTimers();
    });

    it('should ignore non-numeric examId in route param', () => {
        mockRouterState.root.snapshot.firstChild = {
            paramMap: convertToParamMap({ examId: 'invalid' }),
            firstChild: null,
        };

        routerEventsSubject.next(new NavigationEnd(1, '/course/1/exams/invalid', '/course/1/exams/invalid'));

        expect(component.expandedExams().size).toBe(0);
    });

    it('should unsubscribe from router events when destroyed', () => {
        expect(routerEventsSubject.observed).toBe(true);
        fixture.destroy();
        expect(routerEventsSubject.observed).toBe(false);
    });

    it('should toggle exam on header click in onPanelClick', () => {
        const toggleSpy = vi.spyOn(component, 'toggleExam');
        const headerElement = document.createElement('div');
        const mockEvent = { target: headerElement } as unknown as MouseEvent;

        component.onPanelClick(1, mockEvent);

        expect(toggleSpy).toHaveBeenCalledWith(1);
    });

    it('should not toggle exam when clicking panel content container or toggler in onPanelClick', () => {
        const toggleSpy = vi.spyOn(component, 'toggleExam');

        const contentElement = document.createElement('div');
        contentElement.className = 'tum-ui-panel-content-container';
        const innerChild = document.createElement('span');
        contentElement.appendChild(innerChild);

        const contentEvent = { target: innerChild } as unknown as MouseEvent;
        component.onPanelClick(1, contentEvent);
        expect(toggleSpy).not.toHaveBeenCalled();

        const togglerElement = document.createElement('div');
        togglerElement.className = 'tum-ui-panel-toggler';
        const togglerEvent = { target: togglerElement } as unknown as MouseEvent;
        component.onPanelClick(1, togglerEvent);
        expect(toggleSpy).not.toHaveBeenCalled();
    });
});
