import { SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Subject, of } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PlagiarismSplitViewComponent } from 'app/plagiarism/manage/plagiarism-split-view/plagiarism-split-view.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { PlagiarismSubmission } from 'app/plagiarism/shared/entities/PlagiarismSubmission';
import { PlagiarismMatch, SimpleMatch } from 'app/plagiarism/shared/entities/PlagiarismMatch';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { TextSubmissionViewerComponent } from 'app/plagiarism/manage/plagiarism-split-view/text-submission-viewer/text-submission-viewer.component';
import { PlagiarismStatus } from 'app/plagiarism/shared/entities/PlagiarismStatus';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { PlagiarismComparison } from '../../shared/entities/PlagiarismComparison';
import { FromToElement, PlagiarismSubmissionElement } from '../../shared/entities/PlagiarismSubmissionElement';

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
    let plagiarismCasesService: PlagiarismCasesService;

    const textExercise = { id: 234, type: ExerciseType.TEXT, course: { id: 1 } } as TextExercise;
    const splitControlSubject = new Subject<string>();

    const submissionA = { studentLogin: 'studentA' } as PlagiarismSubmission;
    const submissionB = { studentLogin: 'studentB' } as PlagiarismSubmission;

    const comparison = {
        id: 1,
        submissionA,
        submissionB,
        matches: [],
        similarity: 42,
        status: PlagiarismStatus.DENIED,
        statusA: PlagiarismStatus.NONE,
        statusB: PlagiarismStatus.NONE,
    } as PlagiarismComparison;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [PlagiarismSplitViewComponent, MockPipe(ArtemisDatePipe), MockComponent(TextSubmissionViewerComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismSplitViewComponent);
        comp = fixture.componentInstance;
        plagiarismCasesService = TestBed.inject(PlagiarismCasesService);

        comp.plagiarismComparison = comparison;
        fixture.componentRef.setInput('comparison', {
            submissionA,
            submissionB,
        } as PlagiarismComparison);

        fixture.componentRef.setInput('splitControlSubject', splitControlSubject);
    });

    afterEach(() => {
        jest.resetAllMocks();
    });

    it('checks type of text exercise', () => {
        comp.ngOnChanges({
            exercise: { currentValue: textExercise } as SimpleChange,
        });

        expect(comp.isProgrammingOrTextExercise).toBeTrue();
    });

    it('should parse text matches for comparison', fakeAsync(() => {
        fixture.componentRef.setInput('exercise', textExercise as Exercise);
        jest.spyOn(comp, 'parseTextMatches');
        jest.spyOn(plagiarismCasesService, 'getPlagiarismComparisonForSplitView').mockReturnValue(of({ body: comparison } as HttpResponse<PlagiarismComparison>));
        comp.ngOnChanges({
            exercise: { currentValue: textExercise } as SimpleChange,
            comparison: { currentValue: comparison } as SimpleChange,
        });

        tick();

        expect(comp.isProgrammingOrTextExercise).toBeTrue();
        expect(comp.parseTextMatches).toHaveBeenCalledOnce();
    }));

    it('should subscribe to the split control subject', () => {
        fixture.componentRef.setInput('exercise', textExercise as Exercise);
        const subscribeSpy = jest.spyOn(splitControlSubject, 'subscribe');

        comp.ngOnInit();

        expect(subscribeSpy).toHaveBeenCalledOnce();
    });

    it('should collapse the left pane', () => {
        comp.split = { collapse } as unknown as Split.Instance;

        comp.handleSplitControl('left');

        expect(collapse).toHaveBeenCalledWith(1);
    });

    it('should collapse the right pane', () => {
        comp.split = { collapse } as unknown as Split.Instance;

        comp.handleSplitControl('right');

        expect(collapse).toHaveBeenCalledWith(0);
    });

    it('should reset the split panes', () => {
        comp.split = { setSizes } as unknown as Split.Instance;

        comp.handleSplitControl('even');

        expect(setSizes).toHaveBeenCalledWith([50, 50]);
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
        jest.spyOn(comp, 'mapMatchesToElements').mockReturnValue(new Map<string, FromToElement[]>());

        const matches: PlagiarismMatch[] = [
            { startA: 0, startB: 0, length: 5 },
            { startA: 10, startB: 10, length: 20 },
        ];
        comp.parseTextMatches({ submissionA, submissionB, matches } as PlagiarismComparison);

        expect(comp.mapMatchesToElements).toHaveBeenCalledTimes(2);
        expect(comp.mapMatchesToElements).toHaveBeenNthCalledWith(
            1,
            [
                { start: 0, length: 5 },
                { start: 10, length: 20 },
            ],
            submissionA,
        );
        expect(comp.mapMatchesToElements).toHaveBeenNthCalledWith(
            2,
            [
                { start: 0, length: 5 },
                { start: 10, length: 20 },
            ],
            submissionB,
        );
    });

    it('should map matches to elements', () => {
        const matches = [
            { start: 0, length: 2 },
            { start: 0, length: 0 },
            { start: 3, length: 3 },
        ] as SimpleMatch[];
        submissionA.elements = [
            { id: 8, file: '', column: 8, line: 8, length: 1 },
            { id: 2, file: '', column: 2, line: 2, length: 1 },
            { id: 4, file: '', column: 4, line: 4, length: 1 },
            { id: 1, file: '', column: 1, line: 1, length: 1 },
            { id: 5, file: '', column: 5, line: 5, length: 1 },
            { id: 6, file: '', column: 6, line: 6, length: 1 },
            { id: 7, file: '', column: 7, line: 7, length: 1 },
            { id: 9, file: '', column: 9, line: 9, length: 1 },
            { id: 3, file: '', column: 3, line: 3, length: 1 },
            { id: 10, file: '', column: 10, line: 10, length: 1 },
        ] as PlagiarismSubmissionElement[];
        const mappedElements = new Map();
        mappedElements.set('none', [
            new FromToElement(
                { id: 1, file: '', column: 1, line: 1, length: 1 } as PlagiarismSubmissionElement,
                { id: 2, file: '', column: 2, line: 2, length: 1 } as PlagiarismSubmissionElement,
            ),
            new FromToElement(
                { id: 4, file: '', column: 4, line: 4, length: 1 } as PlagiarismSubmissionElement,
                { id: 6, file: '', column: 6, line: 6, length: 1 } as PlagiarismSubmissionElement,
            ),
        ]);

        const result = comp.mapMatchesToElements(matches, submissionA);

        expect(result).toEqual(mappedElements);
    });

    it('should map matches to elements even if matches are out of bounds', () => {
        submissionA.elements = [
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
        ] as PlagiarismSubmissionElement[];
        const matches = [
            { start: 0, length: 2 },
            { start: 0, length: 0 },
            { start: submissionA.elements.length + 1, length: 3 }, // Faulty data
            { start: 3, length: 3 },
        ] as SimpleMatch[];
        const mappedElements = new Map();
        mappedElements.set('none', [
            new FromToElement({ file: '', column: 1, line: 1 } as PlagiarismSubmissionElement, { file: '', column: 2, line: 2 } as PlagiarismSubmissionElement),
            new FromToElement(undefined as unknown as PlagiarismSubmissionElement, undefined as unknown as PlagiarismSubmissionElement),
            new FromToElement({ file: '', column: 4, line: 4 } as PlagiarismSubmissionElement, { file: '', column: 6, line: 6 } as PlagiarismSubmissionElement),
        ]);

        const result = comp.mapMatchesToElements(matches, submissionA);

        expect(result).toEqual(mappedElements);
    });

    it('should swap submissions correctly with corresponding matches', () => {
        const studentLogin = 'student1';

        function createPlagiarismComparison() {
            return {
                id: 1,
                similarity: 80,
                matches: [
                    { startA: 0, startB: 2, length: 1 },
                    { startA: 1, startB: 4, length: 1 },
                    { startA: 2, startB: 5, length: 2 },
                ],
                status: PlagiarismStatus.CONFIRMED,
                submissionA: {
                    studentLogin: 'student2',
                    elements: [
                        { file: '', column: 1, line: 1 },
                        { file: '', column: 2, line: 2 },
                        { file: '', column: 3, line: 3 },
                        { file: '', column: 4, line: 4 },
                    ],
                },
                submissionB: {
                    studentLogin,
                    elements: [
                        { file: '', column: 1, line: 1 },
                        { file: '', column: 2, line: 2 },
                        { file: '', column: 3, line: 3 },
                        { file: '', column: 4, line: 4 },
                        { file: '', column: 5, line: 5 },
                        { file: '', column: 6, line: 6 },
                        { file: '', column: 7, line: 7 },
                        { file: '', column: 8, line: 8 },
                    ],
                },
            } as PlagiarismComparison;
        }

        const plagiarismComparison = createPlagiarismComparison();

        const plagiarismCasesServiceSpy = jest
            .spyOn(plagiarismCasesService, 'getPlagiarismComparisonForSplitView')
            .mockReturnValue(of({ body: plagiarismComparison } as HttpResponse<PlagiarismComparison>));

        fixture.componentRef.setInput('sortByStudentLogin', studentLogin);
        fixture.componentRef.setInput('exercise', textExercise as Exercise);

        comp.ngOnChanges({ comparison: { currentValue: { id: 1 } } as SimpleChange });

        const originalPlagComp = createPlagiarismComparison();
        const plagComp = comp.plagiarismComparison;

        expect(plagiarismCasesServiceSpy).toHaveBeenCalledOnce();

        expect(plagComp.submissionA).toEqual(originalPlagComp.submissionB);
        expect(plagComp.submissionB).toEqual(originalPlagComp.submissionA);
        expect(plagComp.matches?.map((match) => match.startA)).toEqual(originalPlagComp.matches?.map((match) => match.startB));
        expect(plagComp.matches?.map((match) => match.startB)).toEqual(originalPlagComp.matches?.map((match) => match.startA));
        expect(plagComp.matches?.map((match) => match.length)).toEqual(originalPlagComp.matches?.map((match) => match.length));
    });
});
