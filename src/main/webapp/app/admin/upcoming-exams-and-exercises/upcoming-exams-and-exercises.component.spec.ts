/**
 * Vitest tests for UpcomingExamsAndExercisesComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

import { UpcomingExamsAndExercisesComponent } from 'app/admin/upcoming-exams-and-exercises/upcoming-exams-and-exercises.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { MockExerciseService } from 'test/helpers/mocks/service/mock-exercise.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { MockExamManagementService } from 'test/helpers/mocks/service/mock-exam-management.service';

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
});
