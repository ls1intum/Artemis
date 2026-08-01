import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { BehaviorSubject } from 'rxjs';
import { ExamNavigationBarComponent } from 'app/exam/manage/student-exams/student-exam-timeline/exam-navigation-bar/exam-navigation-bar.component';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { DialogService } from 'primeng/dynamicdialog';
import { MockProvider } from 'ng-mocks';
import { LayoutService } from 'app/foundation/breakpoints/layout.service';
import { CustomBreakpointNames } from 'app/foundation/breakpoints/breakpoints.service';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('Exam Navigation Bar Component', () => {
    let fixture: ComponentFixture<ExamNavigationBarComponent>;
    let comp: ExamNavigationBarComponent;

    // Recreated per test: a BehaviorSubject shared across tests would replay the previous test's breakpoint into
    // the next component's ngOnInit, silently making itemsVisiblePerSide order-dependent.
    let breakpointsSubject: BehaviorSubject<string[]>;
    let activeBreakpoints: string[] = [];
    const mockLayoutService = {
        subscribeToLayoutChanges: () => breakpointsSubject.asObservable(),
        isBreakpointActive: (breakpointName: string) => activeBreakpoints.includes(breakpointName),
    };

    /** Emit a breakpoint change, mirroring how the real service keeps `activeBreakpoints` in sync with what it emits. */
    function activateBreakpoint(breakpointName: string): void {
        activeBreakpoints = [breakpointName];
        breakpointsSubject.next(activeBreakpoints);
    }

    let exercises: Exercise[];

    beforeEach(() => {
        activeBreakpoints = [];
        breakpointsSubject = new BehaviorSubject<string[]>([]);
        TestBed.configureTestingModule({
            providers: [
                { provide: LayoutService, useValue: mockLayoutService },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(DialogService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamNavigationBarComponent);
        comp = fixture.componentInstance;

        exercises = [
            {
                id: 0,
                type: ExerciseType.PROGRAMMING,
                studentParticipations: [
                    {
                        submissions: [{ id: 3, isSynced: true } as Submission],
                    } as StudentParticipation,
                ],
            } as Exercise,
            { id: 1, type: ExerciseType.TEXT } as Exercise,
            { id: 2, type: ExerciseType.MODELING } as Exercise,
        ];

        fixture.componentRef.setInput('exercises', exercises);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(async () => {
        fixture.detectChanges();
        await Promise.resolve();
    });

    describe('changePage', () => {
        it('should emit the exercise to navigate to', () => {
            const emitSpy = vi.spyOn(comp.onPageChanged, 'emit');

            comp.changePage(1);

            expect(emitSpy).toHaveBeenCalledExactlyOnceWith({ exercise: exercises[1], submission: undefined });
        });

        it('should forward the submission to display, which is how the timeline selects a point in time', () => {
            const emitSpy = vi.spyOn(comp.onPageChanged, 'emit');
            const submission = { id: 42 } as ProgrammingSubmission;

            comp.changePage(2, submission);

            expect(emitSpy).toHaveBeenCalledExactlyOnceWith({ exercise: exercises[2], submission });
        });

        it('should ignore an index beyond the last exercise', () => {
            const emitSpy = vi.spyOn(comp.onPageChanged, 'emit');

            comp.changePage(exercises.length);

            expect(emitSpy).not.toHaveBeenCalled();
        });

        it('should ignore a negative index', () => {
            const emitSpy = vi.spyOn(comp.onPageChanged, 'emit');

            comp.changePage(-1);

            expect(emitSpy).not.toHaveBeenCalled();
        });
    });

    describe('getExerciseButtonStatus', () => {
        it('should mark the exercise currently being viewed as active', () => {
            fixture.componentRef.setInput('exerciseIndex', 1);

            expect(comp.getExerciseButtonStatus(1)).toBe('synced active');
        });

        it('should mark every other exercise as synced, since all submissions in the timeline are already saved', () => {
            fixture.componentRef.setInput('exerciseIndex', 1);

            expect(comp.getExerciseButtonStatus(0)).toBe('synced');
            expect(comp.getExerciseButtonStatus(2)).toBe('synced');
        });
    });

    describe('visible exercise buttons', () => {
        it.each([
            { breakpoint: CustomBreakpointNames.extraLarge, expected: ExamNavigationBarComponent.itemsVisiblePerSideDefault },
            { breakpoint: CustomBreakpointNames.large, expected: 3 },
            { breakpoint: CustomBreakpointNames.medium, expected: 1 },
            { breakpoint: CustomBreakpointNames.small, expected: 0 },
        ])('should show $expected exercises per side on the $breakpoint breakpoint', ({ breakpoint, expected }) => {
            activateBreakpoint(breakpoint);

            expect(comp.itemsVisiblePerSide()).toBe(expected);
        });
    });
});
