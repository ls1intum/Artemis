import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { PlagiarismComparison } from 'app/plagiarism/shared/entities/PlagiarismComparison';
import { of } from 'rxjs';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { downloadFile } from 'app/shared/util/download.util';
import { Range } from 'app/shared/util/utils';
import { PlagiarismStatus } from 'app/plagiarism/shared/entities/PlagiarismStatus';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { PlagiarismResultDTO } from 'app/plagiarism/shared/entities/PlagiarismResultDTO';
import { generateCsv } from 'export-to-csv';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { PlagiarismInspectorComponent } from 'app/plagiarism/manage/plagiarism-inspector/plagiarism-inspector.component';
import { PlagiarismInspectorService } from 'app/plagiarism/manage/plagiarism-inspector/plagiarism-inspector.service';
import { PlagiarismResult } from 'app/plagiarism/shared/entities/PlagiarismResult';

jest.mock('app/shared/util/download.util', () => ({
    downloadFile: jest.fn(),
}));

jest.mock('export-to-csv', () => {
    return {
        mkConfig: jest.fn(),
        download: jest.fn(() => jest.fn()),
        generateCsv: jest.fn(() => jest.fn()),
    };
});

describe('Plagiarism Inspector Component', () => {
    let comp: PlagiarismInspectorComponent;
    let fixture: ComponentFixture<PlagiarismInspectorComponent>;
    let programmingExerciseService: ProgrammingExerciseService;
    let textExerciseService: TextExerciseService;
    let inspectorService: PlagiarismInspectorService;
    let plagiarismCasesService: PlagiarismCasesService;
    let modalService: NgbModal;

    const modelingExercise = { id: 123, type: ExerciseType.MODELING } as ModelingExercise;
    const textExercise = { id: 234, type: ExerciseType.TEXT } as TextExercise;
    const programmingExercise = { id: 345, type: ExerciseType.PROGRAMMING } as ProgrammingExercise;
    const activatedRoute = {
        data: of({ exercise: modelingExercise }),
    };
    const comparisons = [
        {
            id: 1,
            submissionA: { studentLogin: 'student1A' },
            submissionB: { studentLogin: 'student1B' },
            similarity: 0.5,
            status: PlagiarismStatus.NONE,
        },
        {
            id: 2,
            submissionA: { studentLogin: 'student2A' },
            submissionB: { studentLogin: 'student2B' },
            similarity: 0.8,
            status: PlagiarismStatus.NONE,
        },
        {
            id: 3,
            submissionA: { studentLogin: 'student3A' },
            submissionB: { studentLogin: 'student3B' },
            similarity: 0.7,
            status: PlagiarismStatus.NONE,
        },
    ];

    const textPlagiarismResult = {
        id: 123,
        comparisons,
    } as PlagiarismResult;
    const textPlagiarismResultDTO = {
        plagiarismResult: textPlagiarismResult,
        plagiarismResultStats: {},
    } as PlagiarismResultDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: activatedRoute },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PlagiarismInspectorComponent);
                comp = fixture.componentInstance;
                programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
                textExerciseService = TestBed.inject(TextExerciseService);
                inspectorService = TestBed.inject(PlagiarismInspectorService);
                plagiarismCasesService = TestBed.inject(PlagiarismCasesService);
                modalService = TestBed.inject(NgbModal);
            });
    });

    it('should return the correct topic url', () => {
        const exerciseTypes = [ExerciseType.PROGRAMMING, ExerciseType.TEXT];
        exerciseTypes.forEach((exerciseType) => {
            comp.exercise = { id: 1, type: exerciseType } as Exercise;
            expect(comp.getPlagarismDetectionTopic()).toBe(`/topic/${exerciseType}-exercises/1/plagiarism-check`);
        });
    });

    it('should get the minimumSize tooltip for programming', () => {
        comp.exercise = { type: ExerciseType.PROGRAMMING } as Exercise;

        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumTokenCountTooltipProgrammingExercise');
    });

    it('should get the minimumSize tootip for text', () => {
        comp.exercise = { type: ExerciseType.TEXT } as Exercise;

        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipTextExercise');
    });

    it('should get the minimumSize label for programming', () => {
        comp.exercise = { type: ExerciseType.PROGRAMMING } as Exercise;
        expect(comp.getMinimumSizeLabel()).toBe('artemisApp.plagiarism.minimumTokenCount');
    });

    it('should get the minimumSize label for text', () => {
        comp.exercise = { type: ExerciseType.TEXT } as Exercise;
        expect(comp.getMinimumSizeLabel()).toBe('artemisApp.plagiarism.minimumSize');
    });

    it('should fetch the plagiarism detection results for programming exercises', () => {
        comp.exercise = programmingExercise;
        jest.spyOn(programmingExerciseService, 'checkPlagiarism').mockReturnValue(of(textPlagiarismResultDTO));

        comp.checkPlagiarism();

        expect(programmingExerciseService.checkPlagiarism).toHaveBeenCalledOnce();
    });

    it('should fetch the plagiarism detection results for text exercises', () => {
        comp.exercise = textExercise;
        jest.spyOn(textExerciseService, 'checkPlagiarism').mockReturnValue(of(textPlagiarismResultDTO));

        comp.checkPlagiarism();

        expect(textExerciseService.checkPlagiarism).toHaveBeenCalledOnce();
    });

    it('should select a comparison at the given index', () => {
        comp.selectedComparisonId = 0;
        comp.selectComparisonWithID(1);

        expect(comp.selectedComparisonId).toBe(1);
    });

    it('should download the plagiarism detection results as JSON', () => {
        comp.exercise = textExercise;
        comp.plagiarismResult = textPlagiarismResult;
        comp.downloadPlagiarismResultsJson();

        expect(downloadFile).toHaveBeenCalledOnce();
    });

    it('should download the plagiarism detection results as CSV', () => {
        comp.exercise = textExercise;
        comp.plagiarismResult = textPlagiarismResult;
        comp.downloadPlagiarismResultsCsv();

        expect(generateCsv).toHaveBeenCalledOnce();
    });

    it('should get the latest plagiarism result for programming exercise', fakeAsync(() => {
        comp.exercise = programmingExercise;

        jest.spyOn(programmingExerciseService, 'getLatestPlagiarismResult').mockReturnValue(of(textPlagiarismResultDTO));
        jest.spyOn(comp, 'handlePlagiarismResult');

        comp.getLatestPlagiarismResult();
        expect(comp.detectionInProgress).toBeFalse();

        tick();

        expect(programmingExerciseService.getLatestPlagiarismResult).toHaveBeenCalledWith(programmingExercise.id);
        expect(comp.handlePlagiarismResult).toHaveBeenCalledWith(textPlagiarismResultDTO);
    }));

    it('should get the latest plagiarism result for text exercise', fakeAsync(() => {
        comp.exercise = textExercise;

        jest.spyOn(textExerciseService, 'getLatestPlagiarismResult').mockReturnValue(of(textPlagiarismResultDTO));
        jest.spyOn(comp, 'handlePlagiarismResult');

        comp.getLatestPlagiarismResult();
        expect(comp.detectionInProgress).toBeFalse();

        tick();

        expect(textExerciseService.getLatestPlagiarismResult).toHaveBeenCalledWith(textExercise.id);
        expect(comp.handlePlagiarismResult).toHaveBeenCalledWith(textPlagiarismResultDTO);
    }));

    it('should be programming exercise', () => {
        comp.exercise = { type: ExerciseType.PROGRAMMING } as ProgrammingExercise;

        expect(comp.isProgrammingExercise()).toBeTrue();
    });

    it('should not be programming exercise', () => {
        comp.exercise = { type: ExerciseType.TEXT } as TextExercise;

        expect(comp.isProgrammingExercise()).toBeFalse();
    });

    it('should trigger similarity distribution', () => {
        const getLatestPlagiarismResultStub = jest.spyOn(comp, 'getLatestPlagiarismResult').mockImplementation();
        const resetFilterStub = jest.spyOn(comp, 'resetFilter').mockImplementation();

        comp.showSimilarityDistribution(true);

        expect(resetFilterStub).toHaveBeenCalledOnce();
        expect(getLatestPlagiarismResultStub).toHaveBeenCalledOnce();
        expect(comp.showRunDetails).toBeTrue();
    });

    describe('test chart interactivity', () => {
        it('should apply filter and reset it', () => {
            const filterComparisonsMock = jest.spyOn(inspectorService, 'filterComparisons').mockReturnValue([]);
            const range = new Range(20, 30);
            comp.plagiarismResult = textPlagiarismResult;

            comp.filterByChart(range);

            expect(filterComparisonsMock).toHaveBeenCalledOnce();
            expect(filterComparisonsMock).toHaveBeenCalledWith(range, comparisons);
            expect(comp.visibleComparisons).toEqual([]);
            expect(comp.sidebarOffset).toBe(0);
            expect(comp.chartFilterApplied).toBeTrue();

            comp.resetFilter();

            expect(comp.visibleComparisons).toEqual(comparisons);
            expect(comp.sidebarOffset).toBe(0);
            expect(comp.chartFilterApplied).toBeFalse();
        });

        it('should return the selected comparison', () => {
            comp.selectedComparisonId = 2;
            comp.visibleComparisons = comparisons as PlagiarismComparison[];
            const expected = {
                id: 2,
                submissionA: { studentLogin: 'student2A' },
                submissionB: { studentLogin: 'student2B' },
                similarity: 0.8,
                status: PlagiarismStatus.NONE,
            };

            const selected = comp.getSelectedComparison();

            expect(selected).toEqual(expected);
        });
    });

    it('should clean up plagiarism', fakeAsync(() => {
        const cleanUpPlagiarismSpy = jest.spyOn(plagiarismCasesService, 'cleanUpPlagiarism').mockReturnValue(of(new HttpResponse<void>()));
        const getLatestPlagiarismResultSpy = jest.spyOn(comp, 'getLatestPlagiarismResult');
        comp.exercise = textExercise;
        comp.plagiarismResult = textPlagiarismResult;

        comp.cleanUpPlagiarism();

        tick();

        expect(cleanUpPlagiarismSpy).toHaveBeenCalledWith(textExercise.id, textPlagiarismResult.id, false);
        expect(getLatestPlagiarismResultSpy).toHaveBeenCalledOnce();
        expect(comp.deleteAllPlagiarismComparisons).toBeFalse();
    }));

    it('should clean up plagiarism and delete all plagiarism comparisons', fakeAsync(() => {
        const cleanUpPlagiarismSpy = jest.spyOn(plagiarismCasesService, 'cleanUpPlagiarism').mockReturnValue(of(new HttpResponse<void>()));
        comp.exercise = textExercise;
        comp.plagiarismResult = textPlagiarismResult;
        comp.deleteAllPlagiarismComparisons = true;

        comp.cleanUpPlagiarism();

        tick();

        expect(cleanUpPlagiarismSpy).toHaveBeenCalledWith(textExercise.id, textPlagiarismResult.id, true);
        expect(comp.deleteAllPlagiarismComparisons).toBeFalse();
        expect(comp.plagiarismResult).toBeUndefined();
    }));

    it('should call cleanUpPlagiarism on confirm modal', fakeAsync(() => {
        const cleanUpPlagiarismSpy = jest.spyOn(comp, 'cleanUpPlagiarism');
        const mockReturnValue = { result: Promise.resolve('confirm') } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);
        comp.exercise = textExercise;
        comp.plagiarismResult = textPlagiarismResult;

        comp.openCleanUpModal(undefined);

        tick();

        expect(cleanUpPlagiarismSpy).toHaveBeenCalledOnce();
    }));

    it('should handle plagiarism check state change when completed', () => {
        comp.exercise = textExercise;
        const getLatestPlagiarismResultSpy = jest.spyOn(comp, 'getLatestPlagiarismResult').mockImplementation();
        const checkState = { state: 'COMPLETED' as const, messages: 'Plagiarism check completed' };

        comp.handlePlagiarismCheckStateChange(checkState);

        expect(comp.detectionInProgress).toBeFalsy();
        expect(comp.detectionInProgressMessage).toBe('artemisApp.plagiarism.fetchingResults');
        expect(getLatestPlagiarismResultSpy).toHaveBeenCalled();
    });

    it('should handle plagiarism check state change when running', () => {
        const checkState = { state: 'RUNNING' as const, messages: 'Plagiarism check running' };

        comp.handlePlagiarismCheckStateChange(checkState);

        expect(comp.detectionInProgress).toBeTruthy();
        expect(comp.detectionInProgressMessage).toBe('Plagiarism check running');
    });

    it('should handle error', () => {
        comp.handleError();

        expect(comp.detectionInProgress).toBeFalsy();
    });

    it('should toggle delete all plagiarism comparisons', () => {
        comp.deleteAllPlagiarismComparisons = false;

        comp.toggleDeleteAllPlagiarismComparisons();

        expect(comp.deleteAllPlagiarismComparisons).toBeTruthy();

        comp.toggleDeleteAllPlagiarismComparisons();

        expect(comp.deleteAllPlagiarismComparisons).toBeFalsy();
    });

    it('should check plagiarism JPlag report for programming exercise', () => {
        comp.exercise = programmingExercise;
        const checkPlagiarismJPlagReportSpy = jest.spyOn(programmingExerciseService, 'checkPlagiarismJPlagReport').mockReturnValue(of(new HttpResponse<Blob>()));

        comp.checkPlagiarismJPlagReport();

        expect(checkPlagiarismJPlagReportSpy).toHaveBeenCalled();
    });

    it('should sort comparisons for result', () => {
        const unsortedResult = Object.assign({}, textPlagiarismResult, {
            comparisons: [
                { id: 1, similarity: 0.5 },
                { id: 3, similarity: 0.9 },
                { id: 2, similarity: 0.7 },
            ] as PlagiarismComparison[],
        });

        comp.sortComparisonsForResult(unsortedResult);

        expect(unsortedResult.comparisons[0].similarity).toBe(0.9);
        expect(unsortedResult.comparisons[1].similarity).toBe(0.7);
        expect(unsortedResult.comparisons[2].similarity).toBe(0.5);
    });

    it('should handle plagiarism result', () => {
        const sortComparisonsForResultSpy = jest.spyOn(comp, 'sortComparisonsForResult');

        comp.handlePlagiarismResult(textPlagiarismResultDTO);

        expect(comp.plagiarismResult).toBe(textPlagiarismResultDTO.plagiarismResult);
        expect(comp.plagiarismResultStats).toBe(textPlagiarismResultDTO.plagiarismResultStats);
        expect(comp.visibleComparisons).toBe(textPlagiarismResultDTO.plagiarismResult.comparisons);
        expect(sortComparisonsForResultSpy).toHaveBeenCalledWith(textPlagiarismResultDTO.plagiarismResult);
    });

    it('should return undefined when no comparison is selected', () => {
        comp.selectedComparisonId = -1;
        comp.visibleComparisons = comparisons as PlagiarismComparison[];

        const selected = comp.getSelectedComparison();

        expect(selected).toBeUndefined();
    });
});
