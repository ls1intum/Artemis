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
        switchContextOfCurrentSession: ReturnType<typeof vi.fn>;
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
            switchContextOfCurrentSession: vi.fn(),
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
            expect(component.lectures()).toHaveLength(2);
            expect(component.exercises()).toHaveLength(3);
        });

        it('should return empty data when cache has no course', () => {
            courseStorageServiceMock.getCourse.mockReturnValue(undefined);
            fixture = TestBed.createComponent(ContextSelectionComponent);
            component = fixture.componentInstance;

            expect(component.lectures()).toHaveLength(0);
            expect(component.exercises()).toHaveLength(0);
        });

        it('should handle cached course with empty lectures and exercises', () => {
            courseStorageServiceMock.getCourse.mockReturnValue({ id: courseId, title: 'Empty Course', lectures: [], exercises: [] });
            fixture = TestBed.createComponent(ContextSelectionComponent);
            component = fixture.componentInstance;

            expect(component.lectures()).toHaveLength(0);
            expect(component.exercises()).toHaveLength(0);
        });

        it('should return empty data when courseId is undefined', () => {
            chatServiceMock.getCourseId.mockReturnValue(undefined);

            fixture = TestBed.createComponent(ContextSelectionComponent);
            component = fixture.componentInstance;

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
        it('should not include a course group', () => {
            const groups = component.allGroups();
            const courseGroup = groups.find((g) => g.label === 'artemisApp.iris.contextSelection.courseGroup');
            expect(courseGroup).toBeUndefined();
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
        it('should call chatService.switchContextOfCurrentSession with the correct mode and entityId', () => {
            const value = `${ChatServiceMode.TEXT_EXERCISE}:11`;
            component.onSelectionChange(value);

            expect(chatServiceMock.switchContextOfCurrentSession).toHaveBeenCalledWith(ChatServiceMode.TEXT_EXERCISE, 11);
        });

        it('should call switchContextOfCurrentSession for a lecture option', () => {
            const value = `${ChatServiceMode.LECTURE}:1`;
            component.onSelectionChange(value);

            expect(chatServiceMock.switchContextOfCurrentSession).toHaveBeenCalledWith(ChatServiceMode.LECTURE, 1);
        });

        it('should not call switchContextOfCurrentSession when value does not match any option', () => {
            component.onSelectionChange('UNKNOWN_MODE:999');
            expect(chatServiceMock.switchContextOfCurrentSession).not.toHaveBeenCalled();
        });
    });

    describe('activeChip', () => {
        it('should return undefined when mode is COURSE', () => {
            chatModeSubject.next(ChatServiceMode.COURSE);
            entityIdSubject.next(courseId);
            fixture.detectChanges();

            expect(component.activeChip()).toBeUndefined();
        });

        it('should return undefined when mode or entityId is undefined', () => {
            chatModeSubject.next(undefined);
            entityIdSubject.next(undefined);
            fixture.detectChanges();

            expect(component.activeChip()).toBeUndefined();
        });

        it('should return the matching option for an active lecture context', () => {
            chatModeSubject.next(ChatServiceMode.LECTURE);
            entityIdSubject.next(1);
            fixture.detectChanges();

            const chip = component.activeChip();
            expect(chip).toBeDefined();
            expect(chip!.mode).toBe(ChatServiceMode.LECTURE);
            expect(chip!.entityId).toBe(1);
            expect(chip!.label).toBe('Lecture 1');
        });

        it('should return the matching option for an active exercise context', () => {
            chatModeSubject.next(ChatServiceMode.PROGRAMMING_EXERCISE);
            entityIdSubject.next(10);
            fixture.detectChanges();

            const chip = component.activeChip();
            expect(chip).toBeDefined();
            expect(chip!.mode).toBe(ChatServiceMode.PROGRAMMING_EXERCISE);
            expect(chip!.entityId).toBe(10);
            expect(chip!.label).toBe('Programming Ex');
        });
    });

    describe('onChipRemove', () => {
        it('should switch context back to the course', () => {
            component.onChipRemove();

            expect(chatServiceMock.switchContextOfCurrentSession).toHaveBeenCalledWith(ChatServiceMode.COURSE, courseId);
        });

        it('should do nothing when courseId is undefined', () => {
            chatServiceMock.getCourseId.mockReturnValue(undefined);
            fixture = TestBed.createComponent(ContextSelectionComponent);
            component = fixture.componentInstance;

            component.onChipRemove();

            expect(chatServiceMock.switchContextOfCurrentSession).not.toHaveBeenCalled();
        });
    });
});
