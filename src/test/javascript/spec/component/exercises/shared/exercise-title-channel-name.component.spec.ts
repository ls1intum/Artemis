import { SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course, CourseInformationSharingConfiguration } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text/text-exercise.model';
import { ExerciseTitleChannelNameComponent } from 'app/exercises/shared/exercise-title-channel-name/exercise-title-channel-name.component';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { SessionStorageService } from 'ngx-webstorage';
import { ExerciseService } from '../../../../../../main/webapp/app/exercises/shared/exercise/exercise.service';
import { ExerciseType } from '../../../../../../main/webapp/app/entities/exercise.model';
import { ArtemisTestModule } from '../../../test.module';

describe('ExerciseTitleChannelNameComponent', () => {
    let component: ExerciseTitleChannelNameComponent;
    let fixture: ComponentFixture<ExerciseTitleChannelNameComponent>;
    let exerciseService: ExerciseService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [{ provide: SessionStorageService, useClass: MockSyncStorage }],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(ExerciseTitleChannelNameComponent);

        fixture.componentRef.setInput('course', new Course());
        fixture.componentRef.setInput('isEditFieldDisplayedRecord', true);
        component = fixture.componentInstance;

        exerciseService = TestBed.inject(ExerciseService);
    });

    it('should call getExistingExerciseDetailsInCourse on init', () => {
        const courseId = 123;
        const exerciseType = ExerciseType.PROGRAMMING;
        component.exercise = new TextExercise(new Course(), undefined);
        component.exercise.type = exerciseType;
        component.exercise.course!.id = courseId;

        fixture.componentRef.setInput('courseId', courseId);
        const exerciseServiceSpy = jest.spyOn(exerciseService, 'getExistingExerciseDetailsInCourse');
        fixture.detectChanges();

        expect(exerciseServiceSpy).toHaveBeenCalledExactlyOnceWith(courseId, exerciseType);
    });

    it('should hide channel name input if messaging and communication disabled', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.DISABLED;
        const textExercise = new TextExercise(course, undefined);
        textExercise.course = course;

        component.exercise = textExercise;
        fixture.componentRef.setInput('course', textExercise.course);
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
        fixture.componentRef.setInput('course', textExercise.course);
        component.isExamMode = false;
        component.isImport = true;
        component.ngOnChanges({ course: new SimpleChange(undefined, course, true) });

        expect(component.hideChannelNameInput).toBeFalse();
    });

    it('should hide channel name input based on isExamMode and isImport if messaging is enabled', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;

        component.exercise = new TextExercise(course, undefined);
        fixture.componentRef.setInput('course', course);

        // Simulate different scenarios

        // create new course exercise
        component.isExamMode = false;
        component.isImport = false;
        component.ngOnChanges({
            isExamMode: new SimpleChange(undefined, false, true),
            isImport: new SimpleChange(undefined, false, true),
        });

        expect(component.hideChannelNameInput).toBeFalse();

        // create new exam exercise
        component.isExamMode = true;
        component.isImport = false;
        component.ngOnChanges({
            isExamMode: new SimpleChange(undefined, true, false),
            isImport: new SimpleChange(undefined, false, false),
        });

        expect(component.hideChannelNameInput).toBeTrue();

        // In the following, we are not creating new exercises, so the exercise id must be set
        component.exercise.id = 1;

        // import course exercise
        component.isExamMode = false;
        component.isImport = true;
        component.ngOnChanges({
            isExamMode: new SimpleChange(undefined, false, false),
            isImport: new SimpleChange(undefined, true, false),
        });

        expect(component.hideChannelNameInput).toBeFalse();

        // import exam exercise
        component.isExamMode = true;
        component.isImport = true;
        component.ngOnChanges({
            isExamMode: new SimpleChange(undefined, true, false),
            isImport: new SimpleChange(undefined, true, false),
        });

        expect(component.hideChannelNameInput).toBeTrue();

        // edit exam exercise
        component.isExamMode = true;
        component.isImport = false;
        component.ngOnChanges({
            isExamMode: new SimpleChange(undefined, true, false),
            isImport: new SimpleChange(undefined, true, false),
        });

        expect(component.hideChannelNameInput).toBeTrue();

        // edit course exercise without existing channel name
        component.isExamMode = false;
        component.isImport = false;
        component.ngOnChanges({
            isExamMode: new SimpleChange(undefined, false, false),
            isImport: new SimpleChange(undefined, false, false),
        });

        expect(component.hideChannelNameInput).toBeTrue();

        // edit course exercise with existing channel name
        component.exercise.channelName = 'test';
        component.isExamMode = false;
        component.isImport = false;
        component.ngOnChanges({
            isExamMode: new SimpleChange(undefined, true, false),
            isImport: new SimpleChange(undefined, false, false),
        });

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
