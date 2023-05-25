import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { RouterTestingModule } from '@angular/router/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Course } from 'app/entities/course.model';
import { Lecture } from 'app/entities/lecture.model';
import { CourseLectureRowComponent } from 'app/overview/course-lectures/course-lecture-row.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { Location } from '@angular/common';
import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

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
                MockDirective(NgbTooltip),
            ],
            declarations: [
                DummyComponent,
                CourseLectureRowComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(FaIconComponent),
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

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        courseLectureRowComponentFixture.detectChanges();
        expect(courseLectureRowComponent).not.toBeNull();
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
        expect(dateContainer).not.toBeNull();
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
        expect(dateContainer).toBeNull();
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

        expect(location.path()).toBe('/courses/1/lectures/1');
    }));
});
