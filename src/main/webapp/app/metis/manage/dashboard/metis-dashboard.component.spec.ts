import { HttpErrorResponse } from '@angular/common/http';
import { DebugElement, EmbeddedViewRef, getDebugNode } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiBarChartComponent } from '@tumaet/ui-angular';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { type Mock, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';
import { MetisDashboardComponent } from 'app/metis/manage/dashboard/metis-dashboard.component';
import { RatingDistributionComponent } from 'app/metis/manage/dashboard/rating-distribution/rating-distribution.component';
import { MetisCheckStatus, MetisDashboardRowDTO, MetisDashboardSummaryDTO, MetisQuestionDTO } from 'app/metis/shared/entities/metis.model';
import { RatingSquareComponent } from 'app/metis/shared/rating-square/rating-square.component';
import { MetisService } from 'app/metis/shared/services/metis.service';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

const approvedAt = dayjs('2026-11-02T23:59:00');
const generatedAt = dayjs('2026-11-02T23:58:00');

function question(overrides: Partial<MetisQuestionDTO> & Pick<MetisQuestionDTO, 'id' | 'checkId' | 'questionOrder'>): MetisQuestionDTO {
    return { questionText: `Question ${overrides.id}`, timeLimitSeconds: 240, regenerationCount: 0, focusFilePath: 'BubbleSort.java', focusFileChangedLines: 12, ...overrides };
}

/** Anna is done (ratings 2, 4, 5 and two follow-ups), Ben is answering, Clara waits for the review, David handed nothing in. */
function createRows(): MetisDashboardRowDTO[] {
    return [
        {
            participationId: 1,
            studentName: 'Anna Huber',
            studentLogin: 'anna',
            tutorialGroupTitle: 'Gruppe 1',
            check: {
                id: 11,
                participationId: 1,
                status: MetisCheckStatus.COMPLETED,
                commitHash: 'a3f9c21',
                llmModel: 'qwen3-coder:30b',
                promptVersion: 'v1',
                generatedAt,
                completedAt: dayjs('2026-11-03T10:16:00'),
            },
            questions: [
                question({
                    id: 101,
                    checkId: 11,
                    questionOrder: 1,
                    llmScore: 2,
                    answerText: 'Because that is how bubble sort works, the loop gets shorter.',
                    llmRationale: 'Repeats what the code does without saying why.',
                    approvedAt,
                }),
                question({ id: 102, checkId: 11, questionOrder: 2, parentQuestionId: 101, llmScore: 3, answerText: 'The big numbers move to the back.' }),
                question({ id: 103, checkId: 11, questionOrder: 3, parentQuestionId: 101, llmScore: 3, answerText: 'Line 12 swaps the larger one to the right.' }),
                question({
                    id: 104,
                    checkId: 11,
                    questionOrder: 4,
                    focusFilePath: 'SortUtils.java',
                    llmScore: 4,
                    answerText: 'The temporary variable keeps the first value.',
                    approvedAt,
                }),
                question({
                    id: 105,
                    checkId: 11,
                    questionOrder: 5,
                    focusFilePath: 'BubbleSortTest.java',
                    llmScore: 5,
                    answerText: 'It checks that sorted input stays unchanged.',
                    approvedAt,
                }),
            ],
        },
        {
            participationId: 2,
            studentName: 'Ben Gruber',
            studentLogin: 'ben',
            tutorialGroupTitle: 'Gruppe 2',
            check: { id: 22, participationId: 2, status: MetisCheckStatus.IN_PROGRESS, commitHash: 'b7e2d10', llmModel: 'qwen3-coder:30b', promptVersion: 'v1', generatedAt },
            questions: [
                question({ id: 201, checkId: 22, questionOrder: 1, llmScore: 3, answerText: 'The big numbers move to the back.', llmRationale: 'Partly correct.', approvedAt }),
                question({ id: 202, checkId: 22, questionOrder: 2, parentQuestionId: 201, shownAt: dayjs('2026-11-03T10:24:00') }),
                question({ id: 203, checkId: 22, questionOrder: 3, focusFilePath: 'SortUtils.java', approvedAt }),
                question({ id: 204, checkId: 22, questionOrder: 4, focusFilePath: 'BubbleSortTest.java', approvedAt }),
            ],
        },
        {
            participationId: 3,
            studentName: 'Clara Bauer',
            studentLogin: 'clara',
            tutorialGroupTitle: 'Gruppe 1',
            check: { id: 33, participationId: 3, status: MetisCheckStatus.IN_REVIEW, generatedAt },
            questions: [
                question({ id: 301, checkId: 33, questionOrder: 1 }),
                question({ id: 302, checkId: 33, questionOrder: 2, focusFilePath: 'SortUtils.java' }),
                question({ id: 303, checkId: 33, questionOrder: 3, focusFilePath: 'BubbleSortTest.java' }),
            ],
        },
        {
            participationId: 4,
            studentName: 'David Wagner',
            studentLogin: 'david',
            tutorialGroupTitle: 'Gruppe 2',
            check: { id: 44, participationId: 4, status: MetisCheckStatus.SKIPPED, failureReason: 'No submission' },
            questions: [],
        },
    ];
}

const summary: MetisDashboardSummaryDTO = {
    participants: 4,
    completed: 1,
    inProgress: 1,
    notStarted: 1,
    skippedOrFailed: 1,
    awaitingReview: 1,
    averageScore: 3.4,
    followUps: 3,
    windowEnd: dayjs('2026-11-03T10:35:00'),
};

const csvFile = new Blob(['participation_id,student_login\n1,anna\n'], { type: 'text/csv' });

interface FakeMetisService {
    config: Mock;
    dashboard: Mock;
    exportCsv: Mock;
    generateNow: Mock;
}

describe('MetisDashboardComponent', () => {
    let fixture: ComponentFixture<MetisDashboardComponent>;
    let component: MetisDashboardComponent;
    let alertService: AlertService;
    let metisService: FakeMetisService;
    let exercise: ProgrammingExercise;
    let titleBarActionViews: EmbeddedViewRef<unknown>[];

    beforeEach(async () => {
        exercise = { id: 5, title: 'Bubble Sort', shortName: 'bubblesort' } as ProgrammingExercise;
        const route = { snapshot: { data: { exercise } }, parent: { snapshot: { paramMap: convertToParamMap({ courseId: '7' }) } } } as unknown as ActivatedRoute;
        titleBarActionViews = [];
        metisService = {
            config: vi.fn().mockReturnValue(of({ exerciseId: 5, enabled: true, likertScalePoints: 7 })),
            dashboard: vi.fn().mockReturnValue(of({ summary, rows: createRows() })),
            exportCsv: vi.fn().mockReturnValue(of(csvFile)),
            generateNow: vi.fn().mockReturnValue(of({ createdChecks: 2, requeuedChecks: 1 })),
        };
        await TestBed.configureTestingModule({
            imports: [MetisDashboardComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: MetisService, useValue: metisService },
                MockProvider(AlertService),
            ],
        })
            .overrideComponent(MetisDashboardComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe, ArtemisDatePipe, RatingSquareComponent] },
                add: {
                    imports: [
                        MockDirective(TranslateDirective),
                        MockPipe(ArtemisTranslatePipe, (key: string) => key),
                        MockPipe(ArtemisDatePipe),
                        MockComponent(RatingSquareComponent),
                    ],
                },
            })
            .overrideComponent(RatingDistributionComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe, TumAetUiBarChartComponent] },
                add: { imports: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe, (key: string) => key), MockComponent(TumAetUiBarChartComponent)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(MetisDashboardComponent);
        component = fixture.componentInstance;
        alertService = TestBed.inject(AlertService);
        fixture.detectChanges();
    });

    afterEach(() => {
        titleBarActionViews.forEach((view) => view.destroy());
        vi.restoreAllMocks();
    });

    function element(testId: string): HTMLElement | null {
        return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
    }

    /** Renders the `*titleBarActions` template as `jhi-course-title-bar` would, so its buttons can be queried. */
    function renderTitleBarActions(): DebugElement {
        const view = TestBed.inject(CourseTitleBarService).actionsTemplate()!.createEmbeddedView({});
        titleBarActionViews.push(view);
        view.detectChanges();
        return getDebugNode(view.rootNodes[0]) as DebugElement;
    }

    function text(testId: string): string {
        return element(testId)?.textContent?.trim() ?? '';
    }

    /** The participation ids of the table rows in display order, without the opened question threads. */
    function rowIds(): number[] {
        const rows: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('tbody tr[data-testid^="dashboard-row-"]:not([data-testid^="dashboard-row-details-"])'));
        return rows.map((row) => Number(row.dataset['testid']?.replace('dashboard-row-', '')));
    }

    function search(term: string): void {
        const input: HTMLInputElement = fixture.nativeElement.querySelector('[data-testid="dashboard-search"] input');
        input.value = term;
        input.dispatchEvent(new Event('input'));
        fixture.detectChanges();
    }

    /** Opens the select whose trigger carries the id and clicks the option at the index; the option list is rendered in an overlay outside the fixture. */
    function selectOption(triggerId: string, index: number): void {
        (fixture.nativeElement.querySelector(`#${triggerId}`) as HTMLButtonElement).click();
        fixture.detectChanges();
        const options = Array.from(document.querySelectorAll<HTMLElement>('[role="listbox"] [role="option"]'));
        options[index].click();
        fixture.detectChanges();
    }

    it('should load the dashboard of the resolved exercise and fill the tiles', () => {
        expect(metisService.dashboard).toHaveBeenCalledWith(5);
        expect(component['courseId']).toBe(7);
        expect(text('tile-completed')).toBe('1');
        expect(text('tile-in-progress')).toBe('1');
        expect(text('tile-not-started')).toBe('1');
        expect(element('tile-window-end')).not.toBeNull();
        expect(text('tile-average')).toBe('3.4');
        expect(text('tile-follow-ups')).toBe('3');
        expect(text('tile-skipped-or-failed')).toBe('1');
    });

    it('should read every rating against the Likert scale of the exercise configuration', () => {
        expect(metisService.config).toHaveBeenCalledWith(5);
        expect(component['points']()).toBe(7);

        const distribution = fixture.debugElement.query(By.directive(RatingDistributionComponent)).componentInstance as RatingDistributionComponent;
        expect(distribution.points()).toBe(7);

        (element('dashboard-expand-1') as HTMLButtonElement).click();
        fixture.detectChanges();

        // Three squares per table row with questions (Anna, Ben, Clara), plus one per rated question in Anna's open thread.
        const squares = fixture.debugElement.queryAll(By.directive(RatingSquareComponent));
        expect(squares).toHaveLength(3 * 3 + 5);
        expect(squares.every((square) => square.componentInstance.points() === 7)).toBe(true);
    });

    it('should list every participation sorted by name', () => {
        expect(rowIds()).toEqual([1, 2, 3, 4]);
        expect(component.visibleRows()).toHaveLength(4);
        expect(component['totalCount']()).toBe(4);
        expect(component['questionCount']()).toBe(3);
        expect(element('dashboard-showing')).not.toBeNull();
    });

    it('should show the figures of a row', () => {
        const [anna, ben, clara] = component.visibleRows();

        expect(anna.followUps).toBe(2);
        expect(anna.average).toBeCloseTo(11 / 3);
        expect(anna.completedAt?.isSame(dayjs('2026-11-03T10:16:00'))).toBe(true);
        expect(ben.followUps).toBe(1);
        expect(ben.average).toBe(3);
        expect(clara.average).toBeUndefined();
        expect(element('dashboard-row-1')?.querySelectorAll('jhi-rating-square')).toHaveLength(3);
        expect(component.statusSeverity(MetisCheckStatus.COMPLETED)).toBe('success');
        expect(component.statusSeverity(MetisCheckStatus.FAILED)).toBe('danger');
    });

    it('should offer the tutorial groups as filter options', () => {
        expect(component['groupOptions']().map((option) => option.value)).toEqual(['ALL', 'Gruppe 1', 'Gruppe 2']);
        expect(component['statusOptions']().map((option) => option.value)).toEqual(['ALL', 'COMPLETED', 'IN_PROGRESS', 'NOT_STARTED', 'SKIPPED', 'FAILED']);
        expect(component['sortOptions']().map((option) => option.value)).toEqual(['name', 'lowestAverage', 'mostFollowUps', 'completedLast']);
    });

    it('should filter by tutorial group', () => {
        // The group select lists all groups, Gruppe 1 and Gruppe 2.
        selectOption('metis-dashboard-group-filter', 1);
        expect(component.groupFilter()).toBe('Gruppe 1');
        expect(rowIds()).toEqual([1, 3]);

        component.onGroupFilterChange('Gruppe 2');
        fixture.detectChanges();
        expect(rowIds()).toEqual([2, 4]);
    });

    it('should filter by status', () => {
        // The status select lists all, completed, in progress, not started, skipped and failed.
        selectOption('metis-dashboard-status-filter', 3);
        expect(component.statusFilter()).toBe('NOT_STARTED');
        expect(rowIds()).toEqual([3]);

        component.onStatusFilterChange('COMPLETED');
        fixture.detectChanges();
        expect(rowIds()).toEqual([1]);

        component.onStatusFilterChange('ALL');
        fixture.detectChanges();
        expect(rowIds()).toEqual([1, 2, 3, 4]);
    });

    it('should search by name or login', () => {
        search('GRUBER');
        expect(rowIds()).toEqual([2]);

        search('david');
        expect(rowIds()).toEqual([4]);

        search('nobody');
        expect(rowIds()).toEqual([]);
        expect(fixture.nativeElement.querySelector('tbody td[colspan="9"]')).not.toBeNull();
    });

    it('should sort by the chosen preset', () => {
        // The sort select lists name, lowest average, most follow-ups and completed last.
        selectOption('metis-dashboard-sort', 1);
        expect(component['sortPreset']()).toBe('lowestAverage');
        expect(rowIds()).toEqual([2, 1, 3, 4]);

        component.onSortPresetChange('mostFollowUps');
        fixture.detectChanges();
        expect(rowIds()).toEqual([1, 2, 3, 4]);

        component.onSortPresetChange('completedLast');
        fixture.detectChanges();
        expect(rowIds()).toEqual([1, 2, 3, 4]);

        component.onSortPresetChange(undefined);
        expect(component['sortPreset']()).toBe('completedLast');
    });

    it('should sort through the table headers and turn the order around on the second click', () => {
        const averageHeader: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="dashboard-sort-average"] button');

        averageHeader.click();
        fixture.detectChanges();
        expect(component.sortField()).toBe('average');
        expect(component.sortOrder()).toBe(1);
        expect(rowIds()).toEqual([2, 1, 3, 4]);

        averageHeader.click();
        fixture.detectChanges();
        expect(component.sortOrder()).toBe(-1);
        expect(component['sortPreset']()).toBeUndefined();
        expect(rowIds()).toEqual([1, 2, 3, 4]);

        component.onSortChange({ field: 'unknown', order: 1 });
        expect(component.sortField()).toBe('average');
    });

    it('should expand a row into the question thread', () => {
        expect(element('dashboard-row-details-1')).toBeNull();

        const expander = element('dashboard-expand-1') as HTMLButtonElement;
        expander.click();
        fixture.detectChanges();

        const details = element('dashboard-row-details-1');
        expect(details).not.toBeNull();
        expect(expander.getAttribute('aria-expanded')).toBe('true');
        expect(details?.textContent).toContain('Question 101');
        expect(details?.textContent).toContain('Because that is how bubble sort works, the loop gets shorter.');
        expect(details?.textContent).toContain('Repeats what the code does without saying why.');
        expect(details?.querySelectorAll('jhi-rating-square')).toHaveLength(5);
        expect(element('dashboard-metadata-1')).not.toBeNull();

        (element('dashboard-open-thread-1') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(element('dashboard-row-details-1')).toBeNull();
        expect(expander.getAttribute('aria-expanded')).toBe('false');
    });

    it('should say when a question has no answer yet', () => {
        (element('dashboard-expand-3') as HTMLButtonElement).click();
        fixture.detectChanges();

        const details = element('dashboard-row-details-3');
        expect(details?.querySelectorAll('[jhiTranslate="artemisApp.metis.dashboard.notAnswered"]')).toHaveLength(3);
        expect(details?.querySelectorAll('jhi-rating-square')).toHaveLength(0);
    });

    it('should show the file name of a focus file in its chip and keep the full path as its tooltip', () => {
        const repositoryPath = 'src/de/tum/in/www1/example/SortUtils.java';
        const withRepositoryPath = (question: MetisQuestionDTO): MetisQuestionDTO => (question.id === 104 ? { ...question, focusFilePath: repositoryPath } : question);
        component.rows.update((rows) => rows.map((row) => ({ ...row, questions: row.questions.map(withRepositoryPath) })));
        (element('dashboard-expand-1') as HTMLButtonElement).click();
        fixture.detectChanges();

        const chips = Array.from(element('dashboard-row-details-1')!.querySelectorAll<HTMLElement>('[data-testid="metis-focus-chip"]'));
        expect(chips).toHaveLength(5);
        expect(chips[3].querySelector('[data-testid="metis-focus-chip-file"]')?.textContent).toBe('SortUtils.java');
        expect(chips[3].getAttribute('title')).toBe(repositoryPath);
        expect(chips[0].querySelector('[data-testid="metis-focus-chip-file"]')?.textContent).toBe('BubbleSort.java');
    });

    it('should download the CSV file of the server and count the students', () => {
        const successSpy = vi.spyOn(alertService, 'success');
        const downloadSpy = vi.spyOn(component as any, 'downloadBlob').mockImplementation(() => {});

        component.exportCsv();

        expect(metisService.exportCsv).toHaveBeenCalledOnce();
        expect(metisService.exportCsv).toHaveBeenCalledWith(5);
        expect(downloadSpy).toHaveBeenCalledOnce();
        expect(downloadSpy).toHaveBeenCalledWith(csvFile, 'bubblesort-comprehension-check');
        expect(successSpy).toHaveBeenCalledWith('artemisApp.metis.dashboard.exported', { fileName: 'bubblesort-comprehension-check', count: 4 });
    });

    it('should hand the file to the browser download with the CSV extension', () => {
        // jsdom reports the download navigation on the console; it is not an error of the component.
        vi.spyOn(console, 'error').mockImplementation(() => {});
        const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click');

        component['downloadBlob'](csvFile, 'bubblesort-comprehension-check');

        expect(window.URL.createObjectURL).toHaveBeenCalledWith(csvFile);
        expect(clickSpy).toHaveBeenCalledOnce();
        expect((clickSpy.mock.contexts[0] as HTMLAnchorElement).download).toBe('bubblesort-comprehension-check.csv');
    });

    it('should say so when the export response carries no file', () => {
        const successSpy = vi.spyOn(alertService, 'success');
        const errorSpy = vi.spyOn(alertService, 'error');
        const downloadSpy = vi.spyOn(component as any, 'downloadBlob').mockImplementation(() => {});
        metisService.exportCsv.mockReturnValue(throwError(() => new Error('The export response carried no file.')));

        component.exportCsv();

        expect(downloadSpy).not.toHaveBeenCalled();
        expect(successSpy).not.toHaveBeenCalled();
        expect(errorSpy).toHaveBeenCalledOnce();
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.metis.dashboard.exportFailed');
    });

    it('should leave a refused export to the global error handling', () => {
        const errorSpy = vi.spyOn(alertService, 'error');
        const downloadSpy = vi.spyOn(component as any, 'downloadBlob').mockImplementation(() => {});
        metisService.exportCsv.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

        component.exportCsv();

        expect(downloadSpy).not.toHaveBeenCalled();
        expect(errorSpy).not.toHaveBeenCalled();
    });

    it('should offer generate now to instructors only', () => {
        expect(renderTitleBarActions().query(By.css('[data-testid="dashboard-generate"]'))).toBeNull();
        expect(renderTitleBarActions().query(By.css('[data-testid="dashboard-export"]'))).not.toBeNull();

        exercise.isAtLeastInstructor = true;
        fixture = TestBed.createComponent(MetisDashboardComponent);
        fixture.detectChanges();

        expect(renderTitleBarActions().query(By.css('[data-testid="dashboard-generate"]'))).not.toBeNull();
    });

    it('should offer the question review and the settings to editors only', () => {
        expect(renderTitleBarActions().query(By.css('[data-testid="dashboard-review"]'))).toBeNull();
        expect(renderTitleBarActions().query(By.css('[data-testid="dashboard-settings"]'))).toBeNull();
        expect(renderTitleBarActions().query(By.css('[data-testid="dashboard-back"]'))).not.toBeNull();

        exercise.isAtLeastEditor = true;
        fixture = TestBed.createComponent(MetisDashboardComponent);
        fixture.detectChanges();

        expect(renderTitleBarActions().query(By.css('[data-testid="dashboard-review"]'))).not.toBeNull();
        expect(renderTitleBarActions().query(By.css('[data-testid="dashboard-settings"]'))).not.toBeNull();
    });

    it('should generate the missing checks, report the counts and reload the dashboard', () => {
        exercise.isAtLeastInstructor = true;
        fixture = TestBed.createComponent(MetisDashboardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        const successSpy = vi.spyOn(alertService, 'success');
        metisService.dashboard.mockClear();

        (renderTitleBarActions().query(By.css('[data-testid="dashboard-generate"]')).nativeElement as HTMLButtonElement).click();

        expect(metisService.generateNow).toHaveBeenCalledOnce();
        expect(metisService.generateNow).toHaveBeenCalledWith(5);
        expect(successSpy).toHaveBeenCalledWith('artemisApp.metis.dashboard.generated', { created: 2, requeued: 1 });
        expect(metisService.dashboard).toHaveBeenCalledOnce();
        expect(metisService.dashboard).toHaveBeenCalledWith(5);
    });

    it('should switch the distribution between groups and files', () => {
        const distribution = fixture.debugElement.query(By.directive(RatingDistributionComponent)).componentInstance as RatingDistributionComponent;
        expect(distribution['chartData']().labels).toEqual(['artemisApp.metis.dashboard.distribution.allStudents', 'Gruppe 1']);

        const modeButtons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('[data-testid="rating-distribution-mode"] button'));
        modeButtons[1].click();
        fixture.detectChanges();

        expect(distribution['chartData']().labels).toEqual(['BubbleSort.java', 'BubbleSortTest.java', 'SortUtils.java']);
    });
});
