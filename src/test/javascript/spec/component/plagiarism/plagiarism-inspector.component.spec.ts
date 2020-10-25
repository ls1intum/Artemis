import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ExportToCsv } from 'export-to-csv';
import { ModelingExerciseService, ModelingSubmissionComparisonDTO } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { PlagiarismInspectorComponent } from 'app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component';
import { PlagiarismHeaderComponent } from 'app/exercises/shared/plagiarism/plagiarism-header/plagiarism-header.component';
import { PlagiarismSplitViewComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/plagiarism-split-view.component';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';
import { downloadFile } from 'app/shared/util/download.util';

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

    const modelingComparisons = [
        {
            similarity: 0.5,
            element1: {
                studentLogin: 'ab10cde',
                submissionId: 1,
                score: 10,
                size: 5,
            },
            element2: {
                studentLogin: 'ab20cde',
                submissionId: 2,
                score: 10,
                size: 6,
            },
        } as ModelingSubmissionComparisonDTO,
        { similarity: 0.9 } as ModelingSubmissionComparisonDTO,
        { similarity: 0.8 } as ModelingSubmissionComparisonDTO,
    ];
    const modelingExercise = { id: 123 } as ModelingExercise;
    const activatedRoute = {
        params: of({ exerciseId: modelingExercise.id }),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisModelingEditorModule, TranslateTestingModule],
            declarations: [PlagiarismInspectorComponent, PlagiarismHeaderComponent, PlagiarismSplitViewComponent],
            providers: [{ provide: ActivatedRoute, useValue: activatedRoute }],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismInspectorComponent);
        comp = fixture.componentInstance;
        modelingExerciseService = fixture.debugElement.injector.get(ModelingExerciseService);
    });

    it("should make a call to 'fetchModelingExercise' on initialization", () => {
        spyOn(comp, 'fetchModelingExercise');

        comp.ngOnInit();

        expect(comp.fetchModelingExercise).toHaveBeenCalledWith(modelingExercise.id!);
    });

    it('should load the modeling exercise', () => {
        const mockResponse = new HttpResponse({ body: modelingExercise });
        spyOn(modelingExerciseService, 'find').and.returnValue(of(mockResponse));

        comp.fetchModelingExercise(modelingExercise.id!);

        expect(modelingExerciseService.find).toHaveBeenCalled();
        expect(comp.modelingExercise).toEqual(modelingExercise);
    });

    it('should update the status of a plagiarism', () => {
        const selectedPlagiarismIndex = 0;

        comp.modelingSubmissionComparisons = modelingComparisons;
        comp.selectedPlagiarismIndex = selectedPlagiarismIndex;

        comp.handlePlagiarismStatusChange(true);

        expect(comp.modelingSubmissionComparisons[selectedPlagiarismIndex].confirmed).toEqual(true);
    });

    it('should publish split view updates', () => {
        spyOn(comp.splitControlSubject, 'next');

        comp.handleSplitViewChange('left');

        expect(comp.splitControlSubject.next).toHaveBeenCalledWith('left');
    });

    it('should fetch the plagiarism detection results', () => {
        comp.modelingExercise = modelingExercise;
        spyOn(modelingExerciseService, 'checkPlagiarism').and.returnValue(of(modelingComparisons.slice()));

        comp.checkPlagiarism();

        expect(comp.modelingSubmissionComparisons.length).toEqual(modelingComparisons.length);
    });

    it('should download the plagiarism detection results as JSON', () => {
        comp.modelingExercise = modelingExercise;
        comp.modelingSubmissionComparisons = modelingComparisons;
        comp.downloadPlagiarismResultsJson();

        expect(downloadFile).toHaveBeenCalled();
    });

    it('should download the plagiarism detection results as CSV', () => {
        comp.modelingExercise = modelingExercise;
        comp.modelingSubmissionComparisons = modelingComparisons.slice(0, 1);
        comp.downloadPlagiarismResultsCsv();

        expect(ExportToCsv).toHaveBeenCalled();
        expect(generateCsv).toHaveBeenCalled();
    });
});
