import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ManageAssessmentButtonsComponent } from 'app/exercise/exercise-scores/manage-assessment-buttons/manage-assessment-buttons.component';
import { MockProvider } from 'ng-mocks';
import { ProgrammingAssessmentManualResultService } from 'app/programming/manage/assess/manual-result/programming-assessment-manual-result.service';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { FileUploadAssessmentService } from 'app/fileupload/manage/assess/file-upload-assessment.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { of } from 'rxjs';
import { provideRouter } from '@angular/router';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

describe('ManageAssessmentButtonsComponent', () => {
    let fixture: ComponentFixture<ManageAssessmentButtonsComponent>;
    let comp: ManageAssessmentButtonsComponent;
    let programmingAssessmentService: ProgrammingAssessmentManualResultService;
    let modelingAssessmentService: ModelingAssessmentService;
    let textAssessmentService: TextAssessmentService;
    let fileUploadAssessmentService: FileUploadAssessmentService;

    const course = { id: 1 } as Course;
    const exercise = {
        id: 1,
        type: ExerciseType.PROGRAMMING,
    } as Exercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ManageAssessmentButtonsComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(ProgrammingAssessmentManualResultService),
                MockProvider(ModelingAssessmentService),
                MockProvider(TextAssessmentService),
                MockProvider(FileUploadAssessmentService),
                provideRouter([]),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ManageAssessmentButtonsComponent);
        comp = fixture.componentInstance;
        programmingAssessmentService = TestBed.inject(ProgrammingAssessmentManualResultService);
        modelingAssessmentService = TestBed.inject(ModelingAssessmentService);
        textAssessmentService = TestBed.inject(TextAssessmentService);
        fileUploadAssessmentService = TestBed.inject(FileUploadAssessmentService);

        comp.exercise = exercise;
        comp.course = course;
        comp.participation = {
            id: 1,
            submissions: [{ id: 1, results: [{ id: 1 } as Result] } as Submission],
        } as Participation;
    });

    describe('ngOnInit', () => {
        it('should set newManualResultAllowed for non-exam exercise', () => {
            comp.exercise = { ...exercise, assessmentType: 'SEMI_AUTOMATIC' } as any;

            comp.ngOnInit();

            expect(comp.examMode).toBeFalse();
            expect(comp.correctionRoundIndices).toEqual([0]);
        });

        it('should set examMode to true for exam exercise', () => {
            const examExercise = {
                ...exercise,
                exerciseGroup: {
                    exam: { numberOfCorrectionRoundsInExam: 2 },
                } as ExerciseGroup,
            } as Exercise;
            comp.exercise = examExercise;

            comp.ngOnInit();

            expect(comp.examMode).toBeTrue();
            expect(comp.correctionRoundIndices).toEqual([0, 1]);
        });

        it('should disable manual results for practice mode (testRun)', () => {
            // isPracticeMode checks testRun property
            comp.participation = {
                id: 1,
                testRun: true, // This is what isPracticeMode checks
                submissions: [],
            } as StudentParticipation;
            // Use an exercise that would normally allow manual results
            comp.exercise = {
                ...exercise,
                assessmentType: 'SEMI_AUTOMATIC',
                allowManualFeedbackRequests: true,
            } as any;

            comp.ngOnInit();

            // Practice mode (testRun) should disable manual results for non-exam mode
            expect(comp.newManualResultAllowed).toBeFalse();
        });
    });

    describe('getAssessmentLink', () => {
        it('should return undefined when exercise type is missing', () => {
            comp.exercise = { id: 1 } as Exercise;

            const result = comp.getAssessmentLink();

            expect(result).toBeUndefined();
        });

        it('should return undefined when submission id is missing', () => {
            comp.participation = { id: 1, submissions: [] } as Participation;

            const result = comp.getAssessmentLink();

            expect(result).toBeUndefined();
        });

        it('should return assessment link for valid exercise', () => {
            comp.exercise = { id: 1, type: ExerciseType.TEXT } as Exercise;
            comp.participation = {
                id: 1,
                submissions: [{ id: 1, results: [{ id: 1 }] } as Submission],
            } as Participation;

            const result = comp.getAssessmentLink();

            expect(result).toBeDefined();
        });
    });

    describe('getCorrectionRoundForAssessmentLink', () => {
        it('should return correctionRound when no result exists', () => {
            comp.participation = { id: 1, submissions: [{ id: 1, results: [] } as Submission] } as Participation;

            const result = comp.getCorrectionRoundForAssessmentLink(0);

            expect(result).toBe(0);
        });

        it('should return next correction round when complaint was accepted', () => {
            comp.participation = {
                id: 1,
                submissions: [
                    {
                        id: 1,
                        results: [{ id: 1, hasComplaint: true } as Result, { id: 2 } as Result],
                    } as Submission,
                ],
            } as Participation;

            const result = comp.getCorrectionRoundForAssessmentLink(0);

            expect(result).toBe(1);
        });

        it('should return same correction round when no complaint', () => {
            comp.participation = {
                id: 1,
                submissions: [{ id: 1, results: [{ id: 1, hasComplaint: false } as Result] } as Submission],
            } as Participation;

            const result = comp.getCorrectionRoundForAssessmentLink(0);

            expect(result).toBe(0);
        });
    });

    describe('cancelAssessment', () => {
        beforeEach(() => {
            jest.spyOn(window, 'confirm').mockReturnValue(true);
        });

        afterEach(() => {
            jest.restoreAllMocks();
        });

        it('should cancel programming assessment', () => {
            const cancelSpy = jest.spyOn(programmingAssessmentService, 'cancelAssessment').mockReturnValue(of(undefined));
            const refreshSpy = jest.spyOn(comp.refresh, 'emit');
            comp.exercise = { ...exercise, type: ExerciseType.PROGRAMMING } as Exercise;
            const result = { id: 1, submission: { id: 1 } } as Result;

            comp.cancelAssessment(result, comp.participation);

            expect(cancelSpy).toHaveBeenCalledWith(1);
            expect(refreshSpy).toHaveBeenCalled();
        });

        it('should cancel modeling assessment', () => {
            const cancelSpy = jest.spyOn(modelingAssessmentService, 'cancelAssessment').mockReturnValue(of(undefined));
            const refreshSpy = jest.spyOn(comp.refresh, 'emit');
            comp.exercise = { ...exercise, type: ExerciseType.MODELING } as Exercise;
            const result = { id: 1, submission: { id: 1 } } as Result;

            comp.cancelAssessment(result, comp.participation);

            expect(cancelSpy).toHaveBeenCalledWith(1);
            expect(refreshSpy).toHaveBeenCalled();
        });

        it('should cancel text assessment', () => {
            const cancelSpy = jest.spyOn(textAssessmentService, 'cancelAssessment').mockReturnValue(of(undefined));
            const refreshSpy = jest.spyOn(comp.refresh, 'emit');
            comp.exercise = { ...exercise, type: ExerciseType.TEXT } as Exercise;
            const result = { id: 1, submission: { id: 1 } } as Result;

            comp.cancelAssessment(result, comp.participation);

            expect(cancelSpy).toHaveBeenCalledWith(1, 1);
            expect(refreshSpy).toHaveBeenCalled();
        });

        it('should cancel file upload assessment', () => {
            const cancelSpy = jest.spyOn(fileUploadAssessmentService, 'cancelAssessment').mockReturnValue(of(undefined));
            const refreshSpy = jest.spyOn(comp.refresh, 'emit');
            comp.exercise = { ...exercise, type: ExerciseType.FILE_UPLOAD } as Exercise;
            const result = { id: 1, submission: { id: 1 } } as Result;

            comp.cancelAssessment(result, comp.participation);

            expect(cancelSpy).toHaveBeenCalledWith(1);
            expect(refreshSpy).toHaveBeenCalled();
        });

        it('should not cancel when user declines confirmation', () => {
            jest.spyOn(window, 'confirm').mockReturnValue(false);
            const cancelSpy = jest.spyOn(programmingAssessmentService, 'cancelAssessment');
            const result = { id: 1, submission: { id: 1 } } as Result;

            comp.cancelAssessment(result, comp.participation);

            expect(cancelSpy).not.toHaveBeenCalled();
        });

        it('should not cancel when submission id is missing', () => {
            const cancelSpy = jest.spyOn(programmingAssessmentService, 'cancelAssessment');
            const result = { id: 1, submission: undefined } as Result;

            comp.cancelAssessment(result, comp.participation);

            expect(cancelSpy).not.toHaveBeenCalled();
        });
    });
});
