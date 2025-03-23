import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { MockProvider } from 'ng-mocks';
import { TutorLeaderboardElement } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.model';
import { TutorLeaderboardComponent } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.component';
import { SortService } from 'app/shared/service/sort.service';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/exercise/entities/exercise.model';
import { Exam } from 'app/entities/exam/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('TutorLeaderboardComponent', () => {
    let comp: TutorLeaderboardComponent;
    let fixture: ComponentFixture<TutorLeaderboardComponent>;
    let sortService: SortService;
    let sortByPropertySpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                MockProvider(AccountService),
                {
                    provide: Router,
                    useClass: MockRouter,
                },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(TutorLeaderboardComponent);
        comp = fixture.componentInstance;
        sortService = TestBed.inject(SortService);
        sortByPropertySpy = jest.spyOn(sortService, 'sortByProperty').mockImplementation(() => []);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('ngOnInit', () => {
        it('sets variables correctly if course is set', () => {
            const course = { isAtLeastInstructor: true } as Course;
            comp.course = course;
            comp.ngOnInit();
            expect(comp.isExerciseDashboard).toBeFalse();
            expect(comp.course).toBe(course);
            expect(comp.exercise).toBeUndefined();
        });

        it('sets variables correctly if exercise.course is set', () => {
            const course = { isAtLeastInstructor: true } as Course;
            const exercise = { course } as Exercise;
            comp.exercise = exercise;
            comp.ngOnInit();
            expect(comp.isExerciseDashboard).toBeTrue();
            expect(comp.course).toBe(course);
            expect(comp.exercise).toBe(exercise);
        });

        it('sets variables correctly if exercise.exerciseGroup.exam.course is set', () => {
            const course = {} as Course;
            const exam = { course: course } as Exam;
            const exerciseGroup = { exam: exam } as ExerciseGroup;
            const exercise = { exerciseGroup: exerciseGroup } as Exercise;
            comp.exercise = exercise;
            comp.ngOnInit();
            expect(comp.isExerciseDashboard).toBeTrue();
            expect(comp.course).toBe(course);
            expect(comp.exercise).toBe(exercise);
        });

        it('sets isExamMode if exam is set', () => {
            expect(comp.isExamMode).toBeFalse();
            comp.ngOnInit();
            expect(comp.isExamMode).toBeFalse();

            const exam = {} as Exam;
            const course = { exams: [exam], isAtLeastInstructor: true } as Course;
            comp.exam = exam;
            comp.course = course;
            comp.ngOnInit();
            expect(comp.isExamMode).toBeTrue();
        });

        it('should sort rows', () => {
            comp.ngOnInit();
            expect(sortByPropertySpy).toHaveBeenCalledOnce();
        });
    });

    describe('tutorData', () => {
        it('should fill the table with elements', () => {
            const element = new TutorLeaderboardElement();
            comp.tutorsData = [element];
            const table: HTMLTableElement = fixture.debugElement.nativeElement.querySelector('table');
            expect(table.tBodies).toHaveLength(1);
        });
    });
});
