import { expect, vi } from 'vitest';
import { SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Course, CourseInformationSharingConfiguration } from 'app/core/course/shared/entities/course.model';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ExerciseTitleChannelNameComponent } from 'app/exercise/exercise-title-channel-name/exercise-title-channel-name.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ExerciseTitleChannelNameComponent', () => {
    setupTestBed({ zoneless: true });
    let component: ExerciseTitleChannelNameComponent;
    let fixture: ComponentFixture<ExerciseTitleChannelNameComponent>;
    let exerciseService: ExerciseService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseTitleChannelNameComponent],
            providers: [
                SessionStorageService,
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
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
        const exerciseServiceSpy = vi.spyOn(exerciseService, 'getExistingExerciseDetailsInCourse');
        fixture.detectChanges();

        expect(exerciseServiceSpy).toHaveBeenCalledOnce();
        expect(exerciseServiceSpy).toHaveBeenCalledWith(courseId, exerciseType);
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

        expect(component.hideChannelNameInput).toBe(true);
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

        expect(component.hideChannelNameInput).toBe(false);
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

        expect(component.hideChannelNameInput).toBe(false);

        // create new exam exercise
        component.isExamMode = true;
        component.isImport = false;
        component.ngOnChanges({
            isExamMode: new SimpleChange(undefined, true, false),
            isImport: new SimpleChange(undefined, false, false),
        });

        expect(component.hideChannelNameInput).toBe(true);

        // In the following, we are not creating new exercises, so the exercise id must be set
        component.exercise.id = 1;

        // import course exercise
        component.isExamMode = false;
        component.isImport = true;
        component.ngOnChanges({
            isExamMode: new SimpleChange(undefined, false, false),
            isImport: new SimpleChange(undefined, true, false),
        });

        expect(component.hideChannelNameInput).toBe(false);

        // import exam exercise
        component.isExamMode = true;
        component.isImport = true;
        component.ngOnChanges({
            isExamMode: new SimpleChange(undefined, true, false),
            isImport: new SimpleChange(undefined, true, false),
        });

        expect(component.hideChannelNameInput).toBe(true);

        // edit exam exercise
        component.isExamMode = true;
        component.isImport = false;
        component.ngOnChanges({
            isExamMode: new SimpleChange(undefined, true, false),
            isImport: new SimpleChange(undefined, true, false),
        });

        expect(component.hideChannelNameInput).toBe(true);

        // edit course exercise without existing channel name
        component.isExamMode = false;
        component.isImport = false;
        component.ngOnChanges({
            isExamMode: new SimpleChange(undefined, false, false),
            isImport: new SimpleChange(undefined, false, false),
        });

        expect(component.hideChannelNameInput).toBe(true);

        // edit course exercise with existing channel name
        component.exercise.channelName = 'test';
        component.isExamMode = false;
        component.isImport = false;
        component.ngOnChanges({
            isExamMode: new SimpleChange(undefined, true, false),
            isImport: new SimpleChange(undefined, false, false),
        });

        expect(component.hideChannelNameInput).toBe(false);
    });

    it('should update exercise title and emit event on title change', () => {
        const newTitle = 'new-title';
        const onTitleChangeSpy = vi.spyOn(component.onTitleChange, 'emit');

        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        component.exercise = new TextExercise(course, undefined);
        component.updateTitle(newTitle);

        expect(component.exercise.title).toBe(newTitle);
        expect(onTitleChangeSpy).toHaveBeenCalledWith(newTitle);
    });

    it('should update exercise channel name and emit event on channel name change', () => {
        const newChannelName = 'new-channel-name';
        const onChannelNameChangeSpy = vi.spyOn(component.onChannelNameChange, 'emit');

        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        component.exercise = new TextExercise(course, undefined);
        component.updateChannelName(newChannelName);

        expect(component.exercise.channelName).toBe(newChannelName);
        expect(onChannelNameChangeSpy).toHaveBeenCalledWith(newChannelName);
    });
});
