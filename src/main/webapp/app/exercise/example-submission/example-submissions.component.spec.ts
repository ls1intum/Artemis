import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject, of, throwError } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { AccountService } from 'app/core/auth/account.service';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { ExampleSubmissionService } from 'app/assessment/shared/services/example-submission.service';
import { ExampleSubmissionsComponent } from 'app/exercise/example-submission/example-submissions.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('Example Submission Component', () => {
    setupTestBed({ zoneless: true });

    let component: ExampleSubmissionsComponent;
    let fixture: ComponentFixture<ExampleSubmissionsComponent>;
    let exampleSubmissionService: ExampleSubmissionService;
    let dialogService: DialogService;
    let alertService: AlertService;
    let exercise: Exercise;
    let dialogCloseSubject: Subject<Submission | undefined>;
    let dialogRef: DynamicDialogRef;

    const createExercise = (): Exercise => ({
        id: 1,
        type: ExerciseType.TEXT,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
        exampleSubmissions: [{ id: 1 } as ExampleSubmission, { id: 2 } as ExampleSubmission],
    });

    beforeEach(() => {
        exercise = createExercise();
        const route = { data: of({ exercise }), children: [] } as any as ActivatedRoute;
        dialogCloseSubject = new Subject<Submission | undefined>();
        dialogRef = {
            close: vi.fn(),
            onClose: dialogCloseSubject.asObservable(),
        } as unknown as DynamicDialogRef;

        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: DialogService, useValue: { open: vi.fn().mockReturnValue(dialogRef) } },
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideTemplate(ExampleSubmissionsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ExampleSubmissionsComponent);
        component = fixture.componentInstance;
        exampleSubmissionService = TestBed.inject(ExampleSubmissionService);
        dialogService = TestBed.inject(DialogService);
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        dialogCloseSubject.complete();
    });

    it('should initialize the component', () => {
        component.ngOnInit();

        expect(component.exercise).toBeDefined();
    });

    it('should delete an example submission', () => {
        component.exercise = exercise;
        component.createdExampleAssessment = [false, false];
        const deleteStub = vi.spyOn(exampleSubmissionService, 'delete').mockReturnValue(of(new HttpResponse<void>()));

        component.deleteExampleSubmission(0);

        expect(deleteStub).toHaveBeenCalledOnce();
        expect(exercise.exampleSubmissions).toHaveLength(1);
    });

    it('should catch an error on delete', () => {
        component.exercise = exercise;
        component.createdExampleAssessment = [false, false];
        vi.spyOn(exampleSubmissionService, 'delete').mockReturnValue(throwError(() => ({ status: 500 })));

        const alertServiceSpy = vi.spyOn(alertService, 'error');
        component.deleteExampleSubmission(0);

        expect(alertServiceSpy).toHaveBeenCalledOnce();
    });

    it('should get the submission size', () => {
        const textSubmission = {
            id: 2,
            text: 'test text',
        } as TextSubmission;
        const exampleTextSubmission = {
            id: 1,
            submission: textSubmission,
        } as ExampleSubmission;
        exercise.exampleSubmissions = [exampleTextSubmission];

        const getSubmissionSizeSpy = vi.spyOn(exampleSubmissionService, 'getSubmissionSize');

        component.exercise = exercise;
        component.ngOnInit();
        expect(component.exercise.exampleSubmissions).toBeDefined();
        expect(component.exercise.exampleSubmissions![0].submission?.submissionSize).toBe(2);
        expect(getSubmissionSizeSpy).toHaveBeenCalledOnce();
    });

    it('should not import when the import dialog closes without a submission', () => {
        component.exercise = exercise;
        const importStub = vi.spyOn(exampleSubmissionService, 'import').mockReturnValue(throwError(() => ({ status: 500 })));

        component.openImportModal();
        dialogCloseSubject.next(undefined);

        expect(dialogService.open).toHaveBeenCalledOnce();
        expect(importStub).not.toHaveBeenCalled();
    });

    it('should close the import dialog on component destroy', () => {
        component.exercise = exercise;

        component.openImportModal();
        component.ngOnDestroy();

        expect(dialogRef.close).toHaveBeenCalledOnce();
    });
});
