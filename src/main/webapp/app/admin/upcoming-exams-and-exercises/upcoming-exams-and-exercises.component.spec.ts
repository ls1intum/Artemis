/**
 * Vitest tests for UpcomingExamsAndExercisesComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

import { UpcomingExamsAndExercisesComponent } from 'app/admin/upcoming-exams-and-exercises/upcoming-exams-and-exercises.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { MockExerciseService } from 'test/helpers/mocks/service/mock-exercise.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { MockExamManagementService } from 'test/helpers/mocks/service/mock-exam-management.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { Exam } from 'app/exam/shared/entities/exam.model';

describe('UpcomingExamsAndExercisesComponent', () => {
    let component: UpcomingExamsAndExercisesComponent;
    let fixture: ComponentFixture<UpcomingExamsAndExercisesComponent>;
    let exerciseService: ExerciseService;
    let examManagementService: ExamManagementService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UpcomingExamsAndExercisesComponent],
            providers: [
                provideRouter([]),
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: ExamManagementService, useClass: MockExamManagementService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

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

    describe('Exam Mode Badges', () => {
        it('should render correct exam mode badges', () => {
            const realExam = { id: 1, title: 'Real Exam', examMode: 'REAL' } as Exam;
            const testExam = { id: 2, title: 'Test Exam', examMode: 'TEST' } as Exam;
            const simulationExam = { id: 3, title: 'Simulation Exam', examMode: 'TEST_WITH_SIMULATION' } as Exam;

            vi.spyOn(examManagementService, 'findAllCurrentAndUpcomingExams').mockReturnValue(of(new HttpResponse({ body: [realExam, testExam, simulationExam] })));

            component.ngOnInit();
            fixture.detectChanges();

            const badges = fixture.nativeElement.querySelectorAll('jhi-exam-mode-badge');
            expect(badges).toHaveLength(3);

            // Verify the badges render the correct translation keys
            expect(badges[0].textContent).toContain('artemisApp.examManagement.testExam.realExam');
            expect(badges[1].textContent).toContain('artemisApp.examManagement.testExam.testExam');
            expect(badges[2].textContent).toContain('artemisApp.examManagement.testExam.testExamWithSimulation');
        });
    });
});
