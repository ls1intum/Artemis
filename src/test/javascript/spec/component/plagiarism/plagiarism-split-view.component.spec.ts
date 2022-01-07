import { SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { PlagiarismSplitViewComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/plagiarism-split-view.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { PlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmission';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { PlagiarismMatch } from 'app/exercises/shared/plagiarism/types/PlagiarismMatch';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ModelingSubmissionViewerComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/modeling-submission-viewer/modeling-submission-viewer.component';
import { TextSubmissionViewerComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/text-submission-viewer/text-submission-viewer.component';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';

const collapse = jest.fn();
const setSizes = jest.fn();

jest.mock('split.js', () => ({
    default: jest.fn().mockImplementation(() => ({
        collapse,
        setSizes,
    })),
}));

describe('Plagiarism Split View Component', () => {
    let comp: PlagiarismSplitViewComponent;
    let fixture: ComponentFixture<PlagiarismSplitViewComponent>;

    const modelingExercise = { id: 123, type: ExerciseType.MODELING } as ModelingExercise;
    const textExercise = { id: 234, type: ExerciseType.TEXT } as TextExercise;
    const splitControlSubject = new Subject<string>();

    const submissionA = { studentLogin: 'studentA' } as PlagiarismSubmission<TextSubmissionElement>;
    const submissionB = { studentLogin: 'studentB' } as PlagiarismSubmission<TextSubmissionElement>;

    const comparison = {
        id: 1,
        submissionA,
        submissionB,
        matches: [],
        similarity: 42,
        status: PlagiarismStatus.DENIED,
        statusA: PlagiarismStatus.NONE,
        statusB: PlagiarismStatus.NONE,
    } as PlagiarismComparison<TextSubmissionElement>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [PlagiarismSplitViewComponent, MockPipe(ArtemisDatePipe), MockComponent(ModelingSubmissionViewerComponent), MockComponent(TextSubmissionViewerComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismSplitViewComponent);
        comp = fixture.componentInstance;

        comp.comparison = {
            submissionA,
            submissionB,
        } as PlagiarismComparison<TextSubmissionElement>;
        comp.splitControlSubject = splitControlSubject;
    });

    afterEach(() => {
        jest.resetAllMocks();
    });

    it('checks type of modeling exercise', () => {
        comp.ngOnChanges({
            exercise: { currentValue: modelingExercise } as SimpleChange,
        });

        expect(comp.isModelingExercise).toEqual(true);
        expect(comp.isProgrammingOrTextExercise).toEqual(false);
    });

    it('checks type of text exercise', () => {
        comp.ngOnChanges({
            exercise: { currentValue: textExercise } as SimpleChange,
        });

        expect(comp.isProgrammingOrTextExercise).toEqual(true);
        expect(comp.isModelingExercise).toEqual(false);
    });

    it('should parse text matches for comparison', () => {
        jest.spyOn(comp, 'parseTextMatches');
        comp.ngOnChanges({
            exercise: { currentValue: textExercise } as SimpleChange,
            comparison: { currentValue: comparison } as SimpleChange,
        });

        expect(comp.isProgrammingOrTextExercise).toEqual(true);
        expect(comp.isModelingExercise).toEqual(false);
        expect(comp.parseTextMatches).toHaveBeenCalledTimes(1);
    });

    it('should subscribe to the split control subject', () => {
        comp.exercise = textExercise;
        jest.spyOn(splitControlSubject, 'subscribe');

        comp.ngOnInit();
        expect(comp.splitControlSubject.subscribe).toHaveBeenCalled();
    });

    it('should collapse the left pane', () => {
        comp.exercise = textExercise;
        comp.split = { collapse } as unknown as Split.Instance;

        comp.handleSplitControl('left');

        expect(collapse).toHaveBeenCalledWith(1);
    });

    it('should collapse the right pane', () => {
        comp.exercise = textExercise;
        comp.split = { collapse } as unknown as Split.Instance;

        comp.handleSplitControl('right');

        expect(collapse).toHaveBeenCalledWith(0);
    });

    it('should reset the split panes', () => {
        comp.exercise = textExercise;
        comp.split = { setSizes } as unknown as Split.Instance;

        comp.handleSplitControl('even');

        expect(setSizes).toHaveBeenCalledWith([50, 50]);
    });

    it('should get the first modeling submission', () => {
        const modelingSubmissionA = comp.getModelingSubmissionA();
        expect(modelingSubmissionA).toEqual(submissionA);
    });

    it('should get the second modeling submission', () => {
        const modelingSubmissionB = comp.getModelingSubmissionB();
        expect(modelingSubmissionB).toEqual(submissionB);
    });

    it('should get the first text submission', () => {
        const textSubmissionA = comp.getTextSubmissionA();
        expect(textSubmissionA).toEqual(submissionA);
    });

    it('should get the second text submission', () => {
        const textSubmissionB = comp.getTextSubmissionB();
        expect(textSubmissionB).toEqual(submissionB);
    });

    it('should parse text matches', () => {
        jest.spyOn(comp, 'mapMatchesToElements').mockReturnValue(new Map());

        const matches: PlagiarismMatch[] = [
            { startA: 0, startB: 0, length: 5 },
            { startA: 10, startB: 10, length: 20 },
        ];
        comp.parseTextMatches({ submissionA, submissionB, matches } as PlagiarismComparison<TextSubmissionElement>);

        expect(comp.matchesA).toBeDefined();
        expect(comp.matchesB).toBeDefined();
        expect(comp.mapMatchesToElements).toHaveBeenCalledTimes(2);
    });

    it('should map matches to elements', () => {
        const matches = [
            { start: 0, length: 2 },
            { start: 0, length: 0 },
            { start: 3, length: 3 },
        ];
        const textSubmissionElements = [
            { file: '', column: 1, line: 1 },
            { file: '', column: 2, line: 2 },
            { file: '', column: 3, line: 3 },
            { file: '', column: 4, line: 4 },
            { file: '', column: 5, line: 5 },
            { file: '', column: 6, line: 6 },
            { file: '', column: 7, line: 7 },
            { file: '', column: 8, line: 8 },
            { file: '', column: 9, line: 9 },
            { file: '', column: 10, line: 10 },
        ] as TextSubmissionElement[];
        submissionA.elements = textSubmissionElements;
        const mappedElements = new Map();
        mappedElements.set('none', [
            { from: { file: '', column: 1, line: 1 }, to: { file: '', column: 2, line: 2 } },
            { from: { file: '', column: 4, line: 4 }, to: { file: '', column: 6, line: 6 } },
        ]);

        const result = comp.mapMatchesToElements(matches, submissionA);

        expect(result).toEqual(mappedElements);
    });
});
