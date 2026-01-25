import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { ContextOption, ContextSelectionComponent } from './context-selection.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faFont, faGraduationCap, faKeyboard } from '@fortawesome/free-solid-svg-icons';

describe('ContextSelectionComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ContextSelectionComponent;
    let fixture: ComponentFixture<ContextSelectionComponent>;
    let compiled: DebugElement;
    let chatServiceMock: ReturnType<typeof createChatServiceMock>;
    let courseStorageServiceMock: ReturnType<typeof createCourseStorageServiceMock>;
    let courseManagementServiceMock: ReturnType<typeof createCourseManagementServiceMock>;

    const createChatServiceMock = () => ({
        switchTo: vi.fn<(mode: ChatServiceMode, id: number, resetSession: boolean) => void>(),
    });

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
            lectures: mockLectures,
            exercises: mockExercises,
        });

        TestBed.configureTestingModule({
            imports: [
                ContextSelectionComponent,
                MockComponent(FaIconComponent),
                MockComponent(SearchFilterComponent),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: IrisChatService, useValue: chatServiceMock },
                { provide: CourseStorageService, useValue: courseStorageServiceMock },
                { provide: CourseManagementService, useValue: courseManagementServiceMock },
            ],
        });

        fixture = TestBed.createComponent(ContextSelectionComponent);
        component = fixture.componentInstance;
        compiled = fixture.debugElement;
        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should have default selection as course', () => {
        expect(component.selectedType()).toBe('course');
        expect(component.selection()).toEqual({ type: 'course' });
    });

    it('should render three context options in main view', async () => {
        fixture.componentRef.setInput('courseId', 1);
        await fixture.whenStable();

        const buttons = compiled.queryAll(By.css('.context-selection-box'));
        expect(buttons).toHaveLength(3);
    });

    describe('onOptionClick', () => {
        beforeEach(async () => {
            fixture.componentRef.setInput('courseId', 1);
            await fixture.whenStable();
        });

        it('should switch to course mode when course option is clicked', () => {
            component.onOptionClick('course');

            expect(component.selection()).toEqual({ type: 'course' });
            expect(chatServiceMock.switchTo).toHaveBeenCalledWith(ChatServiceMode.COURSE, 1, true);
        });

        it('should navigate to lecture selection view when lecture option is clicked', () => {
            component.onOptionClick('lecture');

            expect(component.currentView()).toBe('lecture-selection');
            expect(component.searchQuery()).toBe('');
        });

        it('should navigate to exercise selection view when exercise option is clicked', () => {
            component.onOptionClick('exercise');

            expect(component.currentView()).toBe('exercise-selection');
            expect(component.searchQuery()).toBe('');
        });

        it('should not call chatService.switchTo if courseId is undefined', () => {
            fixture.componentRef.setInput('courseId', undefined);
            component.onOptionClick('course');

            expect(chatServiceMock.switchTo).not.toHaveBeenCalled();
        });
    });

    describe('Lecture selection', () => {
        beforeEach(async () => {
            fixture.componentRef.setInput('courseId', 1);
            await fixture.whenStable();
            component.onOptionClick('lecture');
            await fixture.whenStable();
        });

        it('should display lecture selection view', () => {
            expect(component.currentView()).toBe('lecture-selection');
        });

        it('should display all lectures', () => {
            expect(component.filteredLectures()).toHaveLength(3);
        });

        it('should filter lectures by search query', () => {
            component.onSearch('data');

            expect(component.filteredLectures()).toHaveLength(1);
            expect(component.filteredLectures()[0].title).toBe('Data Structures');
        });

        it('should select a lecture and switch to lecture mode', () => {
            const lecture = mockLectures[0];
            component.selectLecture(lecture);

            expect(component.selection()).toEqual({ type: 'lecture', lecture });
            expect(component.selectedLecture()).toBe(lecture);
            expect(component.currentView()).toBe('main');
            expect(chatServiceMock.switchTo).toHaveBeenCalledWith(ChatServiceMode.LECTURE, 1, true);
        });

        it('should go back to main view', () => {
            component.goBack();

            expect(component.currentView()).toBe('main');
            expect(component.searchQuery()).toBe('');
        });
    });

    describe('Exercise selection', () => {
        beforeEach(async () => {
            fixture.componentRef.setInput('courseId', 1);
            await fixture.whenStable();
            component.onOptionClick('exercise');
            await fixture.whenStable();
        });

        it('should display exercise selection view', () => {
            expect(component.currentView()).toBe('exercise-selection');
        });

        it('should only display supported exercise types (TEXT, PROGRAMMING)', () => {
            // Quiz exercises should be filtered out
            expect(component.filteredExercises()).toHaveLength(2);
            expect(component.filteredExercises().map((e) => e.type)).toEqual([ExerciseType.PROGRAMMING, ExerciseType.TEXT]);
        });

        it('should filter exercises by search query', () => {
            component.onSearch('hello');

            expect(component.filteredExercises()).toHaveLength(1);
            expect(component.filteredExercises()[0].title).toBe('Hello World');
        });

        it('should select a programming exercise and switch to programming exercise mode', () => {
            const exercise = mockExercises[0]; // PROGRAMMING
            component.selectExercise(exercise);

            expect(component.selection()).toEqual({ type: 'exercise', exercise });
            expect(component.selectedExercise()).toBe(exercise);
            expect(component.currentView()).toBe('main');
            expect(chatServiceMock.switchTo).toHaveBeenCalledWith(ChatServiceMode.PROGRAMMING_EXERCISE, 1, true);
        });

        it('should select a text exercise and switch to text exercise mode', () => {
            const exercise = mockExercises[1]; // TEXT
            component.selectExercise(exercise);

            expect(component.selection()).toEqual({ type: 'exercise', exercise });
            expect(chatServiceMock.switchTo).toHaveBeenCalledWith(ChatServiceMode.TEXT_EXERCISE, 2, true);
        });
    });

    describe('Data loading', () => {
        it('should load lectures and exercises from cache', async () => {
            fixture.componentRef.setInput('courseId', 1);
            await fixture.whenStable();

            expect(courseStorageServiceMock.getCourse).toHaveBeenCalledWith(1);
            expect(component.lectures()).toEqual(mockLectures);
            expect(component.exercises()).toEqual(mockExercises);
        });

        it('should fetch from server if cache is empty', async () => {
            courseStorageServiceMock.getCourse.mockReturnValue(undefined);
            courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies.mockReturnValue(
                of({
                    body: {
                        lectures: mockLectures,
                        exercises: mockExercises,
                    },
                }),
            );

            fixture.componentRef.setInput('courseId', 2);
            await fixture.whenStable();

            expect(courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies).toHaveBeenCalledWith(2);
            expect(component.lectures()).toEqual(mockLectures);
            expect(component.exercises()).toEqual(mockExercises);
        });
    });

    describe('Derived state', () => {
        it('should return undefined for selectedLecture when type is not lecture', () => {
            expect(component.selectedLecture()).toBeUndefined();
        });

        it('should return undefined for selectedExercise when type is not exercise', () => {
            expect(component.selectedExercise()).toBeUndefined();
        });

        it('should return lecture when selectedType is lecture', async () => {
            fixture.componentRef.setInput('courseId', 1);
            await fixture.whenStable();
            component.selectLecture(mockLectures[0]);

            expect(component.selectedLecture()).toBe(mockLectures[0]);
            expect(component.selectedExercise()).toBeUndefined();
        });

        it('should return exercise when selectedType is exercise', async () => {
            fixture.componentRef.setInput('courseId', 1);
            await fixture.whenStable();
            component.selectExercise(mockExercises[0]);

            expect(component.selectedExercise()).toBe(mockExercises[0]);
            expect(component.selectedLecture()).toBeUndefined();
        });
    });

    describe('Custom options', () => {
        it('should accept custom options input', async () => {
            const customOptions: ContextOption[] = [
                {
                    type: 'course',
                    icon: faGraduationCap,
                    titleKey: 'custom.title',
                    descriptionKey: 'custom.description',
                },
            ];

            fixture.componentRef.setInput('options', customOptions);
            await fixture.whenStable();

            expect(component.options()).toEqual(customOptions);
        });
    });

    describe('getExerciseIcon', () => {
        it('should return faKeyboard icon for programming exercise', () => {
            const programmingExercise = mockExercises[0];
            const icon = component.getExerciseIcon(programmingExercise);

            expect(icon).toBe(faKeyboard);
        });

        it('should return faFont icon for text exercise', () => {
            const textExercise = mockExercises[1];
            const icon = component.getExerciseIcon(textExercise);

            expect(icon).toBe(faFont);
        });
    });
});
