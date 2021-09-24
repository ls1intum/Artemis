import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
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

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseLectureRow', () => {
    let courseLectureRowComponentFixture: ComponentFixture<CourseLectureRowComponent>;
    let courseLectureRowComponent: CourseLectureRowComponent;
    let mockRouter: any;
    let mockActivatedRoute: any;

    beforeEach(() => {
        mockRouter = sinon.createStubInstance(Router);
        mockActivatedRoute = sinon.createStubInstance(ActivatedRoute);

        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                CourseLectureRowComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(FaIconComponent),
                MockDirective(NgbTooltip),
                MockPipe(ArtemisTimeAgoPipe),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
                { provide: Router, useValue: mockRouter },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                courseLectureRowComponentFixture = TestBed.createComponent(CourseLectureRowComponent);
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

    it('navigate to details page if row is clicked and extendedLink is activated', () => {
        const lecture = new Lecture();
        lecture.id = 1;
        lecture.title = 'exampleLecture';
        const course = new Course();
        course.id = 1;

        courseLectureRowComponent.lecture = lecture;
        courseLectureRowComponent.course = course;
        courseLectureRowComponent.extendedLink = true;

        courseLectureRowComponentFixture.detectChanges();

        const icon = courseLectureRowComponentFixture.debugElement.nativeElement.querySelector('.exercise-row-icon');
        icon.click();

        expect(mockRouter.navigate).to.have.been.calledOnce;
        const navigationArray = mockRouter.navigate.getCall(0).args[0];
        expect(navigationArray).to.deep.equal(['courses', course.id, 'lectures', lecture.id]);
    });

    it('navigate to details page if row is clicked and extendedLink is deactivated', () => {
        const lecture = new Lecture();
        lecture.id = 1;
        lecture.title = 'exampleLecture';
        const course = new Course();
        course.id = 1;

        courseLectureRowComponent.lecture = lecture;
        courseLectureRowComponent.course = course;
        courseLectureRowComponent.extendedLink = false;

        courseLectureRowComponentFixture.detectChanges();

        const icon = courseLectureRowComponentFixture.debugElement.nativeElement.querySelector('.exercise-row-icon');
        icon.click();

        expect(mockRouter.navigate).to.have.been.calledOnce;
        const navigationArray = mockRouter.navigate.getCall(0).args[0];
        expect(navigationArray).to.deep.equal([lecture.id]);
    });
});
