import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { MockProvider } from 'ng-mocks';
import { TutorLeaderboardElement } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.model';
import { TutorLeaderboardComponent } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.component';
import { SortService } from 'app/shared/service/sort.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('TutorLeaderboardComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: TutorLeaderboardComponent;
    let fixture: ComponentFixture<TutorLeaderboardComponent>;
    let sortService: SortService;
    let sortByPropertySpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [MockProvider(AccountService), { provide: Router, useClass: MockRouter }, { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(TutorLeaderboardComponent);
        comp = fixture.componentInstance;
        sortService = TestBed.inject(SortService);
        sortByPropertySpy = vi.spyOn(sortService, 'sortByProperty').mockImplementation(() => []);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('derived state', () => {
        it('uses the input course directly when no exercise is provided', () => {
            const course = { isAtLeastInstructor: true } as Course;
            fixture.componentRef.setInput('course', course);
            expect(comp.course()).toBe(course);
            expect(comp.exercise()).toBeUndefined();
            expect(comp.isExerciseDashboard()).toBe(false);
        });

        it('derives the course from exercise.course when present', () => {
            const course = { isAtLeastInstructor: true } as Course;
            const exercise = { course } as Exercise;
            fixture.componentRef.setInput('exercise', exercise);
            expect(comp.course()).toBe(course);
            expect(comp.exercise()).toBe(exercise);
            expect(comp.isExerciseDashboard()).toBe(true);
        });

        it('derives the course from exercise.exerciseGroup.exam.course when present', () => {
            const course = {} as Course;
            const exam = { course } as Exam;
            const exerciseGroup = { exam } as ExerciseGroup;
            const exercise = { exerciseGroup } as Exercise;
            fixture.componentRef.setInput('exercise', exercise);
            expect(comp.course()).toBe(course);
            expect(comp.exercise()).toBe(exercise);
            expect(comp.isExerciseDashboard()).toBe(true);
        });

        it('reflects the exam input through isExamMode', () => {
            expect(comp.isExamMode()).toBe(false);

            const exam = {} as Exam;
            const course = { exams: [exam], isAtLeastInstructor: true } as Course;
            fixture.componentRef.setInput('exam', exam);
            fixture.componentRef.setInput('course', course);
            expect(comp.isExamMode()).toBe(true);
        });

        it('sorts the rows when constructed', () => {
            fixture.detectChanges();
            expect(sortByPropertySpy).toHaveBeenCalled();
        });
    });

    describe('tutorData', () => {
        it('should fill the table with elements', () => {
            const element = new TutorLeaderboardElement();
            fixture.componentRef.setInput('tutorsData', [element]);
            fixture.detectChanges();
            const table: HTMLTableElement = fixture.debugElement.nativeElement.querySelector('table');
            expect(table.tBodies).toHaveLength(1);
        });
    });

    describe('sorting re-renders the table', () => {
        function renderedNames(): string[] {
            const cells = fixture.debugElement.nativeElement.querySelectorAll('tbody tr td:nth-child(2)');
            return Array.from(cells as NodeListOf<HTMLTableCellElement>).map((cell) => cell.textContent!.trim());
        }

        it('reorders the rendered rows when sorting by name using natural ordering', () => {
            // Use the real sort service so the DOM order reflects an actual sort.
            sortByPropertySpy.mockRestore();
            const user9 = { name: 'Test User 9', userId: 1 } as TutorLeaderboardElement;
            const user10 = { name: 'Test User 10', userId: 2 } as TutorLeaderboardElement;
            const user8 = { name: 'Test User 8', userId: 3 } as TutorLeaderboardElement;
            fixture.componentRef.setInput('tutorsData', [user9, user10, user8]);
            fixture.detectChanges();

            comp.sortPredicate.set('name');
            comp.reverseOrder.set(true); // ascending
            fixture.detectChanges();

            // Natural ordering keeps "Test User 10" after "Test User 9" instead of right after "Test User 1".
            expect(renderedNames()).toEqual(['Test User 8', 'Test User 9', 'Test User 10']);
        });
    });
});
