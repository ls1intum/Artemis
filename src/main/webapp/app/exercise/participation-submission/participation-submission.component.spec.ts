import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';

import { ParticipationSubmissionComponent } from 'app/exercise/participation-submission/participation-submission.component';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { FileUploadAssessmentService } from 'app/fileupload/manage/assess/file-upload-assessment.service';
import { ProgrammingAssessmentManualResultService } from 'app/programming/manage/assess/manual-result/programming-assessment-manual-result.service';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';

import { Submission, SubmissionExerciseType, SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ParticipationSubmissionComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: ParticipationSubmissionComponent;
    let fixture: ComponentFixture<ParticipationSubmissionComponent>;
    let participationService: ParticipationService;
    let submissionService: SubmissionService;
    let textAssessmentService: TextAssessmentService;
    let fileUploadAssessmentService: FileUploadAssessmentService;
    let programmingAssessmentService: ProgrammingAssessmentManualResultService;
    let modelingAssessmentService: ModelingAssessmentService;
    let exerciseService: ExerciseService;
    let programmingExerciseService: ProgrammingExerciseService;
    let findAllSubmissionsOfParticipationStub: ReturnType<typeof vi.spyOn>;

    function createSubmissionFixture(submissionId: number) {
        const result1 = { id: 44 } as Result;
        const result2 = { id: 45 } as Result;
        const participation = { id: 66 } as Participation;
        const submission = { id: submissionId, results: [result1, result2], participation } as Submission;
        return { result1, result2, participation, submission };
    }

    const programmingExercise1 = { id: 100, type: ExerciseType.PROGRAMMING } as Exercise;
    const modelingExercise = { id: 100, type: ExerciseType.MODELING } as Exercise;
    const fileUploadExercise = { id: 100, type: ExerciseType.FILE_UPLOAD } as Exercise;
    const textExercise = { id: 100, type: ExerciseType.TEXT } as Exercise;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: { params: of({ participationId: 1, exerciseId: 42 }) } },
                MockProvider(ParticipationService),
                MockProvider(SubmissionService),
                MockProvider(ExerciseService),
                MockProvider(ProgrammingExerciseService),
                MockProvider(TextAssessmentService),
                MockProvider(FileUploadAssessmentService),
                MockProvider(ProgrammingAssessmentManualResultService),
                MockProvider(ModelingAssessmentService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ParticipationSubmissionComponent);
                comp = fixture.componentInstance;
                participationService = TestBed.inject(ParticipationService);
                submissionService = TestBed.inject(SubmissionService);
                textAssessmentService = TestBed.inject(TextAssessmentService);
                modelingAssessmentService = TestBed.inject(ModelingAssessmentService);
                programmingAssessmentService = TestBed.inject(ProgrammingAssessmentManualResultService);
                fileUploadAssessmentService = TestBed.inject(FileUploadAssessmentService);
                exerciseService = TestBed.inject(ExerciseService);
                programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
                findAllSubmissionsOfParticipationStub = vi.spyOn(submissionService, 'findAllSubmissionsOfParticipation');
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('Submissions are correctly loaded from server', () => {
        const participation = new StudentParticipation();
        participation.id = 1;
        vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
        const submissions = [
            {
                submissionExerciseType: SubmissionExerciseType.TEXT,
                id: 2278,
                submitted: true,
                type: SubmissionType.MANUAL,
                submissionDate: dayjs('2019-07-09T10:47:33.244Z'),
                text: 'My TextSubmission',
                participation,
            },
        ] as TextSubmission[];
        const exercise = { type: ExerciseType.TEXT } as Exercise;
        exercise.isAtLeastInstructor = true;
        vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));
        findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: submissions }));
        vi.spyOn(participationService, 'getBuildJobIdsForResultsOfParticipation').mockReturnValue(of({ '4': '2' }));

        comp.ngOnInit();

        expect(comp.isLoading()).toBe(false);
        expect(findAllSubmissionsOfParticipationStub).toHaveBeenCalledOnce();
        expect(comp.participation()).toEqual(participation);
        expect(comp.submissions()).toEqual(submissions);
        expect(comp.participation()?.submissions).toEqual(submissions);
    });

    it('Template Submission is correctly loaded', () => {
        const route = TestBed.inject(ActivatedRoute);
        route.params = of({ participationId: 2, exerciseId: 42 });
        route.queryParams = of({ isTmpOrSolutionProgrParticipation: 'true' });

        const templateParticipation = new TemplateProgrammingExerciseParticipation();
        templateParticipation.id = 2;
        templateParticipation.submissions = [
            {
                submissionExerciseType: SubmissionExerciseType.PROGRAMMING,
                id: 3,
                submitted: true,
                type: SubmissionType.MANUAL,
                submissionDate: dayjs('2019-07-09T10:47:33.244Z'),
                commitHash: '123456789',
                participation: templateParticipation,
            },
        ] as ProgrammingSubmission[];
        const programmingExercise = { type: ExerciseType.PROGRAMMING, projectKey: 'SUBMISSION1', templateParticipation } as ProgrammingExercise;
        const findWithTemplateAndSolutionParticipationStub = vi
            .spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipation')
            .mockReturnValue(of(new HttpResponse({ body: programmingExercise })));
        vi.spyOn(participationService, 'getBuildJobIdsForResultsOfParticipation').mockReturnValue(of({ '4': '2' }));

        comp.ngOnInit();

        expect(comp.isLoading()).toBe(false);
        expect(findWithTemplateAndSolutionParticipationStub).toHaveBeenCalledOnce();
        expect(comp.exercise()).toEqual(programmingExercise);
        expect(comp.participation()).toEqual(templateParticipation);
        expect(comp.submissions()).toEqual(templateParticipation.submissions);
    });

    it('Solution Submission is correctly loaded', () => {
        const route = TestBed.inject(ActivatedRoute);
        route.params = of({ participationId: 3, exerciseId: 42 });
        route.queryParams = of({ isTmpOrSolutionProgrParticipation: 'true' });

        const solutionParticipation = new SolutionProgrammingExerciseParticipation();
        solutionParticipation.id = 3;
        solutionParticipation.submissions = [
            {
                submissionExerciseType: SubmissionExerciseType.PROGRAMMING,
                id: 4,
                submitted: true,
                type: SubmissionType.MANUAL,
                submissionDate: dayjs('2019-07-09T10:47:33.244Z'),
                commitHash: '123456789',
                participation: solutionParticipation,
            },
        ] as ProgrammingSubmission[];
        const programmingExercise = { type: ExerciseType.PROGRAMMING, projectKey: 'SUBMISSION1', solutionParticipation } as ProgrammingExercise;
        const findWithTemplateAndSolutionParticipationStub = vi
            .spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipation')
            .mockReturnValue(of(new HttpResponse({ body: programmingExercise })));
        vi.spyOn(participationService, 'getBuildJobIdsForResultsOfParticipation').mockReturnValue(of({ '4': '2' }));

        comp.ngOnInit();

        expect(comp.isLoading()).toBe(false);
        expect(findWithTemplateAndSolutionParticipationStub).toHaveBeenCalledOnce();
        expect(comp.participation()).toEqual(solutionParticipation);
        expect(comp.submissions()).toEqual(solutionParticipation.submissions);
    });

    describe('should delete', () => {
        beforeEach(() => {
            vi.spyOn(fileUploadAssessmentService, 'deleteAssessment').mockReturnValue(of(void 0));
            vi.spyOn(textAssessmentService, 'deleteAssessment').mockReturnValue(of(void 0));
            vi.spyOn(modelingAssessmentService, 'deleteAssessment').mockReturnValue(of(void 0));
            vi.spyOn(programmingAssessmentService, 'deleteAssessment').mockReturnValue(of(void 0));
            vi.spyOn(participationService, 'getBuildJobIdsForResultsOfParticipation').mockReturnValue(of({ '4': '2' }));
        });

        it('should delete result of fileUploadSubmission', () => {
            const { submission, participation } = createSubmissionFixture(77);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: fileUploadExercise })));
            comp.ngOnInit();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(1);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });

        it('should delete result of modelingSubmission', () => {
            const { submission, participation } = createSubmissionFixture(77);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: modelingExercise })));
            comp.ngOnInit();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(1);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });

        it('should delete result of programmingSubmission', () => {
            const { submission, participation } = createSubmissionFixture(77);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: programmingExercise1 })));
            comp.ngOnInit();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(1);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });

        it('should delete result of textSubmission', () => {
            const { submission, participation } = createSubmissionFixture(77);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: textExercise })));
            comp.ngOnInit();
            expect(findAllSubmissionsOfParticipationStub).toHaveBeenCalledOnce();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(1);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });
    });

    describe('should handle failed delete', () => {
        beforeEach(() => {
            const error = { message: '400 error', error: { message: 'error.hasComplaint' } } as HttpErrorResponse;
            vi.spyOn(fileUploadAssessmentService, 'deleteAssessment').mockReturnValue(throwError(() => error));
            vi.spyOn(programmingAssessmentService, 'deleteAssessment').mockReturnValue(throwError(() => error));
            vi.spyOn(modelingAssessmentService, 'deleteAssessment').mockReturnValue(throwError(() => error));
            vi.spyOn(textAssessmentService, 'deleteAssessment').mockReturnValue(throwError(() => error));
            vi.spyOn(participationService, 'getBuildJobIdsForResultsOfParticipation').mockReturnValue(of({ '4': '2' }));
        });

        it('should not delete result of fileUploadSubmission because of server error', () => {
            const { submission, participation } = createSubmissionFixture(78);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            const error2 = { message: '403 error', error: { message: 'error.badAuthentication' } } as HttpErrorResponse;
            vi.spyOn(fileUploadAssessmentService, 'deleteAssessment').mockReturnValue(throwError(() => error2));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: fileUploadExercise })));
            comp.ngOnInit();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(2);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });

        it('should not delete result of fileUploadSubmission', () => {
            const { submission, participation } = createSubmissionFixture(78);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: fileUploadExercise })));
            comp.ngOnInit();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(2);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });

        it('should not delete result of modelingSubmission', () => {
            const { submission, participation } = createSubmissionFixture(78);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: modelingExercise })));
            comp.ngOnInit();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(2);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });

        it('should not delete result of programmingSubmission', () => {
            const { submission, participation } = createSubmissionFixture(78);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: programmingExercise1 })));
            comp.ngOnInit();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(2);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });

        it('should not delete result of textSubmission', () => {
            const { submission, participation } = createSubmissionFixture(78);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: textExercise })));
            comp.ngOnInit();
            expect(findAllSubmissionsOfParticipationStub).toHaveBeenCalledOnce();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(2);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });
    });
});
