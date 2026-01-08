import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { CourseLectureRowComponent } from 'app/lecture/overview/course-lectures/lecture-row/course-lecture-row.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockDirective, MockPipe } from 'ng-mocks';
import { Location } from '@angular/common';
import { Component } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

@Component({
    template: '',
    standalone: true,
})
class DummyComponent {}

describe('CourseLectureRow', () => {
    setupTestBed({ zoneless: true });

    let courseLectureRowComponentFixture: ComponentFixture<CourseLectureRowComponent>;
    let courseLectureRowComponent: CourseLectureRowComponent;
    let location: Location;
    let router: Router;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                MockDirective(NgbTooltip),
                RouterModule.forRoot([
                    { path: 'courses/:courseId/lectures', component: DummyComponent },
                    { path: 'courses/:courseId/lectures/:lectureId', component: DummyComponent },
                ]),
                FaIconComponent,
                CourseLectureRowComponent,
                DummyComponent,
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(CourseLectureRowComponent, {
                remove: { imports: [ArtemisTranslatePipe, ArtemisDatePipe, ArtemisTimeAgoPipe] },
                add: { imports: [MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockPipe(ArtemisTimeAgoPipe)] },
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
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        const lecture = new Lecture();
        lecture.id = 1;
        lecture.title = 'exampleLecture';
        const course = new Course();
        course.id = 1;

        courseLectureRowComponentFixture.componentRef.setInput('lecture', lecture);
        courseLectureRowComponentFixture.componentRef.setInput('course', course);
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

        courseLectureRowComponentFixture.componentRef.setInput('lecture', lecture);
        courseLectureRowComponentFixture.componentRef.setInput('course', course);
        courseLectureRowComponentFixture.changeDetectorRef.detectChanges();

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

        courseLectureRowComponentFixture.componentRef.setInput('lecture', lecture);
        courseLectureRowComponentFixture.componentRef.setInput('course', course);
        courseLectureRowComponentFixture.changeDetectorRef.detectChanges();

        const dateContainer = courseLectureRowComponentFixture.debugElement.query(By.css('.text-danger'));
        expect(dateContainer).toBeNull();
    });

    it('navigate to details page if row is clicked', async () => {
        const lecture = new Lecture();
        lecture.id = 1;
        lecture.title = 'exampleLecture';
        const course = new Course();
        course.id = 1;

        courseLectureRowComponentFixture.componentRef.setInput('lecture', lecture);
        courseLectureRowComponentFixture.componentRef.setInput('course', course);

        courseLectureRowComponentFixture.detectChanges();

        const link = courseLectureRowComponentFixture.debugElement.nativeElement.querySelector('.stretched-link');
        link.click();
        await courseLectureRowComponentFixture.whenStable();

        expect(location.path()).toBe('/courses/1/lectures/1');
    });
});
