import { Mock, expect, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SubmissionExportDialogComponent } from 'app/exercise/submission-export/dialog/submission-export-dialog.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { MockExerciseService } from 'test/helpers/mocks/service/mock-exercise.service';
import { SubmissionExportService } from 'app/exercise/submission-export/submission-export.service';
import { HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import * as DownloadUtil from 'app/shared/util/download.util';
import { of } from 'rxjs';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

describe('Submission Export Dialog Component', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<SubmissionExportDialogComponent>;
    let component: SubmissionExportDialogComponent;

    let alertService: AlertService;
    let submissionExportService: SubmissionExportService;
    let exerciseService: ExerciseService;

    const exerciseId = 1;
    const validExerciseType = ExerciseType.TEXT;
    const invalidExerciseType = ExerciseType.PROGRAMMING;
    const submissionExportOptions = {
        exportAllParticipants: false,
        filterLateSubmissions: false,
        filterLateSubmissionsDate: null,
        participantIdentifierList: '',
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisTimeAgoPipe), SubmissionExportDialogComponent],
            providers: [
                { provide: SubmissionExportService, useValue: MockProvider(SubmissionExportService) },
                { provide: ExerciseService, useClass: MockExerciseService },
                MockProvider(AlertService),
                MockProvider(NgbActiveModal),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(SubmissionExportDialogComponent);
                component = fixture.componentInstance;

                alertService = TestBed.inject(AlertService);
                submissionExportService = TestBed.inject(SubmissionExportService);
                exerciseService = TestBed.inject(ExerciseService);

                component.exerciseId = exerciseId;
                component.exerciseType = validExerciseType;
                component.submissionExportOptions = submissionExportOptions;
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        const findSpy = vi.spyOn(exerciseService, 'find');
        component.ngOnInit();

        expect(component.isLoading).toBe(false);
        expect(component.exportInProgress).toBe(false);
        expect(component.submissionExportOptions).toEqual(submissionExportOptions);
        expect(component.exercise).toEqual({ id: exerciseId } as Exercise);
        expect(findSpy).toHaveBeenCalledOnce();
        expect(findSpy).toHaveBeenCalledWith(exerciseId);
    });

    it('should handle export response', () => {
        const response: HttpResponse<Blob> = new HttpResponse();
        const alertSpy = vi.spyOn(alertService, 'success');
        const modalSpy = vi.spyOn(component.activeModal, 'dismiss');
        const downloadSpy = vi.spyOn(DownloadUtil, 'downloadZipFileFromResponse');

        component.handleExportResponse(response);

        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.instructorDashboard.exportSubmissions.successMessage');
        expect(modalSpy).toHaveBeenCalledOnce();
        expect(modalSpy).toHaveBeenCalledWith(true);
        expect(component.exportInProgress).toBe(false);
        expect(downloadSpy).toHaveBeenCalledOnce();
        expect(downloadSpy).toHaveBeenCalledWith(response);
    });

    it('should clear dialog', () => {
        const modalSpy = vi.spyOn(component.activeModal, 'dismiss');
        component.clear();

        expect(modalSpy).toHaveBeenCalledOnce();
        expect(modalSpy).toHaveBeenCalledWith('cancel');
    });

    describe('Exporting Submission', () => {
        let exportSubmissionServiceMock: Mock;
        let handleExportResponseMock: Mock;

        beforeEach(() => {
            handleExportResponseMock = vi.fn().mockReturnValue(of());
            component.handleExportResponse = handleExportResponseMock;
        });

        it('should export submission', () => {
            exportSubmissionServiceMock = vi.fn().mockReturnValue(of({ body: {} } as HttpResponse<Blob>));
            submissionExportService.exportSubmissions = exportSubmissionServiceMock;

            component.exportSubmissions(exerciseId);

            expect(exportSubmissionServiceMock).toHaveBeenCalledOnce();
            expect(exportSubmissionServiceMock).toHaveBeenCalledWith(exerciseId, validExerciseType, submissionExportOptions);
            expect(component.exportInProgress).toBe(true);
            expect(handleExportResponseMock).toHaveBeenCalledOnce();
            expect(handleExportResponseMock).toHaveBeenCalledWith({ body: {} });
        });

        it('should handle error exporting submission for unsupported exercise types', () => {
            exportSubmissionServiceMock = vi.fn().mockImplementation(() => {
                throw Error('Export not implemented for exercise type ' + invalidExerciseType);
            });
            submissionExportService.exportSubmissions = exportSubmissionServiceMock;
            component.exerciseType = invalidExerciseType;

            expect(() => {
                component.exportSubmissions(exerciseId);
            }).toThrow('Export not implemented for exercise type ' + invalidExerciseType);

            expect(exportSubmissionServiceMock).toHaveBeenCalledOnce();
            expect(exportSubmissionServiceMock).toHaveBeenCalledWith(exerciseId, invalidExerciseType, submissionExportOptions);
            expect(handleExportResponseMock).not.toHaveBeenCalled();
        });
    });
});
