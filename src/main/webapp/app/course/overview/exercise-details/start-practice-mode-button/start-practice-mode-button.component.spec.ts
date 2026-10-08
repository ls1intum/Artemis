import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TemplateRef } from '@angular/core';
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
                MockPipe(ArtemisTranslatePipe),
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
            // one button to start from the template and one to start from the graded repository
            expect(popover.querySelectorAll('button[jhi-exercise-action-button]')).toHaveLength(2);
        });

        it('should only offer the template as baseline for a team exercise, whose graded participation belongs to the team', async () => {
            const popover = await renderPopover(true);

            expect(translationKeys(popover)).not.toContain('artemisApp.exerciseActions.practiceMode.repositoryChoice');
            expect(translationKeys(popover)).toEqual(['artemisApp.exerciseActions.practiceMode.title', 'artemisApp.exerciseActions.practiceMode.explanation']);
            expect(popover.querySelectorAll('button[jhi-exercise-action-button]')).toHaveLength(1);
        });

        it('should start the practice mode of a team exercise without the graded participation and report the practice participation', async () => {
            const practicePart = { id: 2, initializationState: InitializationState.INITIALIZED, testRun: true } as StudentParticipation;
            const started: StudentParticipation[] = [];
            comp.practiceModeStarted.subscribe((participation) => started.push(participation));
            await renderPopover(true);
            startPracticeStub.mockReturnValue(of(practicePart));

            comp.startPractice(false);

            expect(startPracticeStub).toHaveBeenCalledExactlyOnceWith(45, false, comp.exercise());
            expect(started).toEqual([practicePart]);
            expect(alertServiceSuccessStub).toHaveBeenCalledWith('artemisApp.exercise.personalRepositoryOnline');
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
