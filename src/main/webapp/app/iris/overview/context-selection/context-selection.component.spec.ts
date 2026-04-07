import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockPipe } from 'ng-mocks';
import { BehaviorSubject } from 'rxjs';
import { ContextSelectionComponent } from './context-selection.component';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ContextSelectionComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ContextSelectionComponent;
    let fixture: ComponentFixture<ContextSelectionComponent>;

    let chatServiceMock: {
        getCourseId: ReturnType<typeof vi.fn>;
        currentChatMode: ReturnType<typeof vi.fn>;
        currentRelatedEntityId: ReturnType<typeof vi.fn>;
        switchToNewSession: ReturnType<typeof vi.fn>;
    };
    let courseStorageServiceMock: { getCourse: ReturnType<typeof vi.fn> };

    let chatModeSubject: BehaviorSubject<ChatServiceMode | undefined>;
    let entityIdSubject: BehaviorSubject<number | undefined>;

    const courseId = 42;

    function buildCachedCourse(overrides = {}) {
        return {
            id: courseId,
            title: 'Test Course',
            lectures: [
                { id: 1, title: 'Lecture 1' },
                { id: 2, title: 'Lecture 2' },
            ],
            exercises: [
                { id: 10, title: 'Programming Ex', type: ExerciseType.PROGRAMMING },
                { id: 11, title: 'Text Ex', type: ExerciseType.TEXT },
                { id: 12, title: 'File Upload Ex', type: ExerciseType.FILE_UPLOAD },
            ],
            ...overrides,
        };
    }

    beforeEach(async () => {
        chatModeSubject = new BehaviorSubject<ChatServiceMode | undefined>(ChatServiceMode.COURSE);
        entityIdSubject = new BehaviorSubject<number | undefined>(courseId);

        chatServiceMock = {
            getCourseId: vi.fn().mockReturnValue(courseId),
            currentChatMode: vi.fn().mockReturnValue(chatModeSubject.asObservable()),
            currentRelatedEntityId: vi.fn().mockReturnValue(entityIdSubject.asObservable()),
            switchToNewSession: vi.fn(),
        };

        courseStorageServiceMock = {
            getCourse: vi.fn().mockReturnValue(buildCachedCourse()),
        };

        await TestBed.configureTestingModule({
            imports: [ContextSelectionComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [
                { provide: IrisChatService, useValue: chatServiceMock },
                { provide: CourseStorageService, useValue: courseStorageServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ContextSelectionComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('data loading', () => {
        it('should read course data from cache', () => {
            expect(courseStorageServiceMock.getCourse).toHaveBeenCalledWith(courseId);
            expect(component.courseName()).toBe('Test Course');
            expect(component.lectures()).toHaveLength(2);
            expect(component.exercises()).toHaveLength(3);
        });

        it('should return empty data when cache has no course', () => {
            courseStorageServiceMock.getCourse.mockReturnValue(undefined);
            fixture = TestBed.createComponent(ContextSelectionComponent);
            component = fixture.componentInstance;

            expect(component.courseName()).toBe('');
            expect(component.lectures()).toHaveLength(0);
            expect(component.exercises()).toHaveLength(0);
        });

        it('should handle cached course with empty lectures and exercises', () => {
            courseStorageServiceMock.getCourse.mockReturnValue({ id: courseId, title: 'Empty Course', lectures: [], exercises: [] });
            fixture = TestBed.createComponent(ContextSelectionComponent);
            component = fixture.componentInstance;

            expect(component.courseName()).toBe('Empty Course');
            expect(component.lectures()).toHaveLength(0);
            expect(component.exercises()).toHaveLength(0);
        });

        it('should return empty data when courseId is undefined', () => {
            chatServiceMock.getCourseId.mockReturnValue(undefined);

            fixture = TestBed.createComponent(ContextSelectionComponent);
            component = fixture.componentInstance;

            expect(component.courseName()).toBe('');
            expect(component.lectures()).toHaveLength(0);
            expect(component.exercises()).toHaveLength(0);
        });
    });

    describe('supportedExercises', () => {
        it('should only include TEXT and PROGRAMMING exercise types', () => {
            const supported = component.supportedExercises();
            expect(supported).toHaveLength(2);
            expect(supported.map((e) => e.type)).toContain(ExerciseType.PROGRAMMING);
            expect(supported.map((e) => e.type)).toContain(ExerciseType.TEXT);
        });

        it('should exclude FILE_UPLOAD exercises', () => {
            const supported = component.supportedExercises();
            expect(supported.map((e) => e.type)).not.toContain(ExerciseType.FILE_UPLOAD);
        });
    });

    describe('selectedValue', () => {
        it('should return mode:entityId string when mode and entityId are set', () => {
            chatModeSubject.next(ChatServiceMode.LECTURE);
            entityIdSubject.next(7);
            fixture.detectChanges();

            expect(component.selectedValue()).toBe(`${ChatServiceMode.LECTURE}:7`);
        });

        it('should return undefined when mode is undefined', () => {
            chatModeSubject.next(undefined);
            entityIdSubject.next(7);
            fixture.detectChanges();

            expect(component.selectedValue()).toBeUndefined();
        });

        it('should return undefined when entityId is undefined', () => {
            chatModeSubject.next(ChatServiceMode.COURSE);
            entityIdSubject.next(undefined);
            fixture.detectChanges();

            expect(component.selectedValue()).toBeUndefined();
        });
    });

    describe('allGroups', () => {
        it('should include a course group with the course name', () => {
            const groups = component.allGroups();
            const courseGroup = groups.find((g) => g.label === 'artemisApp.iris.contextSelection.courseGroup');
            expect(courseGroup).toBeDefined();
            expect(courseGroup!.items).toHaveLength(1);
            expect(courseGroup!.items[0].label).toBe('Test Course');
            expect(courseGroup!.items[0].mode).toBe(ChatServiceMode.COURSE);
            expect(courseGroup!.items[0].entityId).toBe(courseId);
        });

        it('should include a lectures group', () => {
            const groups = component.allGroups();
            const lecturesGroup = groups.find((g) => g.label === 'artemisApp.iris.contextSelection.lecturesGroup');
            expect(lecturesGroup).toBeDefined();
            expect(lecturesGroup!.items).toHaveLength(2);
            expect(lecturesGroup!.items[0].mode).toBe(ChatServiceMode.LECTURE);
        });

        it('should include an exercises group containing only supported exercises', () => {
            const groups = component.allGroups();
            const exercisesGroup = groups.find((g) => g.label === 'artemisApp.iris.contextSelection.exercisesGroup');
            expect(exercisesGroup).toBeDefined();
            // FILE_UPLOAD is excluded, only PROGRAMMING and TEXT remain
            expect(exercisesGroup!.items).toHaveLength(2);
        });

        it('should assign correct modes for text and programming exercises in the exercises group', () => {
            const groups = component.allGroups();
            const exercisesGroup = groups.find((g) => g.label === 'artemisApp.iris.contextSelection.exercisesGroup');
            const programmingItem = exercisesGroup!.items.find((i) => i.label === 'Programming Ex');
            const textItem = exercisesGroup!.items.find((i) => i.label === 'Text Ex');

            expect(programmingItem?.mode).toBe(ChatServiceMode.PROGRAMMING_EXERCISE);
            expect(textItem?.mode).toBe(ChatServiceMode.TEXT_EXERCISE);
        });

        it('should not include course group when courseName is empty', () => {
            courseStorageServiceMock.getCourse.mockReturnValue({ id: courseId, title: '', lectures: [], exercises: [] });
            fixture = TestBed.createComponent(ContextSelectionComponent);
            component = fixture.componentInstance;

            const groups = component.allGroups();
            const courseGroup = groups.find((g) => g.label === 'artemisApp.iris.contextSelection.courseGroup');
            expect(courseGroup).toBeUndefined();
        });

        it('should not include lectures group when there are no lectures', () => {
            courseStorageServiceMock.getCourse.mockReturnValue({ id: courseId, title: 'Course', lectures: [], exercises: [] });
            fixture = TestBed.createComponent(ContextSelectionComponent);
            component = fixture.componentInstance;

            const groups = component.allGroups();
            const lecturesGroup = groups.find((g) => g.label === 'artemisApp.iris.contextSelection.lecturesGroup');
            expect(lecturesGroup).toBeUndefined();
        });

        it('should not include exercises group when there are no supported exercises', () => {
            courseStorageServiceMock.getCourse.mockReturnValue({
                id: courseId,
                title: 'Course',
                lectures: [],
                exercises: [{ id: 99, type: ExerciseType.FILE_UPLOAD }],
            });
            fixture = TestBed.createComponent(ContextSelectionComponent);
            component = fixture.componentInstance;

            const groups = component.allGroups();
            const exercisesGroup = groups.find((g) => g.label === 'artemisApp.iris.contextSelection.exercisesGroup');
            expect(exercisesGroup).toBeUndefined();
        });

        it('should build correct value strings for each option', () => {
            const allItems = component.allGroups().flatMap((g) => g.items);
            for (const item of allItems) {
                expect(item.value).toBe(`${item.mode}:${item.entityId}`);
            }
        });
    });

    describe('onSelectionChange', () => {
        it('should call chatService.switchToNewSession with the correct mode and entityId', () => {
            const value = `${ChatServiceMode.TEXT_EXERCISE}:11`;
            component.onSelectionChange(value);

            expect(chatServiceMock.switchToNewSession).toHaveBeenCalledWith(ChatServiceMode.TEXT_EXERCISE, 11);
        });

        it('should call switchToNewSession for a lecture option', () => {
            const value = `${ChatServiceMode.LECTURE}:1`;
            component.onSelectionChange(value);

            expect(chatServiceMock.switchToNewSession).toHaveBeenCalledWith(ChatServiceMode.LECTURE, 1);
        });

        it('should not call switchToNewSession when value does not match any option', () => {
            component.onSelectionChange('UNKNOWN_MODE:999');
            expect(chatServiceMock.switchToNewSession).not.toHaveBeenCalled();
        });
    });
});
