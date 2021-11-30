import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, Router, UrlSegment } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import { Lecture } from 'app/entities/lecture.model';
import { CourseLectureRowComponent } from 'app/overview/course-lectures/course-lecture-row.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as chai from 'chai';
import dayjs from 'dayjs';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import * as sinon from 'sinon';
import sinonChai from 'sinon-chai';
import { Location } from '@angular/common';
import { CourseLectureDetailsComponent } from 'app/overview/course-lectures/course-lecture-details.component';
import { CourseLecturesComponent } from 'app/overview/course-lectures/course-lectures.component';
import { Component } from '@angular/core';

chai.use(sinonChai);
const expect = chai.expect;

@Component({
    template: '',
})
class DummyComponent {}

describe('CourseLectureRow', () => {
    let courseLectureRowComponentFixture: ComponentFixture<CourseLectureRowComponent>;
    let courseLectureRowComponent: CourseLectureRowComponent;
    let location: Location;
    let router: Router;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                RouterTestingModule.withRoutes([
                    { path: 'courses/:courseId/lectures', component: DummyComponent },
                    { path: 'courses/:courseId/lectures/:lectureId', component: DummyComponent },
                ]),
            ],
            declarations: [
                DummyComponent,
                CourseLectureRowComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(FaIconComponent),
                MockDirective(NgbTooltip),
                MockPipe(ArtemisTimeAgoPipe),
            ],
            providers: [],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                courseLectureRowComponentFixture = TestBed.createComponent(CourseLectureRowComponent);
                location = TestBed.inject(Location);
                router = TestBed.inject(Router);
                router.navigate(['courses', 1, 'lectures']);
                courseLectureRowComponent = courseLectureRowComponentFixture.componentInstance;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        courseLectureRowComponentFixture.detectChanges();
        expect(courseLectureRowComponent).to.be.ok;
    });

    it('should set urgent class to date if remaining days is less than 7 days', () => {
        const lecture = new Lecture();
        lecture.id = 1;
        lecture.title = 'exampleLecture';
        lecture.startDate = dayjs().add(1, 'd');
        const course = new Course();
        course.id = 1;

        courseLectureRowComponent.lecture = lecture;
        courseLectureRowComponent.course = course;
        courseLectureRowComponentFixture.detectChanges();

        const dateContainer = courseLectureRowComponentFixture.debugElement.query(By.css('.text-danger'));
        expect(dateContainer).to.be.ok;
    });

    it('should not urgent class to date if remaining days is more than 7 days', () => {
        const lecture = new Lecture();
        lecture.id = 1;
        lecture.title = 'exampleLecture';
        lecture.startDate = dayjs().add(9, 'd');
        const course = new Course();
        course.id = 1;

        courseLectureRowComponent.lecture = lecture;
        courseLectureRowComponent.course = course;
        courseLectureRowComponentFixture.detectChanges();

        const dateContainer = courseLectureRowComponentFixture.debugElement.query(By.css('.text-danger'));
        expect(dateContainer).to.not.exist;
    });

    it('navigate to details page if row is clicked', fakeAsync(() => {
        const lecture = new Lecture();
        lecture.id = 1;
        lecture.title = 'exampleLecture';
        const course = new Course();
        course.id = 1;

        courseLectureRowComponent.lecture = lecture;
        courseLectureRowComponent.course = course;

        courseLectureRowComponentFixture.detectChanges();

        const link = courseLectureRowComponentFixture.debugElement.nativeElement.querySelector('.stretched-link');
        link.click();
        tick();

        expect(location.path()).to.deep.equal('/courses/1/lectures/1');
    }));
});
