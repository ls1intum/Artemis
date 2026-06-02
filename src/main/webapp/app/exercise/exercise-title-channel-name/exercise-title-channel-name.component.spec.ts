import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course, CourseInformationSharingConfiguration } from 'app/course/shared/entities/course.model';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ExerciseTitleChannelNameComponent } from 'app/exercise/exercise-title-channel-name/exercise-title-channel-name.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { of } from 'rxjs';
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
        const exercise = new TextExercise(new Course(), undefined);
        exercise.type = exerciseType;
        exercise.course!.id = courseId;
        fixture.componentRef.setInput('exercise', exercise);

        fixture.componentRef.setInput('courseId', courseId);
        const exerciseServiceSpy = vi.spyOn(exerciseService, 'getExistingExerciseDetailsInCourse').mockReturnValue(of({ exerciseTitles: new Set<string>() }));
        fixture.detectChanges();

        expect(exerciseServiceSpy).toHaveBeenCalledWith(courseId, exerciseType);
    });

    it('should hide channel name input if messaging and communication disabled', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.DISABLED;
        const textExercise = new TextExercise(course, undefined);
        textExercise.course = course;

        fixture.componentRef.setInput('exercise', textExercise);
        fixture.componentRef.setInput('course', textExercise.course);
        fixture.componentRef.setInput('isExamMode', false);
        fixture.componentRef.setInput('isImport', true);
        fixture.detectChanges();

        expect(component.hideChannelNameInput()).toBe(true);
    });

    it('should show channel name input if messaging disabled but communication enabled', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
        const textExercise = new TextExercise(course, undefined);
        textExercise.course = course;

        fixture.componentRef.setInput('exercise', textExercise);
        fixture.componentRef.setInput('course', textExercise.course);
        fixture.componentRef.setInput('isExamMode', false);
        fixture.componentRef.setInput('isImport', true);
        fixture.detectChanges();

        expect(component.hideChannelNameInput()).toBe(false);
    });

    it('should hide channel name input based on isExamMode and isImport if messaging is enabled', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;

        const exercise = new TextExercise(course, undefined);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('course', course);

        // Simulate different scenarios

        // create new course exercise
        fixture.componentRef.setInput('isExamMode', false);
        fixture.componentRef.setInput('isImport', false);
        fixture.detectChanges();

        expect(component.hideChannelNameInput()).toBe(false);

        // create new exam exercise
        fixture.componentRef.setInput('isExamMode', true);
        fixture.componentRef.setInput('isImport', false);
        fixture.detectChanges();

        expect(component.hideChannelNameInput()).toBe(true);

        // In the following, we are not creating new exercises, so the exercise id must be set
        component.exercise().id = 1;

        // import course exercise
        fixture.componentRef.setInput('isExamMode', false);
        fixture.componentRef.setInput('isImport', true);
        fixture.detectChanges();

        expect(component.hideChannelNameInput()).toBe(false);

        // import exam exercise
        fixture.componentRef.setInput('isExamMode', true);
        fixture.componentRef.setInput('isImport', true);
        fixture.detectChanges();

        expect(component.hideChannelNameInput()).toBe(true);

        // edit exam exercise
        fixture.componentRef.setInput('isExamMode', true);
        fixture.componentRef.setInput('isImport', false);
        fixture.detectChanges();

        expect(component.hideChannelNameInput()).toBe(true);

        // edit course exercise without existing channel name
        fixture.componentRef.setInput('isExamMode', false);
        fixture.componentRef.setInput('isImport', false);
        fixture.detectChanges();

        expect(component.hideChannelNameInput()).toBe(true);

        // edit course exercise with existing channel name
        const exerciseWithExistingChannelName = new TextExercise(course, undefined);
        Object.assign(exerciseWithExistingChannelName, component.exercise(), { channelName: 'test' });
        fixture.componentRef.setInput('exercise', exerciseWithExistingChannelName);
        fixture.componentRef.setInput('isExamMode', false);
        fixture.componentRef.setInput('isImport', false);
        fixture.detectChanges();

        expect(component.hideChannelNameInput()).toBe(false);
    });

    it('should update exercise title and emit event on title change', () => {
        const newTitle = 'new-title';
        const onTitleChangeSpy = vi.spyOn(component.onTitleChange, 'emit');

        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        fixture.componentRef.setInput('exercise', new TextExercise(course, undefined));
        component.updateTitle(newTitle);

        expect(component.exercise().title).toBe(newTitle);
        expect(onTitleChangeSpy).toHaveBeenCalledWith(newTitle);
    });

    it('should update exercise channel name and emit event on channel name change', () => {
        const newChannelName = 'new-channel-name';
        const onChannelNameChangeSpy = vi.spyOn(component.onChannelNameChange, 'emit');

        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        fixture.componentRef.setInput('exercise', new TextExercise(course, undefined));
        component.updateChannelName(newChannelName);

        expect(component.exercise().channelName).toBe(newChannelName);
        expect(onChannelNameChangeSpy).toHaveBeenCalledWith(newChannelName);
    });
});
