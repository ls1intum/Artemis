import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseRegistrationSelectorComponent } from 'app/overview/course-registration/course-registration-selector/course-registration-selector.component';
import { Course } from 'app/entities/course.model';
import { ArtemisTestModule } from '../../test.module';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseRegistrationSelectorComponent', () => {
    let fixture: ComponentFixture<CourseRegistrationSelectorComponent>;
    let component: CourseRegistrationSelectorComponent;
    let courseService: CourseManagementService;
    let mockRouter: any;
    let mockActivatedRoute: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseRegistrationSelectorComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
                { provide: Router, useValue: mockRouter },
                MockProvider(TranslateService),
            ],
        })
            .overrideTemplate(CourseRegistrationSelectorComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseRegistrationSelectorComponent);
                component = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);

                mockRouter = sinon.createStubInstance(Router);
                mockActivatedRoute = sinon.createStubInstance(ActivatedRoute);
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should initialize', fakeAsync(() => {
        fixture.detectChanges();
        expect(component).to.be.ok;
    }));

    it('should track course by Id', fakeAsync(() => {
        const indexNumber = 1;
        const course = new Course();
        course.id = 1;
        expect(component.trackCourseById(indexNumber, course)).to.equal(indexNumber);
    }));

    it('should cancel the registration', fakeAsync(() => {
        component.showCourseSelection = true;
        component.cancelRegistration();
        expect(component.showCourseSelection).to.be.false;
    }));

    it('should show registratable courses', fakeAsync(() => {
        const course1 = new Course();
        course1.id = 1;
        const course2 = new Course();
        course2.id = 2;
        const fake = sinon.fake.returns(of(new HttpResponse({ body: [course1] })));
        component.courses = [course2];
        sinon.replace(courseService, 'findAllToRegister', fake);

        component.startRegistration();
        tick();

        expect(component.coursesToSelect.length).to.equal(1);
    }));

    it('should wait until timeout', fakeAsync(() => {
        const course1 = new Course();
        course1.id = 1;
        const course2 = new Course();
        course2.id = 2;
        const fake = sinon.fake.returns(of(new HttpResponse({ body: [course1] })));
        component.courses = [course1, course2];
        sinon.replace(courseService, 'findAllToRegister', fake);

        component.startRegistration();
        tick(4000);

        expect(component.courseToRegister).to.be.undefined;
        expect(component.showCourseSelection).to.be.false;
    }));

    it('should  register for course', fakeAsync(() => {
        const course = new Course();
        course.id = 1;
        component.courseToRegister = course;

        const fake = sinon.fake.returns(of(new HttpResponse({ body: new User() })));
        sinon.replace(courseService, 'registerForCourse', fake);

        component.registerForCourse();
        tick();

        expect(component.addedSuccessful).to.be.true;
        flush();
    }));
});
