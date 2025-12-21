import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UserManagementDropdownComponent } from './user-management-dropdown.component';
import { faGraduationCap, faListAlt, faPersonChalkboard, faSchool } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockJhiTranslateDirective } from 'test/helpers/mocks/directive/mock-jhi-translate-directive.directive';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';

describe('UserManagementDropdownComponent', () => {
    let component: UserManagementDropdownComponent;
    let fixture: ComponentFixture<UserManagementDropdownComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: TranslateDirective, useClass: MockJhiTranslateDirective },
                { provide: RouterLink, useClass: MockRouterLinkDirective },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(UserManagementDropdownComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize userAddActions with correct links and translations when courseId is provided', () => {
        fixture.componentRef.setInput('courseId', 123);
        fixture.changeDetectorRef.detectChanges();
        expect(component.userAddActions).toEqual([
            {
                icon: faSchool,
                routerLink: ['/course-management/123/groups/students'],
                label: 'entity.action.addStudent',
                id: 'add-student',
            },
            {
                icon: faPersonChalkboard,
                routerLink: ['/course-management/123/groups/tutors'],
                label: 'entity.action.addTutor',
                id: 'add-tutor',
            },
            {
                icon: faListAlt,
                routerLink: ['/course-management/123/groups/editors'],
                label: 'entity.action.addEditor',
                id: 'add-editor',
            },
            {
                icon: faGraduationCap,
                routerLink: ['/course-management/123/groups/instructors'],
                label: 'entity.action.addInstructor',
                id: 'add-instructor',
            },
        ]);
    });

    it('should not initialize userAddActions when courseId is undefined', () => {
        fixture.componentRef.setInput('courseId', undefined);
        fixture.changeDetectorRef.detectChanges();
        expect(component.userAddActions).toEqual([]);
    });
});
