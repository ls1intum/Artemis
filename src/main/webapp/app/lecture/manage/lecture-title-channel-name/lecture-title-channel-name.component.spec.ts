import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureTitleChannelNameComponent } from 'app/lecture/manage/lecture-title-channel-name/lecture-title-channel-name.component';
import { Course, CourseInformationSharingConfiguration } from 'app/core/course/shared/entities/course.model';

describe('LectureTitleChannelNameComponent', () => {
    setupTestBed({ zoneless: true });

    let component: LectureTitleChannelNameComponent;
    let fixture: ComponentFixture<LectureTitleChannelNameComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LectureTitleChannelNameComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(LectureTitleChannelNameComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should hide channel name input when messaging and communication is disabled', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.DISABLED;
        const lecture = new Lecture();
        lecture.course = course;

        fixture.componentRef.setInput('lecture', lecture);
        component.ngOnInit();

        expect(component.hideChannelNameInput).toBe(true);
    });

    it('should show channel name input when messaging is disabled but communication enabled', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
        const lecture = new Lecture();
        lecture.course = course;

        fixture.componentRef.setInput('lecture', lecture);
        component.ngOnInit();

        expect(component.hideChannelNameInput).toBe(false);
    });

    it('should not hide channel name input when lecture is created', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        const lecture = new Lecture();
        lecture.course = course;
        fixture.componentRef.setInput('lecture', lecture);

        component.ngOnInit();

        expect(component.hideChannelNameInput).toBe(false);
    });

    it('should not hide channel name input when lecture is being edited and has a channel name', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        const lecture = new Lecture();
        lecture.id = 123;
        lecture.channelName = 'sample-channel';
        lecture.course = course;

        fixture.componentRef.setInput('lecture', lecture);
        component.ngOnInit();

        expect(component.hideChannelNameInput).toBe(false);
    });

    it('should hide channel name input when lecture is being edited and has no channel name', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        const lecture = new Lecture();
        lecture.id = 123;
        lecture.course = course;

        fixture.componentRef.setInput('lecture', lecture);
        component.ngOnInit();

        expect(component.hideChannelNameInput).toBe(true);
    });
});
