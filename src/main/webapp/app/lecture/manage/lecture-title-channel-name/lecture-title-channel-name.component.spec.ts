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

        expect(component.hideChannelNameInput()).toBe(true);
    });

    it('should show channel name input when messaging is disabled but communication enabled', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
        const lecture = new Lecture();
        lecture.course = course;

        fixture.componentRef.setInput('lecture', lecture);

        expect(component.hideChannelNameInput()).toBe(false);
    });

    it('should not hide channel name input when lecture is created', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        const lecture = new Lecture();
        lecture.course = course;
        fixture.componentRef.setInput('lecture', lecture);

        expect(component.hideChannelNameInput()).toBe(false);
    });

    it('should not hide channel name input when lecture is being edited and has a channel name', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        const lecture = new Lecture();
        lecture.id = 123;
        lecture.channelName = 'sample-channel';
        lecture.course = course;

        fixture.componentRef.setInput('lecture', lecture);

        expect(component.hideChannelNameInput()).toBe(false);
    });

    it('should hide channel name input when lecture is being edited and has no channel name', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        const lecture = new Lecture();
        lecture.id = 123;
        lecture.course = course;

        fixture.componentRef.setInput('lecture', lecture);

        expect(component.hideChannelNameInput()).toBe(true);
    });

    it('should emit lectureChange with updated title when onTitleChange is called', () => {
        const lecture = new Lecture();
        lecture.title = 'Original Title';
        lecture.channelName = 'original-channel';
        fixture.componentRef.setInput('lecture', lecture);

        const lectureChangeSpy = vi.fn();
        component.lectureChange.subscribe(lectureChangeSpy);

        component.onTitleChange('New Title');

        expect(lectureChangeSpy).toHaveBeenCalledWith(expect.objectContaining({ title: 'New Title', channelName: 'original-channel' }));
    });

    it('should emit lectureChange with updated channelName when onChannelNameChange is called', () => {
        const lecture = new Lecture();
        lecture.title = 'Original Title';
        lecture.channelName = 'original-channel';
        fixture.componentRef.setInput('lecture', lecture);

        const lectureChangeSpy = vi.fn();
        component.lectureChange.subscribe(lectureChangeSpy);

        component.onChannelNameChange('new-channel');

        expect(lectureChangeSpy).toHaveBeenCalledWith(expect.objectContaining({ title: 'Original Title', channelName: 'new-channel' }));
    });

    it('should preserve title when titleChange and channelNameChange are called in sequence (race condition fix)', () => {
        const course = new Course();
        course.id = 1;
        const lecture = new Lecture();
        lecture.course = course;
        fixture.componentRef.setInput('lecture', lecture);

        const lectureChangeSpy = vi.fn();
        component.lectureChange.subscribe(lectureChangeSpy);

        // Simulate the race condition: titleChange fires, then channelNameChange fires immediately
        // (this happens because updateTitle() in TitleChannelNameComponent calls updateChannelName())
        // Without the fix, the second call would clone the OLD lecture (without title) and overwrite
        component.onTitleChange('Test Lecture');
        component.onChannelNameChange('lecture-test');

        // Verify both emissions contain the correct data
        expect(lectureChangeSpy).toHaveBeenCalledTimes(2);

        // First emission should have the title
        expect(lectureChangeSpy).toHaveBeenNthCalledWith(
            1,
            expect.objectContaining({
                title: 'Test Lecture',
                course: expect.objectContaining({ id: 1 }),
            }),
        );

        // Second emission should have BOTH title AND channelName (the fix)
        expect(lectureChangeSpy).toHaveBeenNthCalledWith(
            2,
            expect.objectContaining({
                title: 'Test Lecture',
                channelName: 'lecture-test',
                course: expect.objectContaining({ id: 1 }),
            }),
        );
    });

    it('should include course data in emitted lecture when title changes', () => {
        const course = new Course();
        course.id = 42;
        course.title = 'Test Course';
        const lecture = new Lecture();
        lecture.course = course;
        fixture.componentRef.setInput('lecture', lecture);

        const lectureChangeSpy = vi.fn();
        component.lectureChange.subscribe(lectureChangeSpy);

        component.onTitleChange('New Lecture Title');

        expect(lectureChangeSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                title: 'New Lecture Title',
                course: expect.objectContaining({ id: 42, title: 'Test Course' }),
            }),
        );
    });
});
