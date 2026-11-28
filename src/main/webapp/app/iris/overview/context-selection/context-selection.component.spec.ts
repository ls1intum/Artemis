import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective } from 'ng-mocks';
import { WritableSignal, signal } from '@angular/core';
import { ContextSelectionComponent } from './context-selection.component';
import { ChatServiceMode, IrisChatService, SessionContext } from 'app/iris/overview/services/iris-chat.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
import { Subject, of, throwError } from 'rxjs';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { By } from '@angular/platform-browser';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular/tooltip';

describe('ContextSelectionComponent', () => {
    let component: ContextSelectionComponent;
    let fixture: ComponentFixture<ContextSelectionComponent>;

    let chatServiceMock: {
        getCourseId: ReturnType<typeof vi.fn>;
        displayContext: WritableSignal<SessionContext | undefined>;
        stagePendingContext: ReturnType<typeof vi.fn>;
    };
    let lectureServiceMock: { findAllByCourseIdForOverview: ReturnType<typeof vi.fn> };
    let exerciseServiceMock: { getTitlesForAccessibleCourse: ReturnType<typeof vi.fn> };
    let entityTitleServiceMock: { getTitle: ReturnType<typeof vi.fn> };

    const courseId = 42;
    const accessibleNames: Record<string, string> = {
        'artemisApp.iris.contextSelection.label': 'Select chat context',
        'artemisApp.iris.contextSelection.searchLabel': 'Search lectures and exercises',
        'artemisApp.iris.contextSelection.removeContext': 'Remove chat context',
    };

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
        exerciseServiceMock = { getTitlesForAccessibleCourse: vi.fn().mockReturnValue(of(exercises)) };
        entityTitleServiceMock = { getTitle: vi.fn().mockReturnValue(of('Resolved Title')) };

        await TestBed.configureTestingModule({
            imports: [ContextSelectionComponent, MockDirective(TranslateDirective)],
            providers: [
                { provide: IrisChatService, useValue: chatServiceMock },
                { provide: LectureService, useValue: lectureServiceMock },
                { provide: ExerciseService, useValue: exerciseServiceMock },
                { provide: EntityTitleService, useValue: entityTitleServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        vi.spyOn(TestBed.inject(TranslateService), 'instant').mockImplementation((key) => accessibleNames[key.toString()] ?? key.toString());
        fixture = TestBed.createComponent(ContextSelectionComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('picker controls', () => {
        const trigger = (): HTMLButtonElement => fixture.debugElement.query(By.css('[data-testid="iris-context-trigger"]')).nativeElement;
        const panel = (): HTMLElement | null => document.querySelector('[data-testid="iris-context-overlay"]');
        const optionLabels = (): string[] => Array.from(document.querySelectorAll('[data-testid="iris-context-option"]')).map((option) => option.textContent!.trim());
        const hint = () => fixture.debugElement.query(By.directive(TumAetUiTooltipDirective)).injector.get(TumAetUiTooltipDirective).content();

        async function openPicker(): Promise<void> {
            trigger().click();
            await fixture.whenStable();
        }

        function search(term: string): void {
            const input = document.querySelector<HTMLInputElement>('[data-testid="iris-context-overlay"] input[type="search"]')!;
            input.value = term;
            input.dispatchEvent(new Event('input', { bubbles: true }));
        }

        it('should name the trigger button and keep the picker closed', () => {
            expect(trigger().getAttribute('aria-label')).toBe('Select chat context');
            expect(trigger().disabled).toBe(false);
            expect(panel()).toBeNull();
        });

        it('should open by click with a named search input and both context groups', async () => {
            await openPicker();

            expect(panel()).not.toBeNull();
            expect(document.querySelector('[data-testid="iris-context-overlay"] input[type="search"]')!.getAttribute('aria-label')).toBe(
                'artemisApp.iris.contextSelection.searchLabel',
            );
            expect(Array.from(document.querySelectorAll('[data-testid="iris-context-overlay"] [role="group"]')).map((group) => group.getAttribute('aria-label'))).toEqual([
                'artemisApp.iris.contextSelection.lecturesGroup',
                'artemisApp.iris.contextSelection.exercisesGroup',
            ]);
            expect(optionLabels()).toEqual(['Lecture 1', 'Lecture 2', 'Programming Ex', 'Text Ex']);
            expect(lectureServiceMock.findAllByCourseIdForOverview).toHaveBeenCalledExactlyOnceWith(courseId);
            expect(hint()).toBeUndefined();
        });

        it('should restore the hint and clear the search term when the picker closes', async () => {
            await openPicker();
            search('Text');
            await fixture.whenStable();
            expect(optionLabels()).toEqual(['Text Ex']);

            document.querySelector('[data-testid="iris-context-overlay"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
            await fixture.whenStable();
            expect(panel()).toBeNull();

            expect(hint()).toBe('artemisApp.iris.contextSelection.addContextTooltip');
            expect(component['filterTerm']()).toBe('');
        });

        it('should filter the options by label and drop a group without matches', async () => {
            await openPicker();
            search('lecture 2');
            await fixture.whenStable();

            expect(optionLabels()).toEqual(['Lecture 2']);
            expect(document.querySelectorAll('[data-testid="iris-context-overlay"] [role="group"]')).toHaveLength(1);
        });

        it('should show a message when nothing matches the search term', async () => {
            await openPicker();
            search('does not exist');
            await fixture.whenStable();

            expect(optionLabels()).toEqual([]);
            expect(document.querySelector('[data-testid="iris-context-overlay"]')!.textContent).toContain('artemisApp.iris.contextSelection.noResults');
        });

        it('should say that the options are loading instead of reporting no results while they load', async () => {
            const pendingLectures = new Subject<typeof lectures>();
            lectureServiceMock.findAllByCourseIdForOverview.mockReturnValue(pendingLectures);
            const overlay = () => document.querySelector('[data-testid="iris-context-overlay"]')!;

            await openPicker();
            expect(overlay().querySelector('[data-testid="iris-context-loading"]')).not.toBeNull();
            expect(overlay().textContent).not.toContain('artemisApp.iris.contextSelection.noResults');

            pendingLectures.next(lectures);
            pendingLectures.complete();
            await fixture.whenStable();
            expect(overlay().querySelector('[data-testid="iris-context-loading"]')).toBeNull();
            expect(optionLabels()).toEqual(['Lecture 1', 'Lecture 2', 'Programming Ex', 'Text Ex']);
        });

        it('should report no results instead of loading once loading the options failed', async () => {
            lectureServiceMock.findAllByCourseIdForOverview.mockReturnValue(throwError(() => new Error('failed')));

            await openPicker();

            const overlay = document.querySelector('[data-testid="iris-context-overlay"]')!;
            expect(overlay.querySelector('[data-testid="iris-context-loading"]')).toBeNull();
            expect(overlay.textContent).toContain('artemisApp.iris.contextSelection.noResults');
        });

        it('should select a filtered option, stage it and close the picker', async () => {
            const contextChangedSpy = vi.spyOn(component.contextChanged, 'emit');
            await openPicker();
            search('Text');
            await fixture.whenStable();

            document.querySelector<HTMLButtonElement>('[data-testid="iris-context-option"]')!.click();
            await fixture.whenStable();

            expect(chatServiceMock.stagePendingContext).toHaveBeenCalledExactlyOnceWith(ChatServiceMode.TEXT_EXERCISE, 11, 'Text Ex');
            expect(contextChangedSpy).toHaveBeenCalledOnce();
            expect(panel()).toBeNull();
        });

        it('should mark the option of the current context', async () => {
            chatServiceMock.displayContext.set({ mode: ChatServiceMode.LECTURE, entityId: 1, entityName: 'Lecture 1' });
            await fixture.whenStable();
            await openPicker();

            const current = Array.from(document.querySelectorAll('[data-testid="iris-context-option"][aria-current="true"]')).map((option) => option.textContent!.trim());
            expect(current).toEqual(['Lecture 1']);
        });

        it('should name the chip removal control and return to the course when it is clicked', async () => {
            chatServiceMock.displayContext.set({ mode: ChatServiceMode.LECTURE, entityId: 1, entityName: 'Lecture 1' });
            await fixture.whenStable();

            const removeButton: HTMLButtonElement = fixture.debugElement.query(By.css('tumaet-ui-chip button')).nativeElement;
            expect(removeButton.getAttribute('aria-label')).toBe('Remove chat context');
            removeButton.click();

            expect(chatServiceMock.stagePendingContext).toHaveBeenCalledExactlyOnceWith(ChatServiceMode.COURSE, courseId);
        });

        it('should disable the trigger and hide chip removal while the picker is disabled', async () => {
            chatServiceMock.displayContext.set({ mode: ChatServiceMode.LECTURE, entityId: 1, entityName: 'Lecture 1' });
            fixture.componentRef.setInput('disabled', true);
            await fixture.whenStable();
            trigger().click();
            await fixture.whenStable();

            expect(trigger().disabled).toBe(true);
            expect(panel()).toBeNull();
            expect(fixture.debugElement.query(By.css('tumaet-ui-chip button'))).toBeNull();
            expect(lectureServiceMock.findAllByCourseIdForOverview).not.toHaveBeenCalled();
        });
    });

    describe('data loading', () => {
        it('should not load any options until the picker is opened', () => {
            expect(lectureServiceMock.findAllByCourseIdForOverview).not.toHaveBeenCalled();
            expect(exerciseServiceMock.getTitlesForAccessibleCourse).not.toHaveBeenCalled();
            expect(component.lectures()).toHaveLength(0);
            expect(component.exercises()).toHaveLength(0);
        });

        it('should load lectures and exercises when the picker is opened', () => {
            component.loadContextOptions();

            expect(lectureServiceMock.findAllByCourseIdForOverview).toHaveBeenCalledExactlyOnceWith(courseId);
            expect(exerciseServiceMock.getTitlesForAccessibleCourse).toHaveBeenCalledExactlyOnceWith(courseId);
            expect(component.lectures()).toHaveLength(2);
            expect(component.exercises()).toHaveLength(3);
        });

        it('should preload the options when the pointer reaches the trigger, so the panel opens at its final height', () => {
            fixture.debugElement.query(By.directive(TumAetUiTooltipDirective)).nativeElement.dispatchEvent(new Event('pointerenter'));

            expect(lectureServiceMock.findAllByCourseIdForOverview).toHaveBeenCalledExactlyOnceWith(courseId);
            expect(exerciseServiceMock.getTitlesForAccessibleCourse).toHaveBeenCalledExactlyOnceWith(courseId);
        });

        it('should load the options only once per course', () => {
            component.loadContextOptions();
            component.loadContextOptions();

            expect(lectureServiceMock.findAllByCourseIdForOverview).toHaveBeenCalledOnce();
            expect(exerciseServiceMock.getTitlesForAccessibleCourse).toHaveBeenCalledOnce();
        });

        it('should leave the options empty when loading fails', () => {
            lectureServiceMock.findAllByCourseIdForOverview.mockReturnValue(throwError(() => new Error('network')));
            exerciseServiceMock.getTitlesForAccessibleCourse.mockReturnValue(throwError(() => new Error('network')));

            component.loadContextOptions();

            expect(component.lectures()).toHaveLength(0);
            expect(component.exercises()).toHaveLength(0);
        });

        it.each(['lectures', 'exercises'] as const)('should retry both option requests after loading %s fails', (failedRequest) => {
            if (failedRequest === 'lectures') {
                lectureServiceMock.findAllByCourseIdForOverview.mockReturnValue(throwError(() => new Error('lecture network')));
            } else {
                exerciseServiceMock.getTitlesForAccessibleCourse.mockReturnValue(throwError(() => new Error('exercise network')));
            }

            component.loadContextOptions();

            expect(component.lectures()).toEqual([]);
            expect(component.exercises()).toEqual([]);
            lectureServiceMock.findAllByCourseIdForOverview.mockReturnValue(of(lectures));
            exerciseServiceMock.getTitlesForAccessibleCourse.mockReturnValue(of(exercises));

            component.loadContextOptions();

            expect(lectureServiceMock.findAllByCourseIdForOverview).toHaveBeenCalledTimes(2);
            expect(exerciseServiceMock.getTitlesForAccessibleCourse).toHaveBeenCalledTimes(2);
            expect(component.lectures()).toEqual(lectures);
            expect(component.exercises()).toEqual(exercises);
        });

        it('should not load anything when no course is known', () => {
            chatServiceMock.getCourseId.mockReturnValue(undefined);
            fixture = TestBed.createComponent(ContextSelectionComponent);
            component = fixture.componentInstance;

            component.loadContextOptions();

            expect(lectureServiceMock.findAllByCourseIdForOverview).not.toHaveBeenCalled();
            expect(exerciseServiceMock.getTitlesForAccessibleCourse).not.toHaveBeenCalled();
        });
    });

    describe('add context hint', () => {
        const hint = () => fixture.debugElement.query(By.directive(TumAetUiTooltipDirective)).injector.get(TumAetUiTooltipDirective).content();

        it('should hide the hint while the picker is open and load the options on opening', async () => {
            await fixture.whenStable();
            expect(hint()).toBe('artemisApp.iris.contextSelection.addContextTooltip');

            component['onPickerOpenChange'](true);
            await fixture.whenStable();
            expect(hint()).toBeUndefined();
            expect(lectureServiceMock.findAllByCourseIdForOverview).toHaveBeenCalledExactlyOnceWith(courseId);

            component['onPickerOpenChange'](false);
            await fixture.whenStable();
            expect(hint()).toBe('artemisApp.iris.contextSelection.addContextTooltip');
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
            exerciseServiceMock.getTitlesForAccessibleCourse.mockReturnValue(of([{ id: 99, type: ExerciseType.FILE_UPLOAD }]));
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
