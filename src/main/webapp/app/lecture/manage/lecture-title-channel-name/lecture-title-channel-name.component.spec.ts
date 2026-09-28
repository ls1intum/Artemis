import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureTitleChannelNameComponent, formatLectureChannelName } from 'app/lecture/manage/lecture-title-channel-name/lecture-title-channel-name.component';
import { Course, CourseInformationSharingConfiguration } from 'app/course/shared/entities/course.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('LectureTitleChannelNameComponent', () => {
    let component: LectureTitleChannelNameComponent;
    let fixture: ComponentFixture<LectureTitleChannelNameComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LectureTitleChannelNameComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(LectureTitleChannelNameComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function lectureInCourse(configuration: CourseInformationSharingConfiguration, id?: number, channelName?: string): Lecture {
        const course = new Course();
        course.courseInformationSharingConfiguration = configuration;
        const lecture = new Lecture();
        lecture.id = id;
        lecture.channelName = channelName;
        lecture.course = course;
        return lecture;
    }

    function input(id: string): HTMLInputElement | null {
        return fixture.nativeElement.querySelector(`#${id}`);
    }

    function type(field: HTMLInputElement, value: string): void {
        field.value = value;
        field.dispatchEvent(new Event('input'));
        fixture.detectChanges();
    }

    describe('channel name field', () => {
        it('should be hidden when messaging and communication are disabled', () => {
            fixture.componentRef.setInput('lecture', lectureInCourse(CourseInformationSharingConfiguration.DISABLED));
            fixture.detectChanges();

            expect(component.hideChannelNameInput()).toBe(true);
            expect(input('field_channel_name')).toBeNull();
        });

        it('should be shown when only communication is enabled', () => {
            fixture.componentRef.setInput('lecture', lectureInCourse(CourseInformationSharingConfiguration.COMMUNICATION_ONLY));

            expect(component.hideChannelNameInput()).toBe(false);
        });

        it('should be shown when a lecture is created', () => {
            fixture.componentRef.setInput('lecture', lectureInCourse(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING));
            fixture.detectChanges();

            expect(component.hideChannelNameInput()).toBe(false);
            expect(input('field_channel_name')).not.toBeNull();
        });

        it('should be shown when an edited lecture has a channel', () => {
            fixture.componentRef.setInput('lecture', lectureInCourse(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING, 123, 'sample-channel'));

            expect(component.hideChannelNameInput()).toBe(false);
        });

        it('should be hidden when an edited lecture has no channel', () => {
            fixture.componentRef.setInput('lecture', lectureInCourse(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING, 123));

            expect(component.hideChannelNameInput()).toBe(true);
        });
    });

    it('should show the channel name derived from the title of a new lecture without reporting a change', () => {
        const lectureChangeSpy = vi.fn();
        component.lectureChange.subscribe(lectureChangeSpy);
        fixture.componentRef.setInput('lecture', lectureInCourse(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING));
        fixture.detectChanges();

        expect(input('field_channel_name')?.value).toBe('lecture-');
        expect(lectureChangeSpy).not.toHaveBeenCalled();
    });

    it('should keep the channel name of an existing lecture', () => {
        const lecture = lectureInCourse(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING, 123, 'lecture-old-name');
        lecture.title = 'Renamed on the server';
        fixture.componentRef.setInput('lecture', lecture);
        fixture.detectChanges();

        expect(input('field_title')?.value).toBe('Renamed on the server');
        expect(input('field_channel_name')?.value).toBe('lecture-old-name');
    });

    it('should emit the title together with the channel name that follows it', () => {
        const lecture = lectureInCourse(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        lecture.course!.id = 42;
        fixture.componentRef.setInput('lecture', lecture);
        fixture.detectChanges();
        const lectureChangeSpy = vi.fn();
        component.lectureChange.subscribe(lectureChangeSpy);

        type(input('field_title')!, 'Software Engineering: Intro!');

        expect(lectureChangeSpy).toHaveBeenCalledOnce();
        expect(lectureChangeSpy).toHaveBeenCalledWith(
            expect.objectContaining({ title: 'Software Engineering: Intro!', channelName: 'lecture-software-engineering-i', course: expect.objectContaining({ id: 42 }) }),
        );
        expect(input('field_channel_name')?.value).toBe('lecture-software-engineering-i');
    });

    it('should format a typed channel name and write it back into the field', () => {
        const lecture = lectureInCourse(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING, 123, 'lecture-intro');
        lecture.title = 'Intro';
        fixture.componentRef.setInput('lecture', lecture);
        fixture.detectChanges();
        const lectureChangeSpy = vi.fn();
        component.lectureChange.subscribe(lectureChangeSpy);
        const channelField = input('field_channel_name')!;

        type(channelField, 'My Channel');

        expect(channelField.value).toBe('my-channel');
        expect(lectureChangeSpy).toHaveBeenCalledWith(expect.objectContaining({ title: 'Intro', channelName: 'my-channel' }));
    });

    it('should report a lecture without a title as invalid and show the error once the field was left', () => {
        fixture.componentRef.setInput('lecture', lectureInCourse(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING));
        fixture.detectChanges();
        const titleField = input('field_title')!;

        expect(component.isValid()).toBe(false);
        expect(titleField.getAttribute('aria-invalid')).toBeNull();

        titleField.dispatchEvent(new Event('blur'));
        fixture.detectChanges();
        expect(titleField.getAttribute('aria-invalid')).toBe('true');

        type(titleField, 'Intro');
        expect(component.isValid()).toBe(true);
        expect(titleField.getAttribute('aria-invalid')).toBeNull();
    });

    it('should report an empty channel name as invalid only while the field is shown', () => {
        const lecture = lectureInCourse(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING, 123, 'lecture-intro');
        lecture.title = 'Intro';
        fixture.componentRef.setInput('lecture', lecture);
        fixture.detectChanges();

        type(input('field_channel_name')!, '');
        expect(component.isValid()).toBe(false);

        const lectureWithoutChannel = lectureInCourse(CourseInformationSharingConfiguration.DISABLED, 123);
        lectureWithoutChannel.title = 'Intro';
        fixture.componentRef.setInput('lecture', lectureWithoutChannel);
        expect(component.isValid()).toBe(true);
    });

    describe('formatLectureChannelName', () => {
        it.each([
            ['lecture-Intro to Git', false, true, 'lecture-intro-to-git'],
            ['lecture-What?', false, true, 'lecture-what'],
            ['lecture-', false, false, 'lecture-'],
            ['My  Channel', true, false, 'my-channel'],
            ['a--b', true, false, 'a--b'],
            ['lecture-a-very-long-lecture-title-that-goes-on', false, true, 'lecture-a-very-long-lecture-ti'],
        ])('should format %s', (name, allowDuplicateHyphens, removeTrailingHyphen, expected) => {
            expect(formatLectureChannelName(name, allowDuplicateHyphens, removeTrailingHyphen)).toBe(expected);
        });
    });
});
