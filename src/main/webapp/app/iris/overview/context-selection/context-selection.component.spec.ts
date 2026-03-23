import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { ContextSelectionComponent } from './context-selection.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('ContextSelectionComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ContextSelectionComponent;
    let fixture: ComponentFixture<ContextSelectionComponent>;
    let chatServiceMock: ReturnType<typeof createChatServiceMock>;
    let courseStorageServiceMock: ReturnType<typeof createCourseStorageServiceMock>;
    let courseManagementServiceMock: ReturnType<typeof createCourseManagementServiceMock>;

    const createChatServiceMock = () => {
        const chatModeSubject = new BehaviorSubject<ChatServiceMode | undefined>(undefined);
        const entityIdSubject = new BehaviorSubject<number | undefined>(undefined);
        return {
            getCourseId: vi.fn().mockReturnValue(undefined),
            switchTo: vi.fn((mode: ChatServiceMode, id: number) => {
                chatModeSubject.next(mode);
                entityIdSubject.next(id);
            }),
            switchToNewSession: vi.fn(),
            currentChatMode: () => chatModeSubject.asObservable(),
            currentRelatedEntityId: () => entityIdSubject.asObservable(),
            chatModeSubject,
            entityIdSubject,
        };
    };

    const createCourseStorageServiceMock = () => ({
        getCourse: vi.fn(),
    });

    const createCourseManagementServiceMock = () => ({
        findWithExercisesAndLecturesAndCompetencies: vi.fn(),
    });

    const mockLectures: Lecture[] = [
        { id: 1, title: 'Introduction to Programming' } as Lecture,
        { id: 2, title: 'Data Structures' } as Lecture,
        { id: 3, title: 'Algorithms' } as Lecture,
    ];

    const mockExercises: Exercise[] = [
        { id: 1, title: 'Hello World', type: ExerciseType.PROGRAMMING } as Exercise,
        { id: 2, title: 'Essay Writing', type: ExerciseType.TEXT } as Exercise,
        { id: 3, title: 'Quiz 1', type: ExerciseType.QUIZ } as Exercise, // Should be filtered out
    ];

    beforeEach(async () => {
        vi.spyOn(console, 'warn').mockImplementation(() => {});

        chatServiceMock = createChatServiceMock();
        courseStorageServiceMock = createCourseStorageServiceMock();
        courseManagementServiceMock = createCourseManagementServiceMock();

        // Default: return cached course with lectures and exercises
        courseStorageServiceMock.getCourse.mockReturnValue({
            title: 'Test Course',
            lectures: mockLectures,
            exercises: mockExercises,
        });

        TestBed.configureTestingModule({
            imports: [ContextSelectionComponent, MockComponent(FaIconComponent), MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: IrisChatService, useValue: chatServiceMock },
                { provide: CourseStorageService, useValue: courseStorageServiceMock },
                { provide: CourseManagementService, useValue: courseManagementServiceMock },
            ],
        });

        fixture = TestBed.createComponent(ContextSelectionComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    afterEach(() => {
        sessionStorage.clear();
        vi.restoreAllMocks();
    });

    describe('Initial state (no courseId)', () => {
        it('should not load data when courseId is undefined', () => {
            expect(courseStorageServiceMock.getCourse).not.toHaveBeenCalled();
            expect(courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies).not.toHaveBeenCalled();
            expect(component.lectures()).toEqual([]);
            expect(component.exercises()).toEqual([]);
            expect(component.courseName()).toBe('');
        });

        it('should have no groups when no courseId is set', () => {
            expect(component.allGroups()).toEqual([]);
        });

        it('should have undefined selectedValue when no mode is set', () => {
            expect(component.selectedValue()).toBeUndefined();
        });
    });

    describe('Data loading from cache', () => {
        beforeEach(async () => {
            component.courseId.set(1);
            await fixture.whenStable();
        });

        it('should load lectures and exercises from cache', () => {
            expect(courseStorageServiceMock.getCourse).toHaveBeenCalledWith(1);
            expect(component.lectures()).toEqual(mockLectures);
            expect(component.exercises()).toEqual(mockExercises);
        });

        it('should set courseName from cache', () => {
            expect(component.courseName()).toBe('Test Course');
        });
    });

    describe('Data loading from server', () => {
        beforeEach(async () => {
            courseStorageServiceMock.getCourse.mockReturnValue(undefined);
            courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies.mockReturnValue(
                of({
                    body: {
                        title: 'Server Course',
                        lectures: mockLectures,
                        exercises: mockExercises,
                    },
                }),
            );

            component.courseId.set(2);
            await fixture.whenStable();
        });

        it('should fetch from server if cache is empty', () => {
            expect(courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies).toHaveBeenCalledWith(2);
            expect(component.lectures()).toEqual(mockLectures);
            expect(component.exercises()).toEqual(mockExercises);
        });

        it('should set courseName from server response', () => {
            expect(component.courseName()).toBe('Server Course');
        });
    });

    describe('supportedExercises', () => {
        beforeEach(async () => {
            component.courseId.set(1);
            await fixture.whenStable();
        });

        it('should filter out unsupported exercise types (QUIZ)', () => {
            expect(component.supportedExercises()).toHaveLength(2);
            expect(component.supportedExercises().map((e) => e.type)).toEqual([ExerciseType.PROGRAMMING, ExerciseType.TEXT]);
        });
    });

    describe('allGroups computed', () => {
        beforeEach(async () => {
            component.courseId.set(1);
            await fixture.whenStable();
        });

        it('should include course group when courseName is set', () => {
            const courseGroup = component.allGroups().find((g) => g.label === 'artemisApp.iris.contextSelection.courseGroup');
            expect(courseGroup).toBeDefined();
            expect(courseGroup!.items).toHaveLength(1);
            expect(courseGroup!.items[0].label).toBe('Test Course');
            expect(courseGroup!.items[0].mode).toBe(ChatServiceMode.COURSE);
            expect(courseGroup!.items[0].entityId).toBe(1);
        });

        it('should include lecture group with all lectures', () => {
            const lectureGroup = component.allGroups().find((g) => g.label === 'artemisApp.iris.contextSelection.lecturesGroup');
            expect(lectureGroup).toBeDefined();
            expect(lectureGroup!.items).toHaveLength(3);
            expect(lectureGroup!.items[0].mode).toBe(ChatServiceMode.LECTURE);
        });

        it('should include exercise group with only supported exercises', () => {
            const exerciseGroup = component.allGroups().find((g) => g.label === 'artemisApp.iris.contextSelection.exercisesGroup');
            expect(exerciseGroup).toBeDefined();
            expect(exerciseGroup!.items).toHaveLength(2);
        });

        it('should be empty when no courseId is set', () => {
            const fresh = TestBed.createComponent(ContextSelectionComponent);
            expect(fresh.componentInstance.allGroups()).toEqual([]);
        });
    });

    describe('selectedValue computed', () => {
        beforeEach(async () => {
            component.courseId.set(1);
            await fixture.whenStable();
        });

        it('should reflect current chat mode and entity ID', async () => {
            chatServiceMock.chatModeSubject.next(ChatServiceMode.COURSE);
            chatServiceMock.entityIdSubject.next(1);
            await fixture.whenStable();

            expect(component.selectedValue()).toBe(`${ChatServiceMode.COURSE}:1`);
        });

        it('should update when mode changes to lecture', async () => {
            chatServiceMock.chatModeSubject.next(ChatServiceMode.LECTURE);
            chatServiceMock.entityIdSubject.next(2);
            await fixture.whenStable();

            expect(component.selectedValue()).toBe(`${ChatServiceMode.LECTURE}:2`);
        });

        it('should be undefined when mode is undefined', () => {
            expect(component.selectedValue()).toBeUndefined();
        });

        it('should be undefined when entityId is undefined', async () => {
            chatServiceMock.chatModeSubject.next(ChatServiceMode.COURSE);
            await fixture.whenStable();

            expect(component.selectedValue()).toBeUndefined();
        });
    });

    describe('onSelectionChange', () => {
        beforeEach(async () => {
            component.courseId.set(1);
            await fixture.whenStable();
        });

        it('should call chatService.switchToNewSession with COURSE mode', () => {
            component.onSelectionChange(`${ChatServiceMode.COURSE}:1`);

            expect(chatServiceMock.switchToNewSession).toHaveBeenCalledWith(ChatServiceMode.COURSE, 1, 'Test Course');
        });

        it('should call chatService.switchToNewSession with LECTURE mode', () => {
            component.onSelectionChange(`${ChatServiceMode.LECTURE}:1`);

            expect(chatServiceMock.switchToNewSession).toHaveBeenCalledWith(ChatServiceMode.LECTURE, 1, 'Introduction to Programming');
        });

        it('should call chatService.switchToNewSession with PROGRAMMING_EXERCISE mode', () => {
            component.onSelectionChange(`${ChatServiceMode.PROGRAMMING_EXERCISE}:1`);

            expect(chatServiceMock.switchToNewSession).toHaveBeenCalledWith(ChatServiceMode.PROGRAMMING_EXERCISE, 1, 'Hello World');
        });

        it('should call chatService.switchToNewSession with TEXT_EXERCISE mode', () => {
            component.onSelectionChange(`${ChatServiceMode.TEXT_EXERCISE}:2`);

            expect(chatServiceMock.switchToNewSession).toHaveBeenCalledWith(ChatServiceMode.TEXT_EXERCISE, 2, 'Essay Writing');
        });

        it('should not call switchToNewSession for unknown value', () => {
            chatServiceMock.switchToNewSession.mockClear();
            component.onSelectionChange('UNKNOWN:999');

            expect(chatServiceMock.switchToNewSession).not.toHaveBeenCalled();
        });
    });

    describe('Data loading falls back to server when cache has empty arrays', () => {
        it('should fetch from server when cache has no lectures and no exercises', async () => {
            courseStorageServiceMock.getCourse.mockReturnValue({
                title: 'Cached Course',
                lectures: [],
                exercises: [],
            });
            courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies.mockReturnValue(
                of({
                    body: {
                        title: 'Server Course',
                        lectures: mockLectures,
                        exercises: mockExercises,
                    },
                }),
            );

            component.courseId.set(5);
            await fixture.whenStable();

            expect(courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies).toHaveBeenCalledWith(5);
            expect(component.courseName()).toBe('Server Course');
            expect(component.lectures()).toEqual(mockLectures);
            expect(component.exercises()).toEqual(mockExercises);
        });
    });

    describe('Server error handling', () => {
        it('should set empty data when server request fails', async () => {
            courseStorageServiceMock.getCourse.mockReturnValue(undefined);
            courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies.mockReturnValue(throwError(() => new Error('Server error')));

            component.courseId.set(3);
            await fixture.whenStable();

            expect(component.courseName()).toBe('');
            expect(component.lectures()).toEqual([]);
            expect(component.exercises()).toEqual([]);
        });
    });

    describe('Reactive selection via chat service mode', () => {
        beforeEach(async () => {
            component.courseId.set(1);
            await fixture.whenStable();
        });

        it('should update selectedValue when chat service reports LECTURE mode', async () => {
            chatServiceMock.chatModeSubject.next(ChatServiceMode.LECTURE);
            chatServiceMock.entityIdSubject.next(2);
            await fixture.whenStable();

            expect(component.selectedValue()).toBe(`${ChatServiceMode.LECTURE}:2`);
        });

        it('should update selectedValue when chat service reports PROGRAMMING_EXERCISE mode', async () => {
            chatServiceMock.chatModeSubject.next(ChatServiceMode.PROGRAMMING_EXERCISE);
            chatServiceMock.entityIdSubject.next(1);
            await fixture.whenStable();

            expect(component.selectedValue()).toBe(`${ChatServiceMode.PROGRAMMING_EXERCISE}:1`);
        });

        it('should update selectedValue when mode changes multiple times', async () => {
            chatServiceMock.chatModeSubject.next(ChatServiceMode.LECTURE);
            chatServiceMock.entityIdSubject.next(1);
            await fixture.whenStable();
            expect(component.selectedValue()).toBe(`${ChatServiceMode.LECTURE}:1`);

            chatServiceMock.chatModeSubject.next(ChatServiceMode.PROGRAMMING_EXERCISE);
            chatServiceMock.entityIdSubject.next(1);
            await fixture.whenStable();
            expect(component.selectedValue()).toBe(`${ChatServiceMode.PROGRAMMING_EXERCISE}:1`);
        });

        it('should update selectedValue when data arrives after mode is already set', async () => {
            courseStorageServiceMock.getCourse.mockReturnValue(undefined);
            const delayedSubject = new Subject<any>();
            courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies.mockReturnValue(delayedSubject);

            chatServiceMock.chatModeSubject.next(ChatServiceMode.LECTURE);
            chatServiceMock.entityIdSubject.next(3);

            const freshFixture = TestBed.createComponent(ContextSelectionComponent);
            freshFixture.componentInstance.courseId.set(99);
            await freshFixture.whenStable();

            // Mode is set but no groups yet
            expect(freshFixture.componentInstance.selectedValue()).toBe(`${ChatServiceMode.LECTURE}:3`);
            expect(freshFixture.componentInstance.allGroups()).toEqual([]);

            // Data arrives — groups now populated
            delayedSubject.next({
                body: {
                    title: 'Late Course',
                    lectures: mockLectures,
                    exercises: mockExercises,
                },
            });
            await freshFixture.whenStable();

            expect(freshFixture.componentInstance.allGroups().length).toBeGreaterThan(0);
            expect(freshFixture.componentInstance.selectedValue()).toBe(`${ChatServiceMode.LECTURE}:3`);
        });
    });
});
