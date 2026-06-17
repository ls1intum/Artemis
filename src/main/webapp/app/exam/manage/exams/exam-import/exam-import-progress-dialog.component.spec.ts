import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ExamImportProgressDialogComponent } from 'app/exam/manage/exams/exam-import/exam-import-progress-dialog.component';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExamImportProgress, ExamImportProgressState, ExerciseImportStatus } from 'app/exam/shared/entities/exam-import-progress.model';
import { ExamImportResultDTO } from 'app/exam/shared/entities/exam-import-result.model';
import { Exam } from 'app/exam/shared/entities/exam.model';

describe('ExamImportProgressDialogComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ExamImportProgressDialogComponent;
    let fixture: ComponentFixture<ExamImportProgressDialogComponent>;
    let examManagementService: ExamManagementService;
    let progress$: Subject<ExamImportProgress>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamImportProgressDialogComponent],
            providers: [MockProvider(ExamManagementService), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamImportProgressDialogComponent);
        component = fixture.componentInstance;
        examManagementService = TestBed.inject(ExamManagementService);

        progress$ = new Subject<ExamImportProgress>();
        vi.spyOn(examManagementService, 'subscribeToImportProgress').mockReturnValue(progress$.asObservable());
    });

    it('should show live progress while the import is running', () => {
        const request$ = new Subject<HttpResponse<ExamImportResultDTO>>();
        component.runImport('import-1', request$.asObservable());

        expect(examManagementService.subscribeToImportProgress).toHaveBeenCalledWith('import-1');

        progress$.next({
            state: ExamImportProgressState.RUNNING,
            totalExercises: 3,
            processedExercises: 1,
            currentExerciseTitle: 'Quiz 1',
            currentStatus: ExerciseImportStatus.IMPORTING,
        });

        expect(component.visible()).toBeTruthy();
        expect(component.ready()).toBeFalsy();
        expect(component.totalExercises()).toBe(3);
        expect(component.processedExercises()).toBe(1);
        expect(component.currentExerciseTitle()).toBe('Quiz 1');
        expect(component.progressPercentage()).toBe(33);
    });

    it('should show a success summary and resolve on dismiss when everything imported', async () => {
        const request$ = new Subject<HttpResponse<ExamImportResultDTO>>();
        const promise = component.runImport('import-2', request$.asObservable());

        progress$.next({
            state: ExamImportProgressState.RUNNING,
            totalExercises: 2,
            processedExercises: 1,
            currentExerciseTitle: 'Text 1',
            currentStatus: ExerciseImportStatus.IMPORTED,
        });
        // The (authoritative) HTTP response carries no skipped/incomplete lists (NON_EMPTY omits empty arrays)
        request$.next(new HttpResponse({ status: 201, body: { exam: { id: 7 } as Exam } }));
        request$.complete();

        expect(component.ready()).toBeTruthy();
        expect(component.hasIssues()).toBeFalsy();
        expect(component.importedCount()).toBe(2);

        component.onDismiss();
        const response = await promise;
        expect(response.body?.exam?.id).toBe(7);
        expect(component.visible()).toBeFalsy();
    });

    it('should list skipped and incomplete exercises from the (authoritative) HTTP response', async () => {
        const request$ = new Subject<HttpResponse<ExamImportResultDTO>>();
        const promise = component.runImport('import-3', request$.asObservable());

        progress$.next({ state: ExamImportProgressState.RUNNING, totalExercises: 3, processedExercises: 0 });
        request$.next(new HttpResponse({ status: 201, body: { exam: { id: 8 } as Exam, skippedExercises: ['Quiz 2'], incompleteExercises: ['Prog 1'] } }));
        request$.complete();

        expect(component.ready()).toBeTruthy();
        expect(component.hasIssues()).toBeTruthy();
        expect(component.skippedExercises()).toEqual(['Quiz 2']);
        expect(component.incompleteExercises()).toEqual(['Prog 1']);
        // 3 total, one skipped and one incomplete -> one imported
        expect(component.importedCount()).toBe(1);

        component.onDismiss();
        await expect(promise).resolves.toBeDefined();
    });

    it('should reject without showing a summary when the request fails before any progress', async () => {
        const request$ = new Subject<HttpResponse<ExamImportResultDTO>>();
        const promise = component.runImport('import-4', request$.asObservable());
        const error = new HttpErrorResponse({ status: 400, error: { errorKey: 'invalidKey' } });

        request$.error(error);

        await expect(promise).rejects.toBe(error);
        expect(component.visible()).toBeFalsy();
        expect(component.ready()).toBeFalsy();
    });
});
