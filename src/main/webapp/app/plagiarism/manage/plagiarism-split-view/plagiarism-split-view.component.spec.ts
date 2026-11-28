import { By } from '@angular/platform-browser';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Subject, of } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PlagiarismSplitViewComponent } from 'app/plagiarism/manage/plagiarism-split-view/plagiarism-split-view.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { PlagiarismSubmission } from 'app/plagiarism/shared/entities/PlagiarismSubmission';
import { PlagiarismMatch, SimpleMatch } from 'app/plagiarism/shared/entities/PlagiarismMatch';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { TextSubmissionViewerComponent } from 'app/plagiarism/manage/plagiarism-split-view/text-submission-viewer/text-submission-viewer.component';
import { PlagiarismStatus } from 'app/plagiarism/shared/entities/PlagiarismStatus';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { PlagiarismComparison } from '../../shared/entities/PlagiarismComparison';
import { FromToElement, PlagiarismSubmissionElement } from '../../shared/entities/PlagiarismSubmissionElement';
import { TumAetUiSplitterComponent } from '@tumaet/ui-angular/splitter';

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
            imports: [PlagiarismSplitViewComponent, MockPipe(ArtemisDatePipe), MockComponent(TextSubmissionViewerComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismSplitViewComponent);
        comp = fixture.componentInstance;
        plagiarismCasesService = TestBed.inject(PlagiarismCasesService);

        comp.plagiarismComparison.set(comparison);
        fixture.componentRef.setInput('comparison', {
            submissionA,
            submissionB,
        } as PlagiarismComparison);

        fixture.componentRef.setInput('splitControlSubject', splitControlSubject);
    });

    afterEach(() => {
        vi.resetAllMocks();
    });

    it('checks type of text exercise', () => {
        // isProgrammingOrTextExercise is now a computed derived from exercise() (replaces the former ngOnChanges branch).
        fixture.componentRef.setInput('exercise', textExercise as Exercise);

        expect(comp.isProgrammingOrTextExercise()).toBe(true);
    });

    it('should parse text matches for comparison', async () => {
        vi.spyOn(comp, 'parseTextMatches');
        vi.spyOn(plagiarismCasesService, 'getPlagiarismComparisonForSplitView').mockReturnValue(of(comparison));

        // The constructor effect fetches and parses the comparison whenever comparison() changes (replaces the former ngOnChanges).
        fixture.componentRef.setInput('exercise', textExercise as Exercise);
        fixture.componentRef.setInput('comparison', comparison);
        fixture.detectChanges();

        await Promise.resolve();

        expect(comp.isProgrammingOrTextExercise()).toBe(true);
        expect(comp.parseTextMatches).toHaveBeenCalledOnce();
    });

    it('should not fetch comparison until the course id can be resolved', () => {
        const getComparisonSpy = vi.spyOn(plagiarismCasesService, 'getPlagiarismComparisonForSplitView').mockReturnValue(of(comparison));

        // With a comparison set but no exercise, courseId() is undefined, so the effect must not fetch.
        fixture.componentRef.setInput('comparison', { id: 1 } as PlagiarismComparison);
        fixture.detectChanges();

        expect(getComparisonSpy).not.toHaveBeenCalled();
    });

    it('should fetch the comparison when the exercise is bound after the comparison', () => {
        const getComparisonSpy = vi.spyOn(plagiarismCasesService, 'getPlagiarismComparisonForSplitView').mockReturnValue(of(comparison));

        // The parent may bind the inputs in either order. The effect tracks the derived course id as well, so the late exercise still triggers the fetch.
        fixture.componentRef.setInput('comparison', { id: 1 } as PlagiarismComparison);
        fixture.detectChanges();
        expect(getComparisonSpy).not.toHaveBeenCalled();

        fixture.componentRef.setInput('exercise', textExercise as Exercise);
        fixture.detectChanges();

        expect(getComparisonSpy).toHaveBeenCalledExactlyOnceWith(textExercise.course!.id, 1);
    });

    it('should subscribe to the split control subject', () => {
        fixture.componentRef.setInput('exercise', textExercise as Exercise);
        const subscribeSpy = vi.spyOn(splitControlSubject, 'subscribe');

        comp.ngOnInit();

        expect(subscribeSpy).toHaveBeenCalledOnce();
    });

    it('should collapse the right pane so the left pane takes all space', () => {
        comp.handleSplitControl('left');
        fixture.detectChanges();

        // a collapsed pane has size 0, which the splitter only allows once the minimum of both panes is lifted
        expect(comp.collapsedSide()).toBe('right');
        expect(comp.panelSizes()).toEqual([100, 0]);
        expect(comp.minSizes()).toEqual([0, 0]);
        // the divider sits at the edge, so the lock-files control is hidden
        expect(fixture.nativeElement.querySelector('[data-qa="lock-files-toggle-button"]')).toBeNull();
    });

    it('should collapse the left pane so the right pane takes all space', () => {
        comp.handleSplitControl('right');
        fixture.detectChanges();

        expect(comp.collapsedSide()).toBe('left');
        expect(comp.panelSizes()).toEqual([0, 100]);
        expect(comp.minSizes()).toEqual([0, 0]);
        expect(fixture.nativeElement.querySelector('[data-qa="lock-files-toggle-button"]')).toBeNull();
    });

    it('should reset the split panes and clear any collapse', () => {
        comp.handleSplitControl('left');

        comp.handleSplitControl('even');
        fixture.detectChanges();

        expect(comp.collapsedSide()).toBeUndefined();
        expect(comp.panelSizes()).toEqual([50, 50]);
        expect(comp.minSizes()).toEqual([10, 10]);
        expect(fixture.nativeElement.querySelector('[data-qa="lock-files-toggle-button"]')).not.toBeNull();
    });

    it('should hand the sizes and minimums to the splitter', () => {
        fixture.detectChanges();
        const splitter = fixture.debugElement.query(By.directive(TumAetUiSplitterComponent)).componentInstance as TumAetUiSplitterComponent;
        expect(splitter.panelSizes()).toEqual([50, 50]);
        expect(splitter.minSizes()).toEqual([10, 10]);

        comp.handleSplitControl('left');
        fixture.detectChanges();
        expect(splitter.panelSizes()).toEqual([100, 0]);
        expect(splitter.minSizes()).toEqual([0, 0]);

        comp.handleSplitControl('right');
        fixture.detectChanges();
        expect(splitter.panelSizes()).toEqual([0, 100]);
        expect(splitter.minSizes()).toEqual([0, 0]);

        comp.handleSplitControl('even');
        fixture.detectChanges();
        expect(splitter.panelSizes()).toEqual([50, 50]);
        expect(splitter.minSizes()).toEqual([10, 10]);
    });

    it('should keep the lock-files control on the divider while it is moved', () => {
        fixture.detectChanges();
        const lockControl = () => fixture.nativeElement.querySelector('.split-pane-header-color') as HTMLElement;
        // the first pane takes its share of the width without the 6px gutter, and the gutter adds half of its width
        expect(comp.lockButtonLeft()).toBe('calc((100% - 6px) * 0.5 + 3px)');
        expect(lockControl().style.left).toContain('calc(');

        comp.onSizesChange([30, 70]);
        fixture.detectChanges();

        expect(comp.lockButtonLeft()).toBe('calc((100% - 6px) * 0.3 + 3px)');
        expect(lockControl().style.left).toContain('0.3');
    });

    it('should leave the collapsed state when the user moves the divider at the edge', () => {
        comp.handleSplitControl('left');

        comp.onResizeEnd([4, 96]);

        // the pane gets back at least the minimum size
        expect(comp.collapsedSide()).toBeUndefined();
        expect(comp.panelSizes()).toEqual([10, 90]);
        expect(comp.splitPercent()).toBe(10);
    });

    it('should not change the sizes when the user moves the divider without a collapsed pane', () => {
        comp.onResizeEnd([40, 60]);

        expect(comp.panelSizes()).toEqual([50, 50]);
    });

    it('should toggle the synchronization of the files', () => {
        fixture.detectChanges();
        const toggle = fixture.nativeElement.querySelector('[data-qa="lock-files-toggle-button"]') as HTMLButtonElement;
        expect(toggle.getAttribute('aria-pressed')).toBe('false');

        toggle.click();
        fixture.detectChanges();

        expect(comp.isLockFilesEnabled()).toBe(true);
        expect(toggle.getAttribute('aria-pressed')).toBe('true');
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
        vi.spyOn(comp, 'mapMatchesToElements').mockReturnValue(new Map<string, FromToElement[]>());

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

        const plagiarismCasesServiceSpy = vi.spyOn(plagiarismCasesService, 'getPlagiarismComparisonForSplitView').mockReturnValue(of(plagiarismComparison));

        fixture.componentRef.setInput('sortByStudentLogin', studentLogin);
        fixture.componentRef.setInput('exercise', textExercise as Exercise);

        // The constructor effect fetches and swaps the comparison whenever comparison() changes (replaces the former ngOnChanges).
        fixture.componentRef.setInput('comparison', { id: 1 } as PlagiarismComparison);
        fixture.detectChanges();

        const originalPlagComp = createPlagiarismComparison();
        const plagComp = comp.plagiarismComparison()!;

        expect(plagiarismCasesServiceSpy).toHaveBeenCalledOnce();

        expect(plagComp.submissionA).toEqual(originalPlagComp.submissionB);
        expect(plagComp.submissionB).toEqual(originalPlagComp.submissionA);
        expect(plagComp.matches?.map((match) => match.startA)).toEqual(originalPlagComp.matches?.map((match) => match.startB));
        expect(plagComp.matches?.map((match) => match.startB)).toEqual(originalPlagComp.matches?.map((match) => match.startA));
        expect(plagComp.matches?.map((match) => match.length)).toEqual(originalPlagComp.matches?.map((match) => match.length));
    });
    it.each([false, true])('passes student viewing purpose %s to both plagiarism panes', (forStudent) => {
        fixture.componentRef.setInput('forStudent', forStudent);
        fixture.componentRef.setInput('exercise', textExercise);
        vi.spyOn(plagiarismCasesService, 'getPlagiarismComparisonForSplitView').mockReturnValue(of(comparison));
        fixture.detectChanges();
        const viewers = fixture.debugElement.queryAll(By.directive(TextSubmissionViewerComponent));
        expect(viewers).toHaveLength(2);
        for (const viewer of viewers) {
            expect(viewer.componentInstance.viewingPurpose()).toBe(forStudent ? 'owner' : 'staff-preview');
        }
    });
});
