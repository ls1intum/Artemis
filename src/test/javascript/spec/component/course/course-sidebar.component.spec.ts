import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { Course, CourseInformationSharingConfiguration } from 'app/entities/course.model';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NgbDropdown, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { NgClass, NgTemplateOutlet, SlicePipe } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ActivatedRoute, RouterLink, RouterLinkActive } from '@angular/router';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { faChalkboardUser, faChartColumn, faGraduationCap, faListAlt } from '@fortawesome/free-solid-svg-icons';
import { CourseActionItem, CourseSidebarComponent, SidebarItem } from 'app/core/course/shared/course-sidebar/course-sidebar.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';

describe('CourseSidebarComponent', () => {
    let component: CourseSidebarComponent;
    let fixture: ComponentFixture<CourseSidebarComponent>;

    const course1: Course = {
        id: 1,
        title: 'Course1',
        description: 'Description of course 1',
        courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
        courseIconPath: 'path/to/icon.png',
        studentCourseAnalyticsDashboardEnabled: true,
    };

    const course2: Course = {
        id: 2,
        title: 'Course2',
        description: 'Description of course 2',
        courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
    };

    const mockSidebarItems: SidebarItem[] = [
        {
            routerLink: 'dashboard',
            icon: faListAlt,
            title: 'Dashboard',
            translation: 'artemisApp.courseOverview.menu.dashboard',
            featureToggle: FeatureToggle.StudentCourseAnalyticsDashboard,
            hidden: false,
        },
        {
            routerLink: 'exercises',
            icon: faListAlt,
            title: 'Exercises',
            translation: 'artemisApp.courseOverview.menu.exercises',
            hidden: false,
        },
        {
            routerLink: 'statistics',
            icon: faChartColumn,
            title: 'Statistics',
            translation: 'artemisApp.courseOverview.menu.statistics',
            guidedTour: true,
            hidden: false,
        },
        {
            routerLink: 'lectures',
            icon: faChalkboardUser,
            title: 'Lectures',
            translation: 'artemisApp.courseOverview.menu.lectures',
            hidden: false,
        },
        {
            routerLink: 'exams',
            icon: faGraduationCap,
            title: 'Exams',
            testId: 'exam-tab',
            translation: 'artemisApp.courseOverview.menu.exams',
            hidden: false,
        },
    ];

    const mockActionItems: CourseActionItem[] = [
        {
            title: 'Unenroll',
            translation: 'artemisApp.courseOverview.exerciseList.details.unenrollmentButton',
            action: () => {},
        },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                NgClass,
                NgTemplateOutlet,
                SlicePipe,
                MockModule(NgbTooltipModule),
                MockModule(BrowserAnimationsModule),
                RouterLink,
                RouterLinkActive,
                CourseSidebarComponent,
                MockComponent(SecuredImageComponent),
                MockDirective(NgbDropdown),
                MockDirective(TranslateDirective),
                MockComponent(FaIconComponent),
                MockDirective(FeatureToggleHideDirective),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTranslatePipe),
            ],
            declarations: [],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseSidebarComponent);
        component = fixture.componentInstance;

        // Set up initial inputs
        fixture.componentRef.setInput('course', course1);
        fixture.componentRef.setInput('courses', [course1, course2]);
        fixture.componentRef.setInput('sidebarItems', mockSidebarItems);
        fixture.componentRef.setInput('courseActionItems', mockActionItems);
        fixture.detectChanges();
    });

    it('should initialize visible/hidden items on component initialization', () => {
        const updateVisibleNavbarItemsSpy = jest.spyOn(component, 'updateVisibleNavbarItems');

        component.ngOnInit();

        expect(updateVisibleNavbarItemsSpy).toHaveBeenCalledWith(window.innerHeight);
    });
    it('should call updateVisibleNavbarItems on window resize', () => {
        const updateVisibleNavbarItemsSpy = jest.spyOn(component, 'updateVisibleNavbarItems');

        window.dispatchEvent(new Event('resize'));

        expect(updateVisibleNavbarItemsSpy).toHaveBeenCalledWith(window.innerHeight);
    });

    it('should check if dropdown should be closed when updating visible navbar items', () => {
        const mockDropdown = {
            open: jest.fn(),
            close: jest.fn(),
        };
        component.itemsDrop = mockDropdown as unknown as NgbDropdown;

        jest.spyOn(component, 'applyThreshold').mockImplementation((threshold, height) => {
            component.anyItemHidden.set(false);
            component.hiddenItems.set([]);
        });

        component.updateVisibleNavbarItems(window.innerHeight);

        expect(mockDropdown.close).toHaveBeenCalled();
    });

    it('should calculate threshold based on sidebar items length', () => {
        const threshold = component.calculateThreshold();

        // WINDOW_OFFSET = 300, ITEM_HEIGHT = 38, sidebarItems.length = 5
        const expectedThreshold = 300 + 5 * 38;

        expect(threshold).toBe(expectedThreshold);
    });

    it('should apply threshold to determine hidden items', () => {
        const threshold = 1000; // High value to force items to be hidden
        const height = 500;

        component.applyThreshold(threshold, height);

        expect(component.anyItemHidden()).toBe(true);
        expect(component.hiddenItems().length).toBeGreaterThan(0);

        component.applyThreshold(100, 1000);

        expect(component.anyItemHidden()).toBe(false);
        expect(component.hiddenItems().length).toBe(0);
    });

    it('should display course title when navbar is not collapsed', () => {
        fixture.componentRef.setInput('isNavbarCollapsed', false);
        fixture.detectChanges();

        const titleElement = fixture.debugElement.query(By.css('#test-course-title'));
        expect(titleElement).toBeTruthy();
        expect(titleElement.nativeElement.textContent).toBe('Course1');
    });

    it('should display more icon and label if at least one item gets hidden in the sidebar', () => {
        component.anyItemHidden.set(true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.three-dots').hidden).toBeFalse();

        component.anyItemHidden.set(false);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.three-dots').hidden).toBeTrue();
    });
    it('should display course icon when available', () => {
        fixture.detectChanges();

        const iconElement = fixture.nativeElement.querySelector('jhi-secured-image');
        expect(iconElement).not.toBeNull();
    });

    it('should not display course icon when not available', () => {
        // course without icon
        fixture.componentRef.setInput('course', course2);
        fixture.componentRef.setInput('courses', [course2]);
        fixture.detectChanges();

        const iconElement = fixture.debugElement.query(By.directive(SecuredImageComponent));
        const iconElement2 = fixture.nativeElement.querySelector('.course-circle');
        expect(iconElement).toBeNull();
        expect(iconElement2).not.toBeNull();
    });

    it('should emit toggleCollapseState when collapse chevron is clicked', () => {
        const toggleCollapseStateSpy = jest.spyOn(component.toggleCollapseState, 'emit');
        fixture.detectChanges();
        const collapseButton = fixture.debugElement.query(By.css('.double-arrow'));
        expect(collapseButton).toBeTruthy();
        collapseButton.nativeElement.click();
        expect(toggleCollapseStateSpy).toHaveBeenCalled();
    });

    it('should emit switchCourse when a course is selected from dropdown', () => {
        const switchCourseSpy = jest.spyOn(component.switchCourse, 'emit');
        fixture.componentRef.setInput('courses', [course1, course2]);
        fixture.detectChanges();

        const courseDropdownItem = fixture.debugElement.query(By.css('[ngbDropdownItem]'));
        courseDropdownItem.nativeElement.click();
        expect(switchCourseSpy).toHaveBeenCalled();
    });

    it('should emit courseActionItemClick when an action item is clicked', () => {
        const courseActionItemClickSpy = jest.spyOn(component.courseActionItemClick, 'emit');
        component.anyItemHidden.set(false);
        fixture.detectChanges();
        const actionItem = fixture.debugElement.query(By.css('#action-item-0'));
        actionItem.nativeElement.click();
        expect(courseActionItemClickSpy).toHaveBeenCalledWith(mockActionItems[0]);
    });
});
