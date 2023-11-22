import { SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgForm } from '@angular/forms';
import { Course, CourseInformationSharingConfiguration } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ExerciseTitleChannelNameComponent } from 'app/exercises/shared/exercise-title-channel-name/exercise-title-channel-name.component';
import { TitleChannelNameModule } from 'app/shared/form/title-channel-name/title-channel-name.module';

describe('ExerciseTitleChannelNameComponent', () => {
    let component: ExerciseTitleChannelNameComponent;
    let fixture: ComponentFixture<ExerciseTitleChannelNameComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [ExerciseTitleChannelNameComponent],
            imports: [TitleChannelNameModule],
            providers: [NgForm],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(ExerciseTitleChannelNameComponent);
        component = fixture.componentInstance;
    });

    it('should hide channel name input if messaging and communication disabled', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.DISABLED;
        const textExercise = new TextExercise(course, undefined);
        textExercise.course = course;

        component.exercise = textExercise;
        component.course = textExercise.course;
        component.isExamMode = false;
        component.isImport = true;
        component.ngOnChanges({ course: new SimpleChange(undefined, course, true) });

        expect(component.hideChannelNameInput).toBeTrue();
    });

    it('should show channel name input if messaging disabled but communication enabled', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
        const textExercise = new TextExercise(course, undefined);
        textExercise.course = course;

        component.exercise = textExercise;
        component.course = textExercise.course;
        component.isExamMode = false;
        component.isImport = true;
        component.ngOnChanges({ course: new SimpleChange(undefined, course, true) });

        expect(component.hideChannelNameInput).toBeFalse();
    });

    it('should hide channel name input based on isExamMode and isImport if messaging is enabled', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;

        component.exercise = new TextExercise(course, undefined);
        component.course = course;

        // Simulate different scenarios

        // create new course exercise
        component.isExamMode = false;
        component.isImport = false;
        component.ngOnChanges({ isExamMode: new SimpleChange(undefined, false, true), isImport: new SimpleChange(undefined, false, true) });

        expect(component.hideChannelNameInput).toBeFalse();

        // create new exam exercise
        component.isExamMode = true;
        component.isImport = false;
        component.ngOnChanges({ isExamMode: new SimpleChange(undefined, true, false), isImport: new SimpleChange(undefined, false, false) });

        expect(component.hideChannelNameInput).toBeTrue();

        // In the following, we are not creating new exercises, so the exercise id must be set
        component.exercise.id = 1;

        // import course exercise
        component.isExamMode = false;
        component.isImport = true;
        component.ngOnChanges({ isExamMode: new SimpleChange(undefined, false, false), isImport: new SimpleChange(undefined, true, false) });

        expect(component.hideChannelNameInput).toBeFalse();

        // import exam exercise
        component.isExamMode = true;
        component.isImport = true;
        component.ngOnChanges({ isExamMode: new SimpleChange(undefined, true, false), isImport: new SimpleChange(undefined, true, false) });

        expect(component.hideChannelNameInput).toBeTrue();

        // edit exam exercise
        component.isExamMode = true;
        component.isImport = false;
        component.ngOnChanges({ isExamMode: new SimpleChange(undefined, true, false), isImport: new SimpleChange(undefined, true, false) });

        expect(component.hideChannelNameInput).toBeTrue();

        // edit course exercise without existing channel name
        component.isExamMode = false;
        component.isImport = false;
        component.ngOnChanges({ isExamMode: new SimpleChange(undefined, false, false), isImport: new SimpleChange(undefined, false, false) });

        expect(component.hideChannelNameInput).toBeTrue();

        // edit course exercise with existing channel name
        component.exercise.channelName = 'test';
        component.isExamMode = false;
        component.isImport = false;
        component.ngOnChanges({ isExamMode: new SimpleChange(undefined, true, false), isImport: new SimpleChange(undefined, false, false) });

        expect(component.hideChannelNameInput).toBeFalse();
    });

    it('should update exercise title and emit event on title change', () => {
        const newTitle = 'new-title';
        const onTitleChangeSpy = jest.spyOn(component.onTitleChange, 'emit');

        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        component.exercise = new TextExercise(course, undefined);
        component.updateTitle(newTitle);

        expect(component.exercise.title).toBe(newTitle);
        expect(onTitleChangeSpy).toHaveBeenCalledWith(newTitle);
    });

    it('should update exercise channel name and emit event on channel name change', () => {
        const newChannelName = 'new-channel-name';
        const onChannelNameChangeSpy = jest.spyOn(component.onChannelNameChange, 'emit');

        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        component.exercise = new TextExercise(course, undefined);
        component.updateChannelName(newChannelName);

        expect(component.exercise.channelName).toBe(newChannelName);
        expect(onChannelNameChangeSpy).toHaveBeenCalledWith(newChannelName);
    });
});
