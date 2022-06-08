import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { SubmissionExportDialogComponent } from 'app/exercises/shared/submission-export/submission-export-dialog.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { MockExerciseService } from '../../../helpers/mocks/service/mock-exercise.service';
import { SubmissionExportService } from 'app/exercises/shared/submission-export/submission-export.service';
import { HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import * as DownloadUtil from 'app/shared/util/download.util';
import { of } from 'rxjs';

describe('Submission Export Dialog Component', () => {
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
            imports: [ArtemisTestModule],
            declarations: [SubmissionExportDialogComponent, MockDirective(NgbTooltip), MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisTimeAgoPipe)],
            providers: [
                { provide: SubmissionExportService, useValue: MockProvider(SubmissionExportService) },
                { provide: ExerciseService, useClass: MockExerciseService },
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
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        const findSpy = jest.spyOn(exerciseService, 'find');
        component.ngOnInit();

        expect(component.isLoading).toBeFalse();
        expect(component.exportInProgress).toBeFalse();
        expect(component.submissionExportOptions).toEqual(submissionExportOptions);
        expect(component.exercise).toEqual({ id: exerciseId } as Exercise);
        expect(findSpy).toHaveBeenCalledOnce();
        expect(findSpy).toHaveBeenCalledWith(exerciseId);
    });

    it('should handle export response', () => {
        const response: HttpResponse<Blob> = new HttpResponse();
        const alertSpy = jest.spyOn(alertService, 'success');
        const modalSpy = jest.spyOn(component.activeModal, 'dismiss');
        const downloadSpy = jest.spyOn(DownloadUtil, 'downloadZipFileFromResponse');

        component.handleExportResponse(response);

        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('instructorDashboard.exportSubmissions.successMessage');
        expect(modalSpy).toHaveBeenCalledOnce();
        expect(modalSpy).toHaveBeenCalledWith(true);
        expect(component.exportInProgress).toBeFalse();
        expect(downloadSpy).toHaveBeenCalledOnce();
        expect(downloadSpy).toHaveBeenCalledWith(response);
    });

    it('should clear dialog', () => {
        const modalSpy = jest.spyOn(component.activeModal, 'dismiss');
        component.clear();

        expect(modalSpy).toHaveBeenCalledOnce();
        expect(modalSpy).toHaveBeenCalledWith('cancel');
    });

    describe('Exporting Submission', () => {
        let exportSubmissionServiceMock: jest.Mock;
        let handleExportResponseMock: jest.Mock;

        beforeEach(() => {
            handleExportResponseMock = jest.fn().mockReturnValue(of());
            component.handleExportResponse = handleExportResponseMock;
        });

        it('should export submission', () => {
            exportSubmissionServiceMock = jest.fn().mockReturnValue(of({ body: {} } as HttpResponse<Blob>));
            submissionExportService.exportSubmissions = exportSubmissionServiceMock;

            component.exportSubmissions(exerciseId);

            expect(exportSubmissionServiceMock).toHaveBeenCalledOnce();
            expect(exportSubmissionServiceMock).toHaveBeenCalledWith(exerciseId, validExerciseType, submissionExportOptions);
            expect(component.exportInProgress).toBeTrue();
            expect(handleExportResponseMock).toHaveBeenCalledOnce();
            expect(handleExportResponseMock).toHaveBeenCalledWith({ body: {} });
        });

        it('should handle error exporting submission for unsupported exercise types', () => {
            exportSubmissionServiceMock = jest.fn().mockImplementation(() => {
                throw Error('Export not implemented for exercise type ' + invalidExerciseType);
            });
            submissionExportService.exportSubmissions = exportSubmissionServiceMock;
            component.exerciseType = invalidExerciseType;

            expect(() => {
                component.exportSubmissions(exerciseId);
            }).toThrow('Export not implemented for exercise type ' + invalidExerciseType);

            expect(exportSubmissionServiceMock).toHaveBeenCalledOnce();
            expect(exportSubmissionServiceMock).toHaveBeenCalledWith(exerciseId, invalidExerciseType, submissionExportOptions);
            expect(handleExportResponseMock).toHaveBeenCalledTimes(0);
        });
    });
});
