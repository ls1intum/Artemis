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
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';

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

    it('should render the title linking to exams overview', () => {
        fixture.detectChanges();
        const titleElement = fixture.debugElement.query(By.css('jhi-course-title-bar-title'));
        expect(titleElement).not.toBeNull();
        expect(titleElement.componentInstance.title()).toBe('artemisApp.examManagement.title');
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

    it('should scroll the active link into view rather than the panel around it', () => {
        vi.useFakeTimers();
        // The panel is what the old code scrolled to. On a deep link far down the list its header is already on
        // screen, so `nearest` moves nothing while the link inside it stays below the fold.
        const panel = document.createElement('div');
        panel.id = 'exam-2';
        panel.scrollIntoView = vi.fn();
        document.body.appendChild(panel);

        const activeLink = document.createElement('a');
        activeLink.classList.add('active');
        activeLink.scrollIntoView = vi.fn();
        fixture.nativeElement.appendChild(activeLink);

        mockRouterState.root.snapshot.firstChild = {
            paramMap: convertToParamMap({ examId: '2' }),
            firstChild: null,
        };
        routerEventsSubject.next(new NavigationEnd(1, '/course/1/exams/2/students', '/course/1/exams/2/students'));

        vi.advanceTimersByTime(100);
        expect(activeLink.scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'nearest' });
        expect(panel.scrollIntoView).not.toHaveBeenCalled();

        activeLink.remove();
        document.body.removeChild(panel);
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
        const expandSpy = vi.spyOn(component as any, 'expandActiveExam');
        routerEventsSubject.next(new NavigationEnd(1, '/course/1/exams/1', '/course/1/exams/1'));
        expect(expandSpy).toHaveBeenCalledTimes(1);

        fixture.destroy();

        routerEventsSubject.next(new NavigationEnd(2, '/course/1/exams/1', '/course/1/exams/1'));
        expect(expandSpy).toHaveBeenCalledTimes(1);
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

    describe('subpage items', () => {
        it.each([
            {
                role: 'instructor',
                course: { id: 1, isAtLeastInstructor: true, isAtLeastEditor: true, isAtLeastTutor: true } as Course,
                expectedSubpages: [
                    'sidebar-subpage-detail',
                    'sidebar-subpage-edit',
                    'sidebar-subpage-exercise-groups',
                    'sidebar-subpage-students',
                    'sidebar-subpage-test-runs',
                    'sidebar-subpage-assessment-dashboard',
                    'sidebar-subpage-grading',
                    'sidebar-subpage-scores',
                ],
            },
            {
                role: 'editor',
                course: { id: 1, isAtLeastInstructor: false, isAtLeastEditor: true, isAtLeastTutor: true } as Course,
                expectedSubpages: ['sidebar-subpage-exercise-groups', 'sidebar-subpage-assessment-dashboard'],
            },
            {
                role: 'tutor',
                course: { id: 1, isAtLeastInstructor: false, isAtLeastEditor: false, isAtLeastTutor: true } as Course,
                expectedSubpages: ['sidebar-subpage-assessment-dashboard'],
            },
        ])('should render the correct subpage items for $role', ({ course, expectedSubpages }) => {
            fixture.componentRef.setInput('course', course);
            fixture.componentRef.setInput('exams', [{ id: 1, title: 'Exam 1' } as Exam]);
            fixture.detectChanges();

            const actualSubpages = fixture.debugElement.queryAll(By.css('[data-testid^="sidebar-subpage-"]')).map((el) => el.nativeElement.getAttribute('data-testid'));

            expect(new Set(actualSubpages)).toEqual(new Set(expectedSubpages));
            expect(actualSubpages).toEqual(expectedSubpages);
        });

        it('should not render any subpage items when course has no role flags', () => {
            fixture.componentRef.setInput('course', { id: 1 } as Course);
            fixture.detectChanges();

            const actualSubpages = fixture.debugElement.queryAll(By.css('[data-testid^="sidebar-subpage-"]'));
            expect(actualSubpages).toHaveLength(0);
        });

        it('should not render grading and assessment dashboard subpages for a test exam', () => {
            fixture.componentRef.setInput('course', {
                id: 1,
                isAtLeastInstructor: true,
                isAtLeastEditor: true,
                isAtLeastTutor: true,
            } as Course);
            fixture.componentRef.setInput('exams', [{ id: 1, title: 'Test Exam', testExam: true } as Exam]);
            fixture.detectChanges();

            const actualSubpages = fixture.debugElement.queryAll(By.css('[data-testid^="sidebar-subpage-"]')).map((el) => el.nativeElement.getAttribute('data-testid'));

            expect(actualSubpages).not.toContain('sidebar-subpage-assessment-dashboard');
            expect(actualSubpages).not.toContain('sidebar-subpage-grading');

            const expectedTestExamSubpages = [
                'sidebar-subpage-detail',
                'sidebar-subpage-edit',
                'sidebar-subpage-exercise-groups',
                'sidebar-subpage-students',
                'sidebar-subpage-test-runs',
                'sidebar-subpage-scores',
            ];
            expect(new Set(actualSubpages)).toEqual(new Set(expectedTestExamSubpages));
            expect(actualSubpages).toEqual(expectedTestExamSubpages);
        });

        it('should not render assessment dashboard for a tutor on a test exam', () => {
            fixture.componentRef.setInput('course', {
                id: 1,
                isAtLeastInstructor: false,
                isAtLeastEditor: false,
                isAtLeastTutor: true,
            } as Course);
            fixture.componentRef.setInput('exams', [{ id: 1, title: 'Test Exam', testExam: true } as Exam]);
            fixture.detectChanges();

            const actualSubpages = fixture.debugElement.queryAll(By.css('[data-testid^="sidebar-subpage-"]'));
            expect(actualSubpages).toHaveLength(0);
        });

        it('should render the empty state when there are no exams', () => {
            fixture.componentRef.setInput('course', {
                id: 1,
                isAtLeastInstructor: true,
                isAtLeastEditor: true,
                isAtLeastTutor: true,
            } as Course);
            fixture.componentRef.setInput('exams', []);
            fixture.detectChanges();

            const emptyContainer = fixture.debugElement.query(By.css('.p-2.text-center.text-muted'));
            expect(emptyContainer).not.toBeNull();

            const emptyMessage = fixture.debugElement.query(By.css('[jhiTranslate="artemisApp.exam.overview.noExams"]'));
            expect(emptyMessage).not.toBeNull();
            expect(emptyMessage.nativeElement.textContent).toContain('artemisApp.exam.overview.noExams');

            const panels = fixture.debugElement.queryAll(By.css('tum-ui-panel'));
            expect(panels).toHaveLength(0);

            const subpages = fixture.debugElement.queryAll(By.css('[data-testid^="sidebar-subpage-"]'));
            expect(subpages).toHaveLength(0);
        });
    });
});
