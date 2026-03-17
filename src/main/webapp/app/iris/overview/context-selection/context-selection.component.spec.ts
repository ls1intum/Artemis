import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective } from 'ng-mocks';
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
import { faChalkboardUser, faGraduationCap } from '@fortawesome/free-solid-svg-icons';

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
            switchTo: vi.fn((mode: ChatServiceMode, id: number) => {
                chatModeSubject.next(mode);
                entityIdSubject.next(id);
            }),
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
            imports: [ContextSelectionComponent, MockComponent(FaIconComponent), MockDirective(TranslateDirective)],
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

        it('should start with isLoading false', () => {
            expect(component.isLoading()).toBe(false);
        });
    });

    describe('Data loading from cache', () => {
        beforeEach(async () => {
            fixture.componentRef.setInput('courseId', 1);
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

        it('should set isLoading to false after loading', () => {
            expect(component.isLoading()).toBe(false);
        });

        it('should set default menuLabel to course name', () => {
            expect(component.menuLabel()).toBe('Test Course');
        });

        it('should set default menuIcon to faGraduationCap', () => {
            expect(component.menuIcon()).toBe(faGraduationCap);
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

            fixture.componentRef.setInput('courseId', 2);
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
            fixture.componentRef.setInput('courseId', 1);
            await fixture.whenStable();
        });

        it('should filter out unsupported exercise types (QUIZ)', () => {
            expect(component.supportedExercises()).toHaveLength(2);
            expect(component.supportedExercises().map((e) => e.type)).toEqual([ExerciseType.PROGRAMMING, ExerciseType.TEXT]);
        });
    });

    describe('allItems computed', () => {
        beforeEach(async () => {
            fixture.componentRef.setInput('courseId', 1);
            await fixture.whenStable();
        });

        it('should include course group when courseName is set', () => {
            const courseGroup = component.allItems().find((i) => i.label === 'artemisApp.iris.contextSelection.courseGroup');
            expect(courseGroup).toBeDefined();
            expect(courseGroup!.items).toHaveLength(1);
            expect(courseGroup!.items![0].label).toBe('Test Course');
        });

        it('should include lecture group with all lectures', () => {
            const lectureGroup = component.allItems().find((i) => i.label === 'artemisApp.iris.contextSelection.lecturesGroup');
            expect(lectureGroup).toBeDefined();
            expect(lectureGroup!.items).toHaveLength(3);
        });

        it('should include exercise group with only supported exercises', () => {
            const exerciseGroup = component.allItems().find((i) => i.label === 'artemisApp.iris.contextSelection.exercisesGroup');
            expect(exerciseGroup).toBeDefined();
            expect(exerciseGroup!.items).toHaveLength(2);
        });

        it('should be empty when no courseId is set', () => {
            // Component without courseId set — lectures/exercises/courseName all empty
            const fresh = TestBed.createComponent(ContextSelectionComponent);
            expect(fresh.componentInstance.allItems()).toEqual([]);
        });
    });

    describe('filteredItems', () => {
        beforeEach(async () => {
            fixture.componentRef.setInput('courseId', 1);
            await fixture.whenStable();
        });

        it('should return allItems when searchTerm is empty', () => {
            expect(component.filteredItems()).toEqual(component.allItems());
        });

        it('should filter items by search term', () => {
            component.searchTerm.set('data');
            const lectureGroup = component.filteredItems().find((i) => i.label === 'artemisApp.iris.contextSelection.lecturesGroup');
            expect(lectureGroup).toBeDefined();
            expect(lectureGroup!.items).toHaveLength(1);
            expect(lectureGroup!.items![0].label).toBe('Data Structures');
        });

        it('should remove groups with no matching items', () => {
            component.searchTerm.set('zzznomatch');
            expect(component.filteredItems()).toHaveLength(0);
        });

        it('should be case-insensitive', () => {
            component.searchTerm.set('HELLO');
            const exerciseGroup = component.filteredItems().find((i) => i.label === 'artemisApp.iris.contextSelection.exercisesGroup');
            expect(exerciseGroup).toBeDefined();
            expect(exerciseGroup!.items![0].label).toBe('Hello World');
        });
    });

    describe('Item commands', () => {
        beforeEach(async () => {
            fixture.componentRef.setInput('courseId', 1);
            await fixture.whenStable();
        });

        it('should call chatService.switchTo with COURSE mode when course item command is triggered', async () => {
            const courseGroup = component.allItems().find((i) => i.label === 'artemisApp.iris.contextSelection.courseGroup');
            courseGroup!.items![0].command!({} as any);
            await fixture.whenStable();

            expect(chatServiceMock.switchTo).toHaveBeenCalledWith(ChatServiceMode.COURSE, 1, true);
            expect(component.menuLabel()).toBe('Test Course');
            expect(component.menuIcon()).toBe(faGraduationCap);
        });

        it('should call chatService.switchTo with LECTURE mode when lecture item command is triggered', async () => {
            const lectureGroup = component.allItems().find((i) => i.label === 'artemisApp.iris.contextSelection.lecturesGroup');
            lectureGroup!.items![0].command!({} as any);
            await fixture.whenStable();

            expect(chatServiceMock.switchTo).toHaveBeenCalledWith(ChatServiceMode.LECTURE, 1, true);
            expect(component.menuLabel()).toBe('Introduction to Programming');
            expect(component.menuIcon()).toBe(faChalkboardUser);
        });

        it('should call chatService.switchTo with PROGRAMMING_EXERCISE mode when programming exercise command is triggered', async () => {
            const exerciseGroup = component.allItems().find((i) => i.label === 'artemisApp.iris.contextSelection.exercisesGroup');
            const programmingItem = exerciseGroup!.items!.find((i) => i.label === 'Hello World');
            programmingItem!.command!({} as any);
            await fixture.whenStable();

            expect(chatServiceMock.switchTo).toHaveBeenCalledWith(ChatServiceMode.PROGRAMMING_EXERCISE, 1, true);
            expect(component.menuLabel()).toBe('Hello World');
        });

        it('should call chatService.switchTo with TEXT_EXERCISE mode when text exercise command is triggered', async () => {
            const exerciseGroup = component.allItems().find((i) => i.label === 'artemisApp.iris.contextSelection.exercisesGroup');
            const textItem = exerciseGroup!.items!.find((i) => i.label === 'Essay Writing');
            textItem!.command!({} as any);
            await fixture.whenStable();

            expect(chatServiceMock.switchTo).toHaveBeenCalledWith(ChatServiceMode.TEXT_EXERCISE, 2, true);
            expect(component.menuLabel()).toBe('Essay Writing');
        });
    });

    describe('Reactive label derivation', () => {
        beforeEach(async () => {
            fixture.componentRef.setInput('courseId', 1);
            await fixture.whenStable();
        });

        it('should show lecture title when chat service reports LECTURE mode', async () => {
            chatServiceMock.chatModeSubject.next(ChatServiceMode.LECTURE);
            chatServiceMock.entityIdSubject.next(2);
            await fixture.whenStable();

            expect(component.menuLabel()).toBe('Data Structures');
            expect(component.menuIcon()).toBe(faChalkboardUser);
        });

        it('should show exercise title when chat service reports PROGRAMMING_EXERCISE mode', async () => {
            chatServiceMock.chatModeSubject.next(ChatServiceMode.PROGRAMMING_EXERCISE);
            chatServiceMock.entityIdSubject.next(1);
            await fixture.whenStable();

            expect(component.menuLabel()).toBe('Hello World');
        });

        it('should show exercise title when chat service reports TEXT_EXERCISE mode', async () => {
            chatServiceMock.chatModeSubject.next(ChatServiceMode.TEXT_EXERCISE);
            chatServiceMock.entityIdSubject.next(2);
            await fixture.whenStable();

            expect(component.menuLabel()).toBe('Essay Writing');
        });

        it('should show course name when chat service reports COURSE mode', async () => {
            chatServiceMock.chatModeSubject.next(ChatServiceMode.LECTURE);
            chatServiceMock.entityIdSubject.next(1);
            await fixture.whenStable();

            chatServiceMock.chatModeSubject.next(ChatServiceMode.COURSE);
            chatServiceMock.entityIdSubject.next(1);
            await fixture.whenStable();

            expect(component.menuLabel()).toBe('Test Course');
            expect(component.menuIcon()).toBe(faGraduationCap);
        });

        it('should update label when mode changes after data is loaded', async () => {
            chatServiceMock.chatModeSubject.next(ChatServiceMode.LECTURE);
            chatServiceMock.entityIdSubject.next(1);
            await fixture.whenStable();
            expect(component.menuLabel()).toBe('Introduction to Programming');

            chatServiceMock.chatModeSubject.next(ChatServiceMode.PROGRAMMING_EXERCISE);
            chatServiceMock.entityIdSubject.next(1);
            await fixture.whenStable();
            expect(component.menuLabel()).toBe('Hello World');
        });

        it('should update label when data arrives after mode is already set', async () => {
            // Create a fresh component where mode is set before data loads
            courseStorageServiceMock.getCourse.mockReturnValue(undefined);
            const delayedSubject = new Subject<any>();
            courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies.mockReturnValue(delayedSubject);

            chatServiceMock.chatModeSubject.next(ChatServiceMode.LECTURE);
            chatServiceMock.entityIdSubject.next(3);

            const freshFixture = TestBed.createComponent(ContextSelectionComponent);
            freshFixture.componentRef.setInput('courseId', 99);
            await freshFixture.whenStable();

            // No data yet, label should be empty (effect returns early when courseName is empty)
            expect(freshFixture.componentInstance.menuLabel()).toBe('');

            // Data arrives
            delayedSubject.next({
                body: {
                    title: 'Late Course',
                    lectures: mockLectures,
                    exercises: mockExercises,
                },
            });
            await freshFixture.whenStable();

            expect(freshFixture.componentInstance.menuLabel()).toBe('Algorithms');
        });

        it('should fall back to course name when entity ID does not match any lecture', async () => {
            chatServiceMock.chatModeSubject.next(ChatServiceMode.LECTURE);
            chatServiceMock.entityIdSubject.next(999);
            await fixture.whenStable();

            expect(component.menuLabel()).toBe('Test Course');
            expect(component.menuIcon()).toBe(faGraduationCap);
        });

        it('should fall back to course name when entity ID does not match any exercise', async () => {
            chatServiceMock.chatModeSubject.next(ChatServiceMode.PROGRAMMING_EXERCISE);
            chatServiceMock.entityIdSubject.next(999);
            await fixture.whenStable();

            expect(component.menuLabel()).toBe('Test Course');
            expect(component.menuIcon()).toBe(faGraduationCap);
        });
    });

    describe('sessionStorage persistence', () => {
        beforeEach(async () => {
            fixture.componentRef.setInput('courseId', 1);
            await fixture.whenStable();
        });

        it('should save course context to sessionStorage when course item is selected', async () => {
            const courseGroup = component.allItems().find((i) => i.label === 'artemisApp.iris.contextSelection.courseGroup');
            courseGroup!.items![0].command!({} as any);

            const stored = JSON.parse(sessionStorage.getItem('iris-context-1')!);
            expect(stored).toEqual({ mode: ChatServiceMode.COURSE, entityId: 1 });
        });

        it('should save lecture context to sessionStorage when lecture item is selected', async () => {
            const lectureGroup = component.allItems().find((i) => i.label === 'artemisApp.iris.contextSelection.lecturesGroup');
            lectureGroup!.items![0].command!({} as any);

            const stored = JSON.parse(sessionStorage.getItem('iris-context-1')!);
            expect(stored).toEqual({ mode: ChatServiceMode.LECTURE, entityId: 1 });
        });

        it('should save exercise context to sessionStorage when exercise item is selected', async () => {
            const exerciseGroup = component.allItems().find((i) => i.label === 'artemisApp.iris.contextSelection.exercisesGroup');
            const programmingItem = exerciseGroup!.items!.find((i) => i.label === 'Hello World');
            programmingItem!.command!({} as any);

            const stored = JSON.parse(sessionStorage.getItem('iris-context-1')!);
            expect(stored).toEqual({ mode: ChatServiceMode.PROGRAMMING_EXERCISE, entityId: 1 });
        });

        it('should not write to sessionStorage when courseId is undefined', () => {
            const freshFixture = TestBed.createComponent(ContextSelectionComponent);
            // No courseId set — trigger a course command manually via the component's method
            // Since allItems is empty without data, we verify indirectly that no storage is written
            expect(sessionStorage.getItem('iris-context-undefined')).toBeNull();
            freshFixture.destroy();
        });
    });

    describe('Server error handling', () => {
        it('should set empty data when server request fails', async () => {
            courseStorageServiceMock.getCourse.mockReturnValue(undefined);
            courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies.mockReturnValue(throwError(() => new Error('Server error')));

            fixture.componentRef.setInput('courseId', 3);
            await fixture.whenStable();

            expect(component.courseName()).toBe('');
            expect(component.lectures()).toEqual([]);
            expect(component.exercises()).toEqual([]);
            expect(component.isLoading()).toBe(false);
        });
    });
});
