/**
 * Vitest tests for UpcomingExamsAndExercisesComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

import { UpcomingExamsAndExercisesComponent } from 'app/core/admin/upcoming-exams-and-exercises/upcoming-exams-and-exercises.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { MockExerciseService } from 'test/helpers/mocks/service/mock-exercise.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { MockExamManagementService } from 'test/helpers/mocks/service/mock-exam-management.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Course } from 'app/core/course/shared/entities/course.model';

describe('UpcomingExamsAndExercisesComponent', () => {
    setupTestBed({ zoneless: true });

    let component: UpcomingExamsAndExercisesComponent;
    let fixture: ComponentFixture<UpcomingExamsAndExercisesComponent>;
    let exerciseService: ExerciseService;
    let examManagementService: ExamManagementService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UpcomingExamsAndExercisesComponent],
            providers: [
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: ExamManagementService, useClass: MockExamManagementService },
            ],
        })
            .overrideTemplate(UpcomingExamsAndExercisesComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(UpcomingExamsAndExercisesComponent);
        component = fixture.componentInstance;
        exerciseService = TestBed.inject(ExerciseService);
        examManagementService = TestBed.inject(ExamManagementService);
    });

    it('should render a component from the admin module', () => {
        expect(component).toBeDefined();
    });

    describe('ngOnInit', () => {
        it('should load upcoming exercises on init', () => {
            component.ngOnInit();

            expect(component.upcomingExercises()).toHaveLength(2);
        });

        it('should load upcoming exams on init', () => {
            component.ngOnInit();

            expect(component.upcomingExams()).toBeDefined();
        });

        it('should handle null body from exercise service', () => {
            vi.spyOn(exerciseService, 'getUpcomingExercises').mockReturnValue(of(new HttpResponse({ body: [] })));

            component.ngOnInit();

            expect(component.upcomingExercises()).toEqual([]);
        });

        it('should handle null body from exam service', () => {
            vi.spyOn(examManagementService, 'findAllCurrentAndUpcomingExams').mockReturnValue(of(new HttpResponse({ body: [] })));

            component.ngOnInit();

            expect(component.upcomingExams()).toEqual([]);
        });
    });

    describe('trackByExercise', () => {
        it('should return course id and exercise id as tracking key', () => {
            const course = { id: 10 } as Course;
            const exercise = { id: 5, course } as Exercise;

            const result = component.trackByExercise(0, exercise);

            expect(result).toBe('10_5');
        });

        it('should handle exercise without course', () => {
            const exercise = { id: 5 } as Exercise;

            const result = component.trackByExercise(0, exercise);

            expect(result).toBe('undefined_5');
        });
    });

    describe('trackByExam', () => {
        it('should return course id and exam id as tracking key', () => {
            const course = { id: 20 } as Course;
            const exam = { id: 15, course } as Exam;

            const result = component.trackByExam(0, exam);

            expect(result).toBe('20_15');
        });

        it('should handle exam without course', () => {
            const exam = { id: 15 } as Exam;

            const result = component.trackByExam(0, exam);

            expect(result).toBe('undefined_15');
        });
    });
});
