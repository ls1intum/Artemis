import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgForm } from '@angular/forms';
import { Lecture } from 'app/entities/lecture.model';
import { LectureTitleChannelNameComponent } from 'app/lecture/lecture-title-channel-name.component';
import { TitleChannelNameModule } from 'app/shared/form/title-channel-name/title-channel-name.module';
import { Course, CourseInformationSharingConfiguration } from 'app/entities/course.model';

describe('LectureTitleChannelNameComponent', () => {
    let component: LectureTitleChannelNameComponent;
    let fixture: ComponentFixture<LectureTitleChannelNameComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [LectureTitleChannelNameComponent],
            imports: [TitleChannelNameModule],
            providers: [NgForm],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(LectureTitleChannelNameComponent);
        component = fixture.componentInstance;
    });

    it('should hide channel name input when messaging and communication is disabled', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.DISABLED;
        const lecture = new Lecture();
        lecture.course = course;

        component.lecture = lecture;
        component.ngOnInit();

        expect(component.hideChannelNameInput).toBeTrue();
    });

    it('should show channel name input when messaging is disabled but communication enabled', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
        const lecture = new Lecture();
        lecture.course = course;

        component.lecture = lecture;
        component.ngOnInit();

        expect(component.hideChannelNameInput).toBeFalse();
    });

    it('should not hide channel name input when lecture is created', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        const lecture = new Lecture();
        lecture.course = course;
        component.lecture = lecture;

        component.ngOnInit();

        expect(component.hideChannelNameInput).toBeFalse();
    });

    it('should not hide channel name input when lecture is being edited and has a channel name', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        const lecture = new Lecture();
        lecture.id = 123;
        lecture.channelName = 'sample-channel';
        lecture.course = course;

        component.lecture = lecture;
        component.ngOnInit();

        expect(component.hideChannelNameInput).toBeFalse();
    });

    it('should hide channel name input when lecture is being edited and has no channel name', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        const lecture = new Lecture();
        lecture.id = 123;
        lecture.course = course;

        component.lecture = lecture;
        component.ngOnInit();

        expect(component.hideChannelNameInput).toBeTrue();
    });
});
