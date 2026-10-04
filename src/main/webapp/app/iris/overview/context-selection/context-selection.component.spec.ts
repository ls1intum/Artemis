import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockPipe } from 'ng-mocks';
import { WritableSignal, signal } from '@angular/core';
import { ContextSelectionComponent } from './context-selection.component';
import { ChatServiceMode, IrisChatService, SessionContext } from 'app/iris/overview/services/iris-chat.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
import { Subject, of, throwError } from 'rxjs';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ContextSelectionComponent', () => {
    let component: ContextSelectionComponent;
    let fixture: ComponentFixture<ContextSelectionComponent>;

    let chatServiceMock: {
        getCourseId: ReturnType<typeof vi.fn>;
        displayContext: WritableSignal<SessionContext | undefined>;
        stagePendingContext: ReturnType<typeof vi.fn>;
    };
    let lectureServiceMock: { findAllByCourseIdForOverview: ReturnType<typeof vi.fn> };
    let exerciseServiceMock: { getTitlesForCourse: ReturnType<typeof vi.fn> };
    let entityTitleServiceMock: { getTitle: ReturnType<typeof vi.fn> };

    const courseId = 42;

    const lectures = [
        { id: 1, title: 'Lecture 1' },
        { id: 2, title: 'Lecture 2' },
    ];
    const exercises = [
        { id: 10, title: 'Programming Ex', type: ExerciseType.PROGRAMMING },
        { id: 11, title: 'Text Ex', type: ExerciseType.TEXT },
        { id: 12, title: 'File Upload Ex', type: ExerciseType.FILE_UPLOAD },
    ];

    beforeEach(async () => {
        chatServiceMock = {
            getCourseId: vi.fn().mockReturnValue(courseId),
            displayContext: signal<SessionContext | undefined>({ mode: ChatServiceMode.COURSE, entityId: courseId }),
            stagePendingContext: vi.fn(),
        };

        lectureServiceMock = { findAllByCourseIdForOverview: vi.fn().mockReturnValue(of(lectures)) };
        exerciseServiceMock = { getTitlesForCourse: vi.fn().mockReturnValue(of(exercises)) };
        entityTitleServiceMock = { getTitle: vi.fn().mockReturnValue(of('Resolved Title')) };

        await TestBed.configureTestingModule({
            imports: [ContextSelectionComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [
                { provide: IrisChatService, useValue: chatServiceMock },
                { provide: LectureService, useValue: lectureServiceMock },
                { provide: ExerciseService, useValue: exerciseServiceMock },
                { provide: EntityTitleService, useValue: entityTitleServiceMock },
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
        it('should not load any options until the picker is opened', () => {
            expect(lectureServiceMock.findAllByCourseIdForOverview).not.toHaveBeenCalled();
            expect(exerciseServiceMock.getTitlesForCourse).not.toHaveBeenCalled();
            expect(component.lectures()).toHaveLength(0);
            expect(component.exercises()).toHaveLength(0);
        });

        it('should load lectures and exercises when the picker is opened', () => {
            component.loadContextOptions();

            expect(lectureServiceMock.findAllByCourseIdForOverview).toHaveBeenCalledExactlyOnceWith(courseId);
            expect(exerciseServiceMock.getTitlesForCourse).toHaveBeenCalledExactlyOnceWith(courseId);
            expect(component.lectures()).toHaveLength(2);
            expect(component.exercises()).toHaveLength(3);
        });

        it('should load the options only once per course', () => {
            component.loadContextOptions();
            component.loadContextOptions();

            expect(lectureServiceMock.findAllByCourseIdForOverview).toHaveBeenCalledOnce();
            expect(exerciseServiceMock.getTitlesForCourse).toHaveBeenCalledOnce();
        });

        it('should leave the options empty when loading fails', () => {
            lectureServiceMock.findAllByCourseIdForOverview.mockReturnValue(throwError(() => new Error('network')));
            exerciseServiceMock.getTitlesForCourse.mockReturnValue(throwError(() => new Error('network')));

            component.loadContextOptions();

            expect(component.lectures()).toHaveLength(0);
            expect(component.exercises()).toHaveLength(0);
        });

        it.each(['lectures', 'exercises'] as const)('should retry both option requests after loading %s fails', (failedRequest) => {
            if (failedRequest === 'lectures') {
                lectureServiceMock.findAllByCourseIdForOverview.mockReturnValue(throwError(() => new Error('lecture network')));
            } else {
                exerciseServiceMock.getTitlesForCourse.mockReturnValue(throwError(() => new Error('exercise network')));
            }

            component.loadContextOptions();

            expect(component.lectures()).toEqual([]);
            expect(component.exercises()).toEqual([]);
            lectureServiceMock.findAllByCourseIdForOverview.mockReturnValue(of(lectures));
            exerciseServiceMock.getTitlesForCourse.mockReturnValue(of(exercises));

            component.loadContextOptions();

            expect(lectureServiceMock.findAllByCourseIdForOverview).toHaveBeenCalledTimes(2);
            expect(exerciseServiceMock.getTitlesForCourse).toHaveBeenCalledTimes(2);
            expect(component.lectures()).toEqual(lectures);
            expect(component.exercises()).toEqual(exercises);
        });

        it('should not load anything when no course is known', () => {
            chatServiceMock.getCourseId.mockReturnValue(undefined);
            fixture = TestBed.createComponent(ContextSelectionComponent);
            component = fixture.componentInstance;

            component.loadContextOptions();

            expect(lectureServiceMock.findAllByCourseIdForOverview).not.toHaveBeenCalled();
            expect(exerciseServiceMock.getTitlesForCourse).not.toHaveBeenCalled();
        });
    });

    describe('active context chip', () => {
        it('should render a chip for a page-set context without loading the picker options', async () => {
            // A page can set a context carrying no name; the chip must still label it.
            chatServiceMock.displayContext.set({ mode: ChatServiceMode.LECTURE, entityId: 1 });
            fixture = TestBed.createComponent(ContextSelectionComponent);
            component = fixture.componentInstance;
            await fixture.whenStable();

            expect(entityTitleServiceMock.getTitle).toHaveBeenCalled();
            expect(component.activeChip()?.label).toBe('Resolved Title');
            expect(lectureServiceMock.findAllByCourseIdForOverview).not.toHaveBeenCalled();
        });

        it('should prefer the name the context already carries', async () => {
            chatServiceMock.displayContext.set({ mode: ChatServiceMode.LECTURE, entityId: 1, entityName: 'Named Lecture' });
            fixture = TestBed.createComponent(ContextSelectionComponent);
            component = fixture.componentInstance;
            await fixture.whenStable();

            expect(component.activeChip()?.label).toBe('Named Lecture');
        });

        it('should label a tutor suggestion without resolving its post id as an exercise', async () => {
            // The tutor suggestion context is keyed by the id of the communication post it was raised from. Resolving
            // that as an exercise title labelled the chip with whichever unrelated exercise shared the number, and left
            // it blank when none did.
            chatServiceMock.displayContext.set({ mode: ChatServiceMode.TUTOR_SUGGESTION, entityId: 1 });
            fixture = TestBed.createComponent(ContextSelectionComponent);
            component = fixture.componentInstance;
            await fixture.whenStable();

            expect(entityTitleServiceMock.getTitle).not.toHaveBeenCalled();
            expect(component.activeChip()?.label).toBe('artemisApp.iris.contextSelection.tutorSuggestionContext');
        });

        it('should cancel an old title lookup so it cannot overwrite a newer context', () => {
            const firstLookup = new Subject<string>();
            const secondLookup = new Subject<string>();
            entityTitleServiceMock.getTitle.mockReturnValueOnce(firstLookup).mockReturnValueOnce(secondLookup);

            chatServiceMock.displayContext.set({ mode: ChatServiceMode.LECTURE, entityId: 1 });
            fixture.detectChanges();
            chatServiceMock.displayContext.set({ mode: ChatServiceMode.TEXT_EXERCISE, entityId: 11 });
            fixture.detectChanges();

            firstLookup.next('Stale lecture title');
            expect(component.activeChip()?.label).toBe('');

            secondLookup.next('Current exercise title');
            expect(component.activeChip()?.label).toBe('Current exercise title');
            expect(entityTitleServiceMock.getTitle).toHaveBeenNthCalledWith(1, EntityType.LECTURE, [1]);
            expect(entityTitleServiceMock.getTitle).toHaveBeenNthCalledWith(2, EntityType.EXERCISE, [11]);
        });

        it('should render no chip for the course context', () => {
            expect(component.activeChip()).toBeUndefined();
        });
    });

    describe('supportedExercises', () => {
        beforeEach(() => component.loadContextOptions());

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
        it('should return mode:entityId string when context is set', () => {
            chatServiceMock.displayContext.set({ mode: ChatServiceMode.LECTURE, entityId: 7 });

            expect(component.selectedValue()).toBe(`${ChatServiceMode.LECTURE}:7`);
        });

        it('should return undefined when context is undefined', () => {
            chatServiceMock.displayContext.set(undefined);

            expect(component.selectedValue()).toBeUndefined();
        });
    });

    describe('allGroups', () => {
        beforeEach(() => component.loadContextOptions());

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
            lectureServiceMock.findAllByCourseIdForOverview.mockReturnValue(of([]));
            fixture = TestBed.createComponent(ContextSelectionComponent);
            component = fixture.componentInstance;
            component.loadContextOptions();

            const groups = component.allGroups();
            const lecturesGroup = groups.find((g) => g.label === 'artemisApp.iris.contextSelection.lecturesGroup');
            expect(lecturesGroup).toBeUndefined();
        });

        it('should not include exercises group when there are no supported exercises', () => {
            lectureServiceMock.findAllByCourseIdForOverview.mockReturnValue(of([]));
            exerciseServiceMock.getTitlesForCourse.mockReturnValue(of([{ id: 99, type: ExerciseType.FILE_UPLOAD }]));
            fixture = TestBed.createComponent(ContextSelectionComponent);
            component = fixture.componentInstance;
            component.loadContextOptions();

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
        beforeEach(() => component.loadContextOptions());

        it('should call chatService.stagePendingContext with the correct mode, entityId and label', () => {
            const value = `${ChatServiceMode.TEXT_EXERCISE}:11`;
            component.onSelectionChange(value);

            expect(chatServiceMock.stagePendingContext).toHaveBeenCalledWith(ChatServiceMode.TEXT_EXERCISE, 11, 'Text Ex');
        });

        it('should call stagePendingContext for a lecture option', () => {
            const value = `${ChatServiceMode.LECTURE}:1`;
            component.onSelectionChange(value);

            expect(chatServiceMock.stagePendingContext).toHaveBeenCalledWith(ChatServiceMode.LECTURE, 1, 'Lecture 1');
        });

        it('should not call stagePendingContext when value does not match any option', () => {
            component.onSelectionChange('UNKNOWN_MODE:999');
            expect(chatServiceMock.stagePendingContext).not.toHaveBeenCalled();
        });

        it('should emit contextChanged when a valid option is selected (drives the onboarding flow)', () => {
            const emitSpy = vi.spyOn(component.contextChanged, 'emit');

            component.onSelectionChange(`${ChatServiceMode.LECTURE}:1`);

            expect(emitSpy).toHaveBeenCalledOnce();
        });

        it('should not emit contextChanged for an unknown value', () => {
            const emitSpy = vi.spyOn(component.contextChanged, 'emit');

            component.onSelectionChange('UNKNOWN_MODE:999');

            expect(emitSpy).not.toHaveBeenCalled();
        });
    });

    describe('onChipRemove', () => {
        it('should stage the COURSE context as pending when a chip is removed and a courseId is available', () => {
            const emitSpy = vi.spyOn(component.contextChanged, 'emit');

            component.onChipRemove();

            expect(chatServiceMock.stagePendingContext).toHaveBeenCalledWith(ChatServiceMode.COURSE, courseId);
            expect(emitSpy).toHaveBeenCalledOnce();
        });

        it('should not stage a pending context nor emit contextChanged when courseId is undefined', () => {
            chatServiceMock.getCourseId.mockReturnValue(undefined);
            fixture = TestBed.createComponent(ContextSelectionComponent);
            component = fixture.componentInstance;
            chatServiceMock.stagePendingContext.mockClear();
            const emitSpy = vi.spyOn(component.contextChanged, 'emit');

            component.onChipRemove();

            expect(chatServiceMock.stagePendingContext).not.toHaveBeenCalled();
            expect(emitSpy).not.toHaveBeenCalled();
        });
    });
});
