import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApplicationRef, TemplateRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ExerciseActionButtonComponent } from 'app/shared-ui/components/buttons/exercise-action-button/exercise-action-button.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { Subject, of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClient } from '@angular/common/http';
import { StartPracticeModeButtonComponent } from 'app/course/overview/exercise-details/start-practice-mode-button/start-practice-mode-button.component';

describe('JhiStartPracticeModeButtonComponent', () => {
    let comp: StartPracticeModeButtonComponent;
    let fixture: ComponentFixture<StartPracticeModeButtonComponent>;

    let courseExerciseService: CourseExerciseService;
    let startPracticeStub: ReturnType<typeof vi.spyOn>;
    let alertService: AlertService;
    let alertServiceSuccessStub: ReturnType<typeof vi.spyOn>;
    let alertServiceErrorStub: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                StartPracticeModeButtonComponent,
                MockDirective(NgbPopover),
                MockComponent(ExerciseActionButtonComponent),
                MockPipe(ArtemisTranslatePipe, (key: string) => key),
                MockDirective(FeatureToggleDirective),
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, { provide: AccountService, useClass: MockAccountService }, provideHttpClient()],
        }).compileComponents();

        fixture = TestBed.createComponent(StartPracticeModeButtonComponent);
        comp = fixture.componentInstance;
        courseExerciseService = TestBed.inject(CourseExerciseService);
        alertService = TestBed.inject(AlertService);

        startPracticeStub = vi.spyOn(courseExerciseService, 'startPractice');
        alertServiceSuccessStub = vi.spyOn(alertService, 'success');
        alertServiceErrorStub = vi.spyOn(alertService, 'error');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should reflect the correct participation state for practice mode with no graded participation', async () => {
        const exercise = {
            id: 43,
            type: ExerciseType.PROGRAMMING,
            dueDate: dayjs().subtract(5, 'minutes'),
            studentParticipations: [] as StudentParticipation[],
        } as ProgrammingExercise;
        const inactivePart = { id: 2, initializationState: InitializationState.UNINITIALIZED, testRun: true } as StudentParticipation;
        const initPart = { id: 2, initializationState: InitializationState.INITIALIZED, testRun: true } as StudentParticipation;
        const participationSubject = new Subject<StudentParticipation>();

        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('smallButtons', false);

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        startPracticeStub.mockReturnValue(participationSubject);
        comp.startPractice(false);
        participationSubject.next(inactivePart);

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(alertServiceErrorStub).toHaveBeenCalledOnce();
        expect(alertServiceErrorStub).toHaveBeenCalledWith('artemisApp.exercise.startError');
        expect(startPracticeStub).toHaveBeenCalledOnce();

        fixture.componentRef.setInput('exercise', { ...exercise, studentParticipations: [] });
        participationSubject.next(initPart);

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(alertServiceSuccessStub).toHaveBeenCalledOnce();
        expect(alertServiceSuccessStub).toHaveBeenCalledWith('artemisApp.exercise.personalRepositoryOnline');

        fixture.destroy();
    });

    it('should reflect the correct participation state for practice mode with graded participation', async () => {
        const exercise = {
            id: 43,
            type: ExerciseType.PROGRAMMING,
            dueDate: dayjs().subtract(5, 'minutes'),
            allowOfflineIde: true,
            studentParticipations: [] as StudentParticipation[],
        } as ProgrammingExercise;
        const gradedPart = { id: 1, initializationState: InitializationState.FINISHED, testRun: false } as StudentParticipation;
        const inactivePart = { id: 2, initializationState: InitializationState.UNINITIALIZED, testRun: true } as StudentParticipation;
        const initPart = { id: 2, initializationState: InitializationState.INITIALIZED, testRun: true } as StudentParticipation;
        const participationSubject = new Subject<StudentParticipation>();

        fixture.componentRef.setInput('exercise', { ...exercise, studentParticipations: [gradedPart] });
        fixture.componentRef.setInput('smallButtons', false);

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        startPracticeStub.mockReturnValue(participationSubject);
        comp.startPractice(false);
        participationSubject.next(inactivePart);

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(alertServiceErrorStub).toHaveBeenCalledOnce();
        expect(alertServiceErrorStub).toHaveBeenCalledWith('artemisApp.exercise.startError');
        expect(startPracticeStub).toHaveBeenCalledOnce();

        participationSubject.next(initPart);

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(alertServiceSuccessStub).toHaveBeenCalledOnce();
        expect(alertServiceSuccessStub).toHaveBeenCalledWith('artemisApp.exercise.personalRepositoryClone');

        fixture.destroy();
    });

    it('should not offer the graded participation as baseline for a team exercise', async () => {
        const gradedTeamPart = { id: 1, initializationState: InitializationState.FINISHED, testRun: false } as StudentParticipation;
        const exercise = {
            id: 45,
            type: ExerciseType.PROGRAMMING,
            teamMode: true,
            dueDate: dayjs().subtract(5, 'minutes'),
            studentParticipations: [gradedTeamPart],
        } as ProgrammingExercise;

        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('smallButtons', false);
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        // the graded participation belongs to the team, so the practice repository always starts from the template
        expect(comp.gradedStudentParticipation()).toBeUndefined();

        fixture.componentRef.setInput('exercise', { ...exercise, teamMode: false });
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(comp.gradedStudentParticipation()).toEqual(gradedTeamPart);
    });

    describe('popover of a programming exercise', () => {
        const gradedPart = { id: 1, initializationState: InitializationState.FINISHED, testRun: false } as StudentParticipation;

        /** Renders the content of the popover, which the button opens on click, into a detached view. */
        const renderPopover = async (teamMode: boolean): Promise<HTMLElement> => {
            const exercise = {
                id: 45,
                type: ExerciseType.PROGRAMMING,
                teamMode,
                dueDate: dayjs().subtract(5, 'minutes'),
                studentParticipations: [gradedPart],
            } as ProgrammingExercise;
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('smallButtons', false);
            fixture.changeDetectorRef.detectChanges();
            await fixture.whenStable();
            fixture.changeDetectorRef.detectChanges();

            const popover = fixture.debugElement.query(By.css('button[jhi-exercise-action-button]')).injector.get(NgbPopover);
            const view = (popover.ngbPopover as TemplateRef<unknown>).createEmbeddedView({});
            view.detectChanges();
            return view.rootNodes.find((node: Node) => node.nodeType === Node.ELEMENT_NODE) as HTMLElement;
        };

        const translationKeys = (popover: HTMLElement) => Array.from(popover.querySelectorAll('[jhiTranslate]')).map((element) => element.getAttribute('jhiTranslate'));

        it('should offer the repository of the graded participation as baseline for an individual exercise', async () => {
            const popover = await renderPopover(false);

            expect(translationKeys(popover)).toContain('artemisApp.exerciseActions.practiceMode.repositoryChoice');
            // an individual exercise is not explained as team practice
            expect(translationKeys(popover)).not.toContain('artemisApp.exerciseActions.practiceMode.teamHint');
            expect(popover.querySelector('[data-testid="start-practice-popover-team-hint"]')).toBeNull();
            // one button to start from the template and one to start from the graded repository
            expect(popover.querySelectorAll('button[jhi-exercise-action-button]')).toHaveLength(2);
        });

        it('should only offer the template as baseline for a team exercise, whose graded participation belongs to the team', async () => {
            const popover = await renderPopover(true);

            expect(translationKeys(popover)).not.toContain('artemisApp.exerciseActions.practiceMode.repositoryChoice');
            expect(translationKeys(popover)).toEqual([
                'artemisApp.exerciseActions.practiceMode.title',
                'artemisApp.exerciseActions.practiceMode.explanation',
                'artemisApp.exerciseActions.practiceMode.teamHint',
            ]);
            expect(popover.querySelector('[data-testid="start-practice-popover-team-hint"]')?.getAttribute('jhiTranslate')).toBe(
                'artemisApp.exerciseActions.practiceMode.teamHint',
            );
            expect(popover.querySelectorAll('button[jhi-exercise-action-button]')).toHaveLength(1);
        });

        it('should start the practice mode of a team exercise without the graded participation and report the practice participation', async () => {
            const practicePart = { id: 2, initializationState: InitializationState.INITIALIZED, testRun: true } as StudentParticipation;
            const started: StudentParticipation[] = [];
            comp.practiceModeStarted.subscribe((participation) => started.push(participation));
            const popover = await renderPopover(true);
            startPracticeStub.mockReturnValue(of(practicePart));

            // click the only button of the rendered popover instead of calling the component method, so the click wiring of the template is covered
            popover.querySelector<HTMLButtonElement>('button[jhi-exercise-action-button]')!.click();

            expect(startPracticeStub).toHaveBeenCalledExactlyOnceWith(45, false, comp.exercise());
            expect(started).toEqual([practicePart]);
            expect(alertServiceSuccessStub).toHaveBeenCalledWith('artemisApp.exercise.personalRepositoryOnline');
        });
    });

    describe('hint that practice of a team exercise is individual', () => {
        const TEAM_HINT = 'artemisApp.exerciseActions.practiceMode.teamHint';

        const createExercise = (type: ExerciseType, teamMode?: boolean) =>
            ({ id: 46, type, teamMode, dueDate: dayjs().subtract(5, 'minutes'), studentParticipations: [] as StudentParticipation[] }) as ProgrammingExercise;

        const render = async (type: ExerciseType, teamMode?: boolean) => {
            fixture.componentRef.setInput('exercise', createExercise(type, teamMode));
            fixture.componentRef.setInput('smallButtons', false);
            fixture.changeDetectorRef.detectChanges();
            await fixture.whenStable();
            fixture.changeDetectorRef.detectChanges();
        };

        const startButton = (): HTMLButtonElement => fixture.nativeElement.querySelector('button[jhi-exercise-action-button]');
        const description = (): HTMLElement | null => fixture.nativeElement.querySelector('[data-testid="start-practice-team-hint"]');
        const tooltipBubble = (): HTMLElement | null => document.querySelector('.tumaet-ui-tooltip-bubble');

        /** Hovers the button for longer than the show delay of the tooltip. */
        const hover = () => {
            startButton().dispatchEvent(new MouseEvent('mouseenter'));
            vi.advanceTimersByTime(1000);
            // the bubble is created outside of the fixture, so it renders with the application
            TestBed.inject(ApplicationRef).tick();
        };

        beforeEach(() => {
            vi.useFakeTimers();
        });

        afterEach(() => {
            fixture.destroy();
            vi.runOnlyPendingTimers();
            vi.useRealTimers();
        });

        it.each([ExerciseType.PROGRAMMING, ExerciseType.TEXT, ExerciseType.MODELING])(
            'should explain on the start practice button of a team %s exercise that practice is individual',
            async (type) => {
                await render(type, true);

                expect(comp.isTeamExercise()).toBe(true);
                // exactly one start practice button, which carries the tooltip
                expect(fixture.nativeElement.querySelectorAll('button[jhi-exercise-action-button]')).toHaveLength(1);
                expect(tooltipBubble()).toBeNull();
                hover();
                expect(tooltipBubble()).not.toBeNull();
                expect(tooltipBubble()!.textContent).toContain(TEAM_HINT);
                expect(tooltipBubble()!.getAttribute('role')).toBe('tooltip');
            },
        );

        it.each([ExerciseType.PROGRAMMING, ExerciseType.TEXT])(
            'should describe the start practice button of a team %s exercise permanently for assistive technology',
            async (type) => {
                await render(type, true);

                const hint = description();
                expect(hint).not.toBeNull();
                expect(hint!.textContent?.trim()).toBe(TEAM_HINT);
                expect(hint!.classList).toContain('sr-only');
                expect(hint!.id).toBe(comp.teamHintId);
                expect(startButton().getAttribute('aria-describedby')).toBe(hint!.id);
                // the tooltip does not announce the same text a second time
                hover();
                expect(startButton().getAttribute('aria-describedby')).toBe(hint!.id);
            },
        );

        it.each([
            [ExerciseType.PROGRAMMING, false],
            [ExerciseType.PROGRAMMING, undefined],
            [ExerciseType.TEXT, false],
            [ExerciseType.TEXT, undefined],
            [ExerciseType.MODELING, false],
        ])('should not show the hint on the start practice button of an individual %s exercise with teamMode %s', async (type, teamMode) => {
            await render(type, teamMode);

            expect(comp.isTeamExercise()).toBe(false);
            expect(fixture.nativeElement.querySelectorAll('button[jhi-exercise-action-button]')).toHaveLength(1);
            expect(description()).toBeNull();
            expect(startButton().hasAttribute('aria-describedby')).toBe(false);
            hover();
            expect(tooltipBubble()).toBeNull();
            expect(fixture.nativeElement.textContent).not.toContain(TEAM_HINT);
        });

        it('should keep the description of a team programming exercise after the popover closed, because it removes aria-describedby of its trigger', async () => {
            await render(ExerciseType.PROGRAMMING, true);
            const popover = fixture.debugElement.query(By.css('button[jhi-exercise-action-button]')).injector.get(NgbPopover);

            // the popover points the trigger to itself while it is open and removes the attribute on close, before it reports hidden
            startButton().setAttribute('aria-describedby', 'ngb-popover-0');
            expect(startButton().getAttribute('aria-describedby')).toBe('ngb-popover-0');
            startButton().removeAttribute('aria-describedby');
            popover.hidden.emit();

            expect(startButton().getAttribute('aria-describedby')).toBe(comp.teamHintId);
            expect(startButton().getAttribute('aria-describedby')).toBe(description()!.id);
        });

        it('should not describe the trigger of an individual programming exercise after the popover closed', async () => {
            await render(ExerciseType.PROGRAMMING, false);
            const popover = fixture.debugElement.query(By.css('button[jhi-exercise-action-button]')).injector.get(NgbPopover);

            startButton().removeAttribute('aria-describedby');
            popover.hidden.emit();

            expect(startButton().hasAttribute('aria-describedby')).toBe(false);
            expect(description()).toBeNull();
        });

        it('should add and remove the hint when the exercise switches between individual and team mode', async () => {
            await render(ExerciseType.TEXT, false);
            expect(description()).toBeNull();

            fixture.componentRef.setInput('exercise', createExercise(ExerciseType.TEXT, true));
            fixture.changeDetectorRef.detectChanges();
            expect(description()?.textContent?.trim()).toBe(TEAM_HINT);
            expect(startButton().getAttribute('aria-describedby')).toBe(comp.teamHintId);

            fixture.componentRef.setInput('exercise', createExercise(ExerciseType.TEXT, false));
            fixture.changeDetectorRef.detectChanges();
            expect(description()).toBeNull();
            expect(startButton().hasAttribute('aria-describedby')).toBe(false);
        });

        it('should give every button its own description id, because several buttons of one exercise can be on screen', async () => {
            const other = TestBed.createComponent(StartPracticeModeButtonComponent);
            other.componentRef.setInput('exercise', createExercise(ExerciseType.TEXT, true));
            other.componentRef.setInput('smallButtons', true);
            other.changeDetectorRef.detectChanges();
            await render(ExerciseType.TEXT, true);

            expect(other.componentInstance.teamHintId).not.toBe(comp.teamHintId);
            other.destroy();
        });
    });

    it('should ignore a second start while starting the practice mode is in flight', () => {
        const exercise = { id: 44, type: ExerciseType.PROGRAMMING, studentParticipations: [] as StudentParticipation[] } as ProgrammingExercise;
        const participationSubject = new Subject<StudentParticipation>();
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('smallButtons', false);
        startPracticeStub.mockReturnValue(participationSubject);

        comp.startPractice(false);
        comp.startPractice(true);

        expect(startPracticeStub).toHaveBeenCalledOnce();
        expect(comp.startingPracticeMode()).toBe(true);

        participationSubject.error(new Error('failed'));
        expect(comp.startingPracticeMode()).toBe(false);

        comp.startPractice(true);
        expect(startPracticeStub).toHaveBeenCalledTimes(2);
    });
});
