import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ExportToCsv } from 'export-to-csv';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { PlagiarismCheckState, PlagiarismInspectorComponent } from 'app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ArtemisTestModule } from '../../test.module';
import { downloadFile } from 'app/shared/util/download.util';
import { Range } from 'app/shared/util/utils';
import { ModelingPlagiarismResult } from 'app/exercises/shared/plagiarism/types/modeling/ModelingPlagiarismResult';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { TextPlagiarismResult } from 'app/exercises/shared/plagiarism/types/text/TextPlagiarismResult';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { PlagiarismDetailsComponent } from 'app/exercises/shared/plagiarism/plagiarism-details/plagiarism-details.component';
import { PlagiarismRunDetailsComponent } from 'app/exercises/shared/plagiarism/plagiarism-run-details/plagiarism-run-details.component';
import { PlagiarismSidebarComponent } from 'app/exercises/shared/plagiarism/plagiarism-sidebar/plagiarism-sidebar.component';
import { NgbModal, NgbModalRef, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgModel } from '@angular/forms';
import { PlagiarismInspectorService } from 'app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.service';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { HttpResponse } from '@angular/common/http';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';

jest.mock('app/shared/util/download.util', () => ({
    downloadFile: jest.fn(),
}));

const generateCsv = jest.fn();

jest.mock('export-to-csv', () => ({
    ExportToCsv: jest.fn().mockImplementation(() => ({
        generateCsv,
    })),
}));

