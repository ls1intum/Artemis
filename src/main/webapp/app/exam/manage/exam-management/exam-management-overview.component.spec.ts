import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, RouterLink } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { MockComponent, MockDirective } from 'ng-mocks';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { ExamManagementOverviewComponent } from 'app/exam/manage/exam-management/exam-management-overview.component';
import { ExamManagementComponent } from 'app/exam/manage/exam-management/exam-management.component';
import { SortService } from 'app/foundation/service/sort.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ExamStatusComponent } from 'app/exam/manage/exam-status/exam-status.component';
import { ExamImportComponent } from 'app/exam/manage/exams/exam-import/exam-import.component';

describe('ExamManagementOverviewComponent', () => {
    let comp: ExamManagementOverviewComponent;
    let fixture: ComponentFixture<ExamManagementOverviewComponent>;
    let sortService: SortService;
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
                { provide: Router, useClass: MockRouter },
                {
                    provide: ExamManagementComponent,
                    useValue: {
                        course: courseSignal,
                        exams: examsSignal,
                    },
                },
            ],
        })
            .overrideComponent(ExamManagementOverviewComponent, {
                remove: { imports: [ExamImportComponent, ExamStatusComponent, RouterLink] },
                add: { imports: [MockComponent(ExamImportComponent), MockComponent(ExamStatusComponent), MockDirective(RouterLink)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExamManagementOverviewComponent);
        comp = fixture.componentInstance;
        sortService = TestBed.inject(SortService);
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
        comp.predicate.set('id');
        comp.ascending.set(true);

        const sortSpy = vi.spyOn(sortService, 'sortByProperty').mockReturnValue([exam1, exam2]);

        comp.sortRows();

        expect(sortSpy).toHaveBeenCalledWith([exam1, exam2], 'id', true);
        expect(comp.exams()).toEqual([exam1, exam2]);
    });

    it('should update predicate and direction and re-sort on sort change', () => {
        const sortSpy = vi.spyOn(sortService, 'sortByProperty').mockReturnValue([exam2, exam1]);

        comp.onSortChange({ field: 'title', order: -1 });

        expect(comp.predicate()).toBe('title');
        expect(comp.ascending()).toBe(false);
        expect(sortSpy).toHaveBeenCalledWith([exam1, exam2], 'title', false);
    });

    it('should host the import in a dialog that is closed and not rendered initially', () => {
        fixture.detectChanges();

        expect(comp.importDialogVisible()).toBe(false);
        expect(fixture.debugElement.query(By.directive(ExamImportComponent))).toBeNull();
    });

    it('should open the import dialog with the exam import and navigate when an exam is selected', () => {
        const selectedExam: Exam = { id: 99, title: 'Imported Exam' };
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        fixture.detectChanges();

        comp.openImportModal();
        fixture.detectChanges();

        expect(comp.importDialogVisible()).toBe(true);
        const importComponent = fixture.debugElement.query(By.directive(ExamImportComponent));
        expect(importComponent).not.toBeNull();
        expect(importComponent.componentInstance.subsequentExerciseGroupSelection()).toBeFalsy();

        importComponent.componentInstance.examSelected.emit(selectedExam);
        fixture.detectChanges();

        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', course.id, 'exams', 'import', selectedExam.id]);
        expect(comp.importDialogVisible()).toBe(false);
        expect(fixture.debugElement.query(By.directive(ExamImportComponent))).toBeNull();
    });

    it('should not navigate when the import dialog is dismissed without a selection', () => {
        const navigateSpy = vi.spyOn(router, 'navigate');
        fixture.detectChanges();

        comp.openImportModal();
        fixture.detectChanges();
        comp.importDialogVisible.set(false);
        fixture.detectChanges();

        expect(navigateSpy).not.toHaveBeenCalled();
        expect(fixture.debugElement.query(By.directive(ExamImportComponent))).toBeNull();
    });

    it('should destroy dialogErrorSource on ngOnDestroy', () => {
        const unsubscribeSpy = vi.spyOn((comp as any).dialogErrorSource, 'unsubscribe');

        comp.ngOnDestroy();

        expect(unsubscribeSpy).toHaveBeenCalled();
    });
});
