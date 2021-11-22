import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { CourseRegistrationComponent } from 'app/overview/course-registration/course-registration.component';
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
import { MockRouter } from '../../helpers/mocks/mock-router';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseRegistrationComponent', () => {
    let fixture: ComponentFixture<CourseRegistrationComponent>;
    let component: CourseRegistrationComponent;
    let courseService: CourseManagementService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseRegistrationComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: Router, useClass: MockRouter },
                MockProvider(TranslateService),
            ],
        })
            .overrideTemplate(CourseRegistrationComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseRegistrationComponent);
                component = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);
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

    it('should show registratable courses', fakeAsync(() => {
        const course1 = new Course();
        course1.id = 1;
        const course2 = new Course();
        course2.id = 2;
        const fake = sinon.fake.returns(of(new HttpResponse({ body: [course1] })));
        component.courses = [course2];
        sinon.replace(courseService, 'findAllToRegister', fake);

        component.loadAndFilterCourses();
        tick();

        expect(component.coursesToSelect.length).to.equal(1);
    }));

    it('should  register for course', fakeAsync(() => {
        const course = new Course();
        course.id = 1;

        const fake = sinon.fake.returns(of(new HttpResponse({ body: new User() })));
        sinon.replace(courseService, 'registerForCourse', fake);

        component.registerForCourse(1);
        tick();

        expect(component.addedSuccessful).to.be.true;
        flush();
    }));
});
