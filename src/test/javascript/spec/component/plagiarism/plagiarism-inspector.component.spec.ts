import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ExportToCsv } from 'export-to-csv';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { PlagiarismInspectorComponent } from 'app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';
import { downloadFile } from 'app/shared/util/download.util';
import { ModelingPlagiarismResult } from 'app/exercises/shared/plagiarism/types/modeling/ModelingPlagiarismResult';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';

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

    const modelingExercise = { id: 123, type: ExerciseType.MODELING } as ModelingExercise;
    const textExercise = { id: 234, type: ExerciseType.TEXT } as TextExercise;
    const programmingExercise = { id: 345, type: ExerciseType.PROGRAMMING } as ProgrammingExercise;
    const activatedRoute = {
        data: of({ exercise: modelingExercise }),
    };
    const plagiarismResult = {
        comparisons: [
            {
                submissionA: { studentLogin: 'student1A' },
                submissionB: { studentLogin: 'student1B' },
                similarity: 0.5,
                status: PlagiarismStatus.NONE,
            },
            {
                submissionA: { studentLogin: 'student2A' },
                submissionB: { studentLogin: 'student2B' },
                similarity: 0.8,
                status: PlagiarismStatus.NONE,
            },
            {
                submissionA: { studentLogin: 'student3A' },
                submissionB: { studentLogin: 'student3B' },
                similarity: 0.7,
                status: PlagiarismStatus.NONE,
            },
        ],
    } as ModelingPlagiarismResult;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisPlagiarismModule, TranslateTestingModule],
            providers: [{ provide: ActivatedRoute, useValue: activatedRoute }],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismInspectorComponent);
        comp = fixture.componentInstance;
        modelingExerciseService = fixture.debugElement.injector.get(ModelingExerciseService);
        programmingExerciseService = fixture.debugElement.injector.get(ProgrammingExerciseService);
        textExerciseService = fixture.debugElement.injector.get(TextExerciseService);
    });

    it('should get the minimumSize tootip', () => {
        comp.exercise = { type: ExerciseType.PROGRAMMING } as Exercise;

        expect(comp.getMinimumSizeTooltip()).toEqual('artemisApp.plagiarism.minimum-size-tooltip');
    });

    it('should get the minimumSize tootip for modeling', () => {
        comp.exercise = { type: ExerciseType.MODELING } as Exercise;

        expect(comp.getMinimumSizeTooltip()).toEqual('artemisApp.plagiarism.minimum-size-tooltip-modeling');
    });

    it('should get the minimumSize tootip for text', () => {
        comp.exercise = { type: ExerciseType.TEXT } as Exercise;

        expect(comp.getMinimumSizeTooltip()).toEqual('artemisApp.plagiarism.minimum-size-tooltip-text');
    });

    it('should fetch the plagiarism detection results for modeling exercises', () => {
        comp.exercise = modelingExercise;
        spyOn(modelingExerciseService, 'checkPlagiarism').and.returnValue(of(plagiarismResult));

        comp.checkPlagiarism();

        expect(modelingExerciseService.checkPlagiarism).toHaveBeenCalled();
    });

    it('should fetch the plagiarism detection results for programming exercises', () => {
        comp.exercise = programmingExercise;
        spyOn(programmingExerciseService, 'checkPlagiarism').and.returnValue(of(plagiarismResult));

        comp.checkPlagiarism();

        expect(programmingExerciseService.checkPlagiarism).toHaveBeenCalled();
    });

    it('should fetch the plagiarism detection results for text exercises', () => {
        comp.exercise = textExercise;
        spyOn(textExerciseService, 'checkPlagiarismJPlag').and.returnValue(of(plagiarismResult));

        comp.checkPlagiarism();

        expect(textExerciseService.checkPlagiarismJPlag).toHaveBeenCalled();
    });

    it('should comparisons by similarity', () => {
        comp.sortComparisonsForResult(plagiarismResult);

        expect(plagiarismResult.comparisons[0].similarity).toEqual(0.8);
    });

    it('should select a comparison at the given index', () => {
        comp.selectedComparisonIndex = 0;
        comp.selectComparisonAtIndex(1);

        expect(comp.selectedComparisonIndex).toEqual(1);
    });

    it('should download the plagiarism detection results as JSON', () => {
        comp.exercise = modelingExercise;
        comp.plagiarismResult = plagiarismResult;
        comp.downloadPlagiarismResultsJson();

        expect(downloadFile).toHaveBeenCalled();
    });

    it('should download the plagiarism detection results as CSV', () => {
        comp.exercise = modelingExercise;
        comp.plagiarismResult = plagiarismResult;
        comp.downloadPlagiarismResultsCsv();

        expect(ExportToCsv).toHaveBeenCalled();
        expect(generateCsv).toHaveBeenCalled();
    });
});
