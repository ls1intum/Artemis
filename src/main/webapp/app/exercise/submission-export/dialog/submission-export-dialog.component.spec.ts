import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SubmissionExportDialogComponent } from 'app/exercise/submission-export/dialog/submission-export-dialog.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { MockExerciseService } from 'test/helpers/mocks/service/mock-exercise.service';
import { SubmissionExportOptions, SubmissionExportService } from 'app/exercise/submission-export/submission-export.service';
import { HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import * as DownloadUtil from 'app/shared/util/download.util';
import { Subject, of } from 'rxjs';
import { DynamicDialogRef } from 'primeng/dynamicdialog';

describe('Submission Export Dialog Component', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<SubmissionExportDialogComponent>;
    let component: SubmissionExportDialogComponent;

    let alertService: AlertService;
    let submissionExportService: SubmissionExportService;
    let exerciseService: ExerciseService;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;

    const exerciseId = 1;
    const validExerciseType = ExerciseType.TEXT;
    const invalidExerciseType = ExerciseType.PROGRAMMING;
    const submissionExportOptions = {
        exportAllParticipants: false,
        filterLateSubmissions: false,
        filterLateSubmissionsDate: null,
        participantIdentifierList: '',
    };

    beforeEach(async () => {
        dialogRefCloseSpy = vi.fn();
        alertService = {
            success: vi.fn(),
            error: vi.fn(),
        } as unknown as AlertService;
        submissionExportService = {
            exportSubmissions: vi.fn(),
        } as unknown as SubmissionExportService;

        await TestBed.configureTestingModule({
            imports: [SubmissionExportDialogComponent],
            providers: [
                { provide: SubmissionExportService, useValue: submissionExportService },
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: AlertService, useValue: alertService },
                { provide: DynamicDialogRef, useValue: { close: dialogRefCloseSpy, onClose: new Subject<unknown>() } as unknown as DynamicDialogRef },
            ],
        })
            .overrideTemplate(SubmissionExportDialogComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(SubmissionExportDialogComponent);
        component = fixture.componentInstance;

        exerciseService = TestBed.inject(ExerciseService);
        fixture.componentRef.setInput('exerciseId', exerciseId);
        fixture.componentRef.setInput('exerciseType', validExerciseType);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        const findSpy = vi.spyOn(exerciseService, 'find');
        component.ngOnInit();

        expect(component.isLoading()).toBe(false);
        expect(component.exportInProgress()).toBe(false);
        expect(component.submissionExportOptions()).toEqual(submissionExportOptions);
        expect(component.exercise()).toEqual({ id: exerciseId } as Exercise);
        expect(findSpy).toHaveBeenCalledOnce();
        expect(findSpy).toHaveBeenCalledWith(exerciseId);
    });

    it('should handle export response', () => {
        const response: HttpResponse<Blob> = new HttpResponse();
        const alertSpy = vi.spyOn(alertService, 'success');
        const downloadSpy = vi.spyOn(DownloadUtil, 'downloadZipFileFromResponse');

        component.handleExportResponse(response);

        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.instructorDashboard.exportSubmissions.successMessage');
        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();
        expect(dialogRefCloseSpy).toHaveBeenCalledWith(true);
        expect(component.exportInProgress()).toBe(false);
        expect(downloadSpy).toHaveBeenCalledOnce();
        expect(downloadSpy).toHaveBeenCalledWith(response);
    });

    it('should clear dialog', () => {
        component.clear();

        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();
        expect(dialogRefCloseSpy).toHaveBeenCalledWith('cancel');
    });

    describe('Exporting Submission', () => {
        let exportSubmissionServiceMock: (
            exerciseId: number,
            exerciseType: ExerciseType,
            repositoryExportOptions: SubmissionExportOptions,
        ) => ReturnType<SubmissionExportService['exportSubmissions']>;
        let handleExportResponseMock: ReturnType<typeof vi.fn>;

        beforeEach(() => {
            handleExportResponseMock = vi.fn().mockReturnValue(of());
            component.handleExportResponse = handleExportResponseMock as unknown as (response: HttpResponse<Blob>) => void;
        });

        it('should export submission', () => {
            exportSubmissionServiceMock = vi
                .fn<
                    (exerciseId: number, exerciseType: ExerciseType, repositoryExportOptions: SubmissionExportOptions) => ReturnType<SubmissionExportService['exportSubmissions']>
                >()
                .mockReturnValue(of({ body: {} } as HttpResponse<Blob>));
            vi.spyOn(submissionExportService, 'exportSubmissions').mockImplementation(exportSubmissionServiceMock);

            component.exportSubmissions(exerciseId);

            expect(exportSubmissionServiceMock).toHaveBeenCalledOnce();
            expect(exportSubmissionServiceMock).toHaveBeenCalledWith(exerciseId, validExerciseType, submissionExportOptions);
            expect(component.exportInProgress()).toBe(true);
            expect(handleExportResponseMock).toHaveBeenCalledOnce();
            expect(handleExportResponseMock).toHaveBeenCalledWith({ body: {} });
        });

        it('should handle error exporting submission for unsupported exercise types', () => {
            exportSubmissionServiceMock = vi.fn<
                (exerciseId: number, exerciseType: ExerciseType, repositoryExportOptions: SubmissionExportOptions) => ReturnType<SubmissionExportService['exportSubmissions']>
            >(() => {
                throw new Error('Export not implemented for exercise type ' + invalidExerciseType);
            });
            vi.spyOn(submissionExportService, 'exportSubmissions').mockImplementation(exportSubmissionServiceMock);
            fixture.componentRef.setInput('exerciseType', invalidExerciseType);

            expect(() => {
                component.exportSubmissions(exerciseId);
            }).toThrow('Export not implemented for exercise type ' + invalidExerciseType);

            expect(exportSubmissionServiceMock).toHaveBeenCalledOnce();
            expect(exportSubmissionServiceMock).toHaveBeenCalledWith(exerciseId, invalidExerciseType, submissionExportOptions);
            expect(handleExportResponseMock).not.toHaveBeenCalled();
        });
    });
});