describe('Plagiarism Inspector Component', () => {
    let comp: PlagiarismInspectorComponent;
    let fixture: ComponentFixture<PlagiarismInspectorComponent>;
    let modelingExerciseService: ModelingExerciseService;
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
    const modelingPlagiarismResult = {
        comparisons,
    } as ModelingPlagiarismResult;

    const textPlagiarismResult = {
        id: 123,
        comparisons,
    } as TextPlagiarismResult;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                PlagiarismInspectorComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
                MockDirective(NgModel),
                MockComponent(PlagiarismDetailsComponent),
                MockComponent(PlagiarismRunDetailsComponent),
                MockComponent(PlagiarismSidebarComponent),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: activatedRoute },
                { provide: NgbModal, useClass: MockNgbModalService },
                MockProvider(JhiWebsocketService),
                MockProvider(TranslateService),
                MockProvider(PlagiarismInspectorService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PlagiarismInspectorComponent);
                comp = fixture.componentInstance;
                modelingExerciseService = TestBed.inject(ModelingExerciseService);
                programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
                textExerciseService = fixture.debugElement.injector.get(TextExerciseService);
                inspectorService = TestBed.inject(PlagiarismInspectorService);
                plagiarismCasesService = TestBed.inject(PlagiarismCasesService);
                modalService = TestBed.inject(NgbModal);
            });
    });

    it('should register to topic and fetch latest results on init', fakeAsync(() => {
        const websocketService = TestBed.inject(JhiWebsocketService);
        const websocketServiceSpy = jest.spyOn(websocketService, 'subscribe');
        jest.spyOn(websocketService, 'receive').mockReturnValue(of({ state: 'COMPLETED', messages: 'a message' } as PlagiarismCheckState));
        jest.spyOn(modelingExerciseService, 'getLatestPlagiarismResult').mockReturnValue(of(modelingPlagiarismResult));

        comp.ngOnInit();
        tick();

        expect(websocketServiceSpy).toHaveBeenCalledWith(comp.getPlagarismDetectionTopic());
        expect(comp.getPlagarismDetectionTopic()).toBe(`/topic/modeling-exercises/${modelingExercise.id}/plagiarism-check`);
        expect(comp.detectionInProgress).toBeFalse();
        expect(comp.plagiarismResult).toBe(modelingPlagiarismResult);
    }));

    it('should return the correct topic url', () => {
        const exerciseTypes = [ExerciseType.PROGRAMMING, ExerciseType.TEXT, ExerciseType.MODELING];
        exerciseTypes.forEach((exerciseType) => {
            comp.exercise = { id: 1, type: exerciseType } as Exercise;
            expect(comp.getPlagarismDetectionTopic()).toBe(`/topic/${exerciseType}-exercises/1/plagiarism-check`);
        });
    });

    it('should get the minimumSize tootip', () => {
        comp.exercise = { type: ExerciseType.PROGRAMMING } as Exercise;

        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltip');
    });

    it('should get the minimumSize tootip for modeling', () => {
        comp.exercise = { type: ExerciseType.MODELING } as Exercise;

        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipModeling');
    });

    it('should get the minimumSize tootip for text', () => {
        comp.exercise = { type: ExerciseType.TEXT } as Exercise;

        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipText');
    });

    it('should fetch the plagiarism detection results for modeling exercises', () => {
        comp.exercise = modelingExercise;
        jest.spyOn(modelingExerciseService, 'checkPlagiarism').mockReturnValue(of(modelingPlagiarismResult));

        comp.checkPlagiarism();

        expect(modelingExerciseService.checkPlagiarism).toHaveBeenCalledOnce();
    });

    it('should fetch the plagiarism detection results for programming exercises', () => {
        comp.exercise = programmingExercise;
        jest.spyOn(programmingExerciseService, 'checkPlagiarism').mockReturnValue(of(textPlagiarismResult));

        comp.checkPlagiarism();

        expect(programmingExerciseService.checkPlagiarism).toHaveBeenCalledOnce();
    });

    it('should fetch the plagiarism detection results for text exercises', () => {
        comp.exercise = textExercise;
        jest.spyOn(textExerciseService, 'checkPlagiarism').mockReturnValue(of(textPlagiarismResult));

        comp.checkPlagiarism();

        expect(textExerciseService.checkPlagiarism).toHaveBeenCalledOnce();
    });

    it('should comparisons by similarity', () => {
        comp.sortComparisonsForResult(modelingPlagiarismResult);

        expect(modelingPlagiarismResult.comparisons[0].similarity).toBe(0.8);
    });

    it('should select a comparison at the given index', () => {
        comp.selectedComparisonId = 0;
        comp.selectComparisonWithID(1);

        expect(comp.selectedComparisonId).toBe(1);
    });

    it('should download the plagiarism detection results as JSON', () => {
        comp.exercise = modelingExercise;
        comp.plagiarismResult = modelingPlagiarismResult;
        comp.downloadPlagiarismResultsJson();

        expect(downloadFile).toHaveBeenCalledOnce();
    });

    it('should download the plagiarism detection results as CSV', () => {
        comp.exercise = modelingExercise;
        comp.plagiarismResult = modelingPlagiarismResult;
        comp.downloadPlagiarismResultsCsv();

        expect(ExportToCsv).toHaveBeenCalledOnce();
        expect(generateCsv).toHaveBeenCalledOnce();
    });

    it('should get the latest plagiarism result for modeling exercise', fakeAsync(() => {
        comp.exercise = modelingExercise;

        jest.spyOn(modelingExerciseService, 'getLatestPlagiarismResult').mockReturnValue(of(modelingPlagiarismResult));
        jest.spyOn(comp, 'handlePlagiarismResult');

        comp.getLatestPlagiarismResult();
        expect(comp.detectionInProgress).toBeFalse();

        tick();

        expect(modelingExerciseService.getLatestPlagiarismResult).toHaveBeenCalledWith(modelingExercise.id);
        expect(comp.handlePlagiarismResult).toHaveBeenCalledWith(modelingPlagiarismResult);
    }));

    it('should get the latest plagiarism result for programming exercise', fakeAsync(() => {
        comp.exercise = programmingExercise;

        jest.spyOn(programmingExerciseService, 'getLatestPlagiarismResult').mockReturnValue(of(textPlagiarismResult));
        jest.spyOn(comp, 'handlePlagiarismResult');

        comp.getLatestPlagiarismResult();
        expect(comp.detectionInProgress).toBeFalse();

        tick();

        expect(programmingExerciseService.getLatestPlagiarismResult).toHaveBeenCalledWith(programmingExercise.id);
        expect(comp.handlePlagiarismResult).toHaveBeenCalledWith(textPlagiarismResult);
    }));

    it('should get the latest plagiarism result for text exercise', fakeAsync(() => {
        comp.exercise = textExercise;

        jest.spyOn(textExerciseService, 'getLatestPlagiarismResult').mockReturnValue(of(textPlagiarismResult));
        jest.spyOn(comp, 'handlePlagiarismResult');

        comp.getLatestPlagiarismResult();
        expect(comp.detectionInProgress).toBeFalse();

        tick();

        expect(textExerciseService.getLatestPlagiarismResult).toHaveBeenCalledWith(textExercise.id);
        expect(comp.handlePlagiarismResult).toHaveBeenCalledWith(textPlagiarismResult);
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
            comp.visibleComparisons = comparisons as PlagiarismComparison<TextSubmissionElement>[];
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
});
