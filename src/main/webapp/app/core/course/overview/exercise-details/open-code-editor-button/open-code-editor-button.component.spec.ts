import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { OpenCodeEditorButtonComponent } from 'app/core/course/overview/exercise-details/open-code-editor-button/open-code-editor-button.component';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseActionButtonComponent } from 'app/shared/components/buttons/exercise-action-button/exercise-action-button.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import dayjs from 'dayjs/esm';

describe('OpenCodeEditorButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let component: OpenCodeEditorButtonComponent;
    let fixture: ComponentFixture<OpenCodeEditorButtonComponent>;
    let debugElement: DebugElement;
    let participationService: ParticipationService;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [
                OpenCodeEditorButtonComponent,
                MockComponent(ExerciseActionButtonComponent),
                MockDirective(FeatureToggleDirective),
                MockDirective(RouterLink),
                MockDirective(NgbPopover),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                provideHttpClient(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(OpenCodeEditorButtonComponent);
        component = fixture.componentInstance;
        debugElement = fixture.debugElement;
        participationService = TestBed.inject(ParticipationService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function createExercise(dueDate?: dayjs.Dayjs): Exercise {
        return {
            id: 1,
            type: ExerciseType.PROGRAMMING,
            dueDate: dueDate,
        } as Exercise;
    }

    function createParticipation(id: number, testRun = false): ProgrammingExerciseStudentParticipation {
        return {
            id: id,
            testRun: testRun,
        } as ProgrammingExerciseStudentParticipation;
    }

    function setupComponent(
        participations: ProgrammingExerciseStudentParticipation[],
        exercise: Exercise,
        courseAndExerciseNavigationUrlSegment: any[] = ['courses', '1', 'exercises', '1', 'code-editor'],
    ) {
        fixture.componentRef.setInput('participations', participations);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('courseAndExerciseNavigationUrlSegment', courseAndExerciseNavigationUrlSegment);
        fixture.detectChanges();
    }

    it('should create component', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with correct values', () => {
        expect(component.loading()).toBe(false);
        expect(component.hideLabelMobile()).toBe(false);
    });

    it('should compute courseAndExerciseNavigationUrl from segments', async () => {
        vi.useFakeTimers();
        const participation = createParticipation(1);
        const exercise = createExercise();
        const urlSegments = ['courses', '1', 'exercises', '1', 'code-editor'];

        vi.spyOn(participationService, 'shouldPreferPractice').mockReturnValue(false);
        vi.spyOn(participationService, 'getSpecificStudentParticipation').mockReturnValue(participation);

        setupComponent([participation], exercise, urlSegments);
        await vi.advanceTimersByTimeAsync(0);

        expect(component.courseAndExerciseNavigationUrl()).toBe('courses/1/exercises/1/code-editor');
    });

    it('should set activeParticipation based on shouldPreferPractice', async () => {
        vi.useFakeTimers();
        const gradedParticipation = createParticipation(1, false);
        const practiceParticipation = createParticipation(2, true);
        const exercise = createExercise(dayjs().subtract(1, 'day')); // Past due date

        vi.spyOn(participationService, 'shouldPreferPractice').mockReturnValue(true);
        vi.spyOn(participationService, 'getSpecificStudentParticipation').mockReturnValue(practiceParticipation);

        setupComponent([gradedParticipation, practiceParticipation], exercise);
        await vi.advanceTimersByTimeAsync(0);

        expect(participationService.shouldPreferPractice).toHaveBeenCalledWith(exercise);
        expect(component.activeParticipation()).toBe(practiceParticipation);
    });

    it('should fall back to first participation when getSpecificStudentParticipation returns undefined', async () => {
        vi.useFakeTimers();
        const participation1 = createParticipation(1, false);
        const participation2 = createParticipation(2, true);
        const exercise = createExercise();

        vi.spyOn(participationService, 'shouldPreferPractice').mockReturnValue(false);
        vi.spyOn(participationService, 'getSpecificStudentParticipation').mockReturnValue(undefined);

        setupComponent([participation1, participation2], exercise);
        await vi.advanceTimersByTimeAsync(0);

        expect(component.activeParticipation()).toBe(participation1);
    });

    it('should switchPracticeMode and toggle isPracticeMode', async () => {
        vi.useFakeTimers();
        const gradedParticipation = createParticipation(1, false);
        const practiceParticipation = createParticipation(2, true);
        const exercise = createExercise();

        vi.spyOn(participationService, 'shouldPreferPractice').mockReturnValue(false);
        vi.spyOn(participationService, 'getSpecificStudentParticipation').mockReturnValue(gradedParticipation);

        setupComponent([gradedParticipation, practiceParticipation], exercise);
        await vi.advanceTimersByTimeAsync(0);

        // Initial state: isPracticeMode should be true
        expect(component.isPracticeMode()).toBe(true);

        // Now switch practice mode - should toggle to false
        vi.spyOn(participationService, 'getSpecificStudentParticipation').mockReturnValue(gradedParticipation);
        component.switchPracticeMode();

        expect(component.isPracticeMode()).toBe(false);
        expect(participationService.getSpecificStudentParticipation).toHaveBeenCalledWith([gradedParticipation, practiceParticipation], false);
    });

    it('should switch back to practice mode', async () => {
        vi.useFakeTimers();
        const gradedParticipation = createParticipation(1, false);
        const practiceParticipation = createParticipation(2, true);
        const exercise = createExercise();

        vi.spyOn(participationService, 'shouldPreferPractice').mockReturnValue(false);
        vi.spyOn(participationService, 'getSpecificStudentParticipation').mockReturnValue(gradedParticipation);

        setupComponent([gradedParticipation, practiceParticipation], exercise);
        await vi.advanceTimersByTimeAsync(0);

        // First switch: isPracticeMode = true -> false
        component.switchPracticeMode();
        expect(component.isPracticeMode()).toBe(false);

        // Second switch: isPracticeMode = false -> true
        vi.spyOn(participationService, 'getSpecificStudentParticipation').mockReturnValue(practiceParticipation);
        component.switchPracticeMode();

        expect(component.isPracticeMode()).toBe(true);
        expect(participationService.getSpecificStudentParticipation).toHaveBeenCalledWith([gradedParticipation, practiceParticipation], true);
        expect(component.activeParticipation()).toBe(practiceParticipation);
    });

    it('should render button when there is exactly one participation', async () => {
        vi.useFakeTimers();
        const participation = createParticipation(1);
        const exercise = createExercise();

        vi.spyOn(participationService, 'shouldPreferPractice').mockReturnValue(false);
        vi.spyOn(participationService, 'getSpecificStudentParticipation').mockReturnValue(participation);

        setupComponent([participation], exercise);
        await vi.advanceTimersByTimeAsync(0);
        fixture.detectChanges();

        const buttons = debugElement.queryAll(By.css('button[jhi-exercise-action-button]'));
        expect(buttons.length).toBe(1);
    });

    it('should render button with popover when there are multiple participations', async () => {
        vi.useFakeTimers();
        const gradedParticipation = createParticipation(1, false);
        const practiceParticipation = createParticipation(2, true);
        const exercise = createExercise();

        vi.spyOn(participationService, 'shouldPreferPractice').mockReturnValue(false);
        vi.spyOn(participationService, 'getSpecificStudentParticipation').mockReturnValue(gradedParticipation);

        setupComponent([gradedParticipation, practiceParticipation], exercise);
        await vi.advanceTimersByTimeAsync(0);
        fixture.detectChanges();

        // Should have a button with popover
        const buttons = debugElement.queryAll(By.css('button[jhi-exercise-action-button]'));
        expect(buttons.length).toBe(1);

        // Check if NgbPopover directive is applied
        const popoverButton = debugElement.query(By.directive(NgbPopover));
        expect(popoverButton).toBeTruthy();
    });

    it('should not render any button when participations is empty', async () => {
        vi.useFakeTimers();
        const exercise = createExercise();

        setupComponent([], exercise);
        await vi.advanceTimersByTimeAsync(0);
        fixture.detectChanges();

        const buttons = debugElement.queryAll(By.css('button[jhi-exercise-action-button]'));
        expect(buttons.length).toBe(0);
    });

    it('should pass loading input to button component', async () => {
        vi.useFakeTimers();
        const participation = createParticipation(1);
        const exercise = createExercise();

        vi.spyOn(participationService, 'shouldPreferPractice').mockReturnValue(false);
        vi.spyOn(participationService, 'getSpecificStudentParticipation').mockReturnValue(participation);

        fixture.componentRef.setInput('participations', [participation]);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('courseAndExerciseNavigationUrlSegment', ['courses', '1', 'exercises', '1', 'code-editor']);
        fixture.componentRef.setInput('loading', true);

        await vi.advanceTimersByTimeAsync(0);
        fixture.detectChanges();

        expect(component.loading()).toBe(true);
    });

    it('should pass smallButtons input to button component', async () => {
        vi.useFakeTimers();
        const participation = createParticipation(1);
        const exercise = createExercise();

        vi.spyOn(participationService, 'shouldPreferPractice').mockReturnValue(false);
        vi.spyOn(participationService, 'getSpecificStudentParticipation').mockReturnValue(participation);

        fixture.componentRef.setInput('participations', [participation]);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('courseAndExerciseNavigationUrlSegment', ['courses', '1', 'exercises', '1', 'code-editor']);
        fixture.componentRef.setInput('smallButtons', true);

        await vi.advanceTimersByTimeAsync(0);
        fixture.detectChanges();

        expect(component.smallButtons()).toBe(true);
    });

    it('should pass hideLabelMobile input to button component', async () => {
        vi.useFakeTimers();
        const participation = createParticipation(1);
        const exercise = createExercise();

        vi.spyOn(participationService, 'shouldPreferPractice').mockReturnValue(false);
        vi.spyOn(participationService, 'getSpecificStudentParticipation').mockReturnValue(participation);

        fixture.componentRef.setInput('participations', [participation]);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('courseAndExerciseNavigationUrlSegment', ['courses', '1', 'exercises', '1', 'code-editor']);
        fixture.componentRef.setInput('hideLabelMobile', true);

        await vi.advanceTimersByTimeAsync(0);
        fixture.detectChanges();

        expect(component.hideLabelMobile()).toBe(true);
    });

    it('should have faFolderOpen icon defined', () => {
        expect(component.faFolderOpen).toBeDefined();
    });

    it('should have FeatureToggle enum available', () => {
        expect(component.FeatureToggle).toBeDefined();
    });
});
