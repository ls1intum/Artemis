import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { ExamManagementOverviewComponent } from 'app/exam/manage/exam-management/exam-management-overview.component';
import { ExamManagementComponent } from 'app/exam/manage/exam-management/exam-management.component';
import { SortService } from 'app/foundation/service/sort.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ExamImportComponent } from 'app/exam/manage/exams/exam-import/exam-import.component';

describe('ExamManagementOverviewComponent', () => {
    let comp: ExamManagementOverviewComponent;
    let fixture: ComponentFixture<ExamManagementOverviewComponent>;
    let sortService: SortService;
    let dialogService: DialogService;
    let router: Router;

    const course: Course = { id: 456, isAtLeastInstructor: true } as Course;
    const exam1: Exam = { id: 1, title: 'Exam 1', testExam: false } as Exam;
    const exam2: Exam = { id: 2, title: 'Exam 2', testExam: true } as Exam;

    const courseSignal = signal<Course>(course);
    const examsSignal = signal<Exam[]>([exam1, exam2]);

    beforeEach(async () => {
        courseSignal.set(course);
        examsSignal.set([exam1, exam2]);

        await TestBed.configureTestingModule({
            imports: [ExamManagementOverviewComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useClass: MockDialogService },
                { provide: Router, useClass: MockRouter },
                {
                    provide: ExamManagementComponent,
                    useValue: {
                        course: courseSignal,
                        exams: examsSignal,
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamManagementOverviewComponent);
        comp = fixture.componentInstance;
        sortService = TestBed.inject(SortService);
        dialogService = TestBed.inject(DialogService);
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should read course and exams from parent ExamManagementComponent', () => {
        expect(comp.course()).toEqual(course);
        expect(comp.exams()).toEqual([exam1, exam2]);
    });

    it('should track exam by trackId', () => {
        expect(comp.trackId(0, exam1)).toBe(exam1.id);
        expect(comp.trackId(1, { id: undefined } as any)).toBeUndefined();
    });

    it('should sort rows using sortService', () => {
        comp.predicate = 'id';
        comp.ascending = true;

        const sortSpy = vi.spyOn(sortService, 'sortByProperty').mockReturnValue([exam1, exam2]);

        comp.sortRows();

        expect(sortSpy).toHaveBeenCalledWith([exam1, exam2], 'id', true);
        expect(comp.exams()).toEqual([exam1, exam2]);
    });

    it('should open import modal and navigate when an exam is selected', () => {
        const selectedExam: Exam = { id: 99, title: 'Imported Exam' };
        const dialogRef = {
            onClose: of(selectedExam),
        } as unknown as DynamicDialogRef;

        const openSpy = vi.spyOn(dialogService, 'open').mockReturnValue(dialogRef);
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

        comp.openImportModal();

        expect(openSpy).toHaveBeenCalledWith(
            ExamImportComponent,
            expect.objectContaining({
                data: { subsequentExerciseGroupSelection: false },
            }),
        );
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', course.id, 'exams', 'import', selectedExam.id]);
    });

    it('should open import modal and not navigate when closed without selection', () => {
        const dialogRef = {
            onClose: of(undefined),
        } as unknown as DynamicDialogRef;

        vi.spyOn(dialogService, 'open').mockReturnValue(dialogRef);
        const navigateSpy = vi.spyOn(router, 'navigate');

        comp.openImportModal();

        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should destroy dialogErrorSource on ngOnDestroy', () => {
        const unsubscribeSpy = vi.spyOn((comp as any).dialogErrorSource, 'unsubscribe');

        comp.ngOnDestroy();

        expect(unsubscribeSpy).toHaveBeenCalled();
    });
});
