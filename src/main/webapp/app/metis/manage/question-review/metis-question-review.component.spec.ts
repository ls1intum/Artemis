import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Subject, of, throwError } from 'rxjs';
import { type Mock, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';
import { MetisQuestionReviewComponent } from 'app/metis/manage/question-review/metis-question-review.component';
import { MetisCheckStatus, MetisDashboardRowDTO, MetisDashboardSummaryDTO, MetisQuestionDTO } from 'app/metis/shared/entities/metis.model';
import { MetisService } from 'app/metis/shared/services/metis.service';
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

/** Anna is approved, Ben waits for the review, Clara handed nothing in, David's generation failed, Eva has started with one question that nobody approved. */
function createRows(): MetisDashboardRowDTO[] {
    return [
        {
            participationId: 1,
            studentName: 'Anna Huber',
            studentLogin: 'anna',
            tutorialGroupTitle: 'Gruppe 1',
            check: { id: 11, participationId: 1, status: MetisCheckStatus.READY, commitHash: 'a3f9c21', llmModel: 'qwen3-coder:30b', promptVersion: 'v1', generatedAt },
            questions: [
                question({ id: 101, checkId: 11, questionOrder: 1, approvedAt }),
                question({ id: 102, checkId: 11, questionOrder: 2, focusFilePath: 'SortUtils.java', focusFileChangedLines: 20, approvedAt }),
                question({ id: 103, checkId: 11, questionOrder: 3, focusFilePath: 'BubbleSortTest.java', focusFileChangedLines: 20, approvedAt }),
            ],
        },
        {
            participationId: 2,
            studentName: 'Ben Gruber',
            studentLogin: 'ben',
            tutorialGroupTitle: 'Gruppe 2',
            check: { id: 22, participationId: 2, status: MetisCheckStatus.IN_REVIEW, commitHash: 'b7e2d10', llmModel: 'qwen3-coder:30b', promptVersion: 'v1', generatedAt },
            questions: [
                question({ id: 201, checkId: 22, questionOrder: 1 }),
                question({ id: 202, checkId: 22, questionOrder: 2, focusFilePath: 'SortUtils.java' }),
                question({ id: 203, checkId: 22, questionOrder: 3, focusFilePath: 'BubbleSortTest.java' }),
            ],
        },
        {
            participationId: 3,
            studentName: 'Clara Bauer',
            studentLogin: 'clara',
            tutorialGroupTitle: 'Gruppe 1',
            check: { id: 33, participationId: 3, status: MetisCheckStatus.SKIPPED, failureReason: 'NO_SUBMISSION' },
            questions: [],
        },
        {
            participationId: 4,
            studentName: 'David Wagner',
            studentLogin: 'david',
            tutorialGroupTitle: 'Gruppe 2',
            check: { id: 44, participationId: 4, status: MetisCheckStatus.FAILED, failureReason: 'LLM_UNAVAILABLE' },
            questions: [],
        },
        {
            participationId: 5,
            studentName: 'Eva Pichler',
            studentLogin: 'eva',
            tutorialGroupTitle: 'Gruppe 1',
            check: { id: 55, participationId: 5, status: MetisCheckStatus.IN_PROGRESS, commitHash: 'c1d2e3f', generatedAt },
            questions: [
                question({ id: 501, checkId: 55, questionOrder: 1, approvedAt, shownAt: approvedAt }),
                question({ id: 502, checkId: 55, questionOrder: 2, focusFilePath: 'SortUtils.java' }),
            ],
        },
    ];
}

const summary: MetisDashboardSummaryDTO = { participants: 5, completed: 0, inProgress: 1, notStarted: 2, skippedOrFailed: 2, awaitingReview: 1, followUps: 0 };

interface FakeMetisService {
    dashboard: Mock;
    approveQuestion: Mock;
    approveAllQuestions: Mock;
    updateQuestionText: Mock;
    regenerateQuestion: Mock;
}

/**
 * A stateful stand-in for the server of an exercise that requires a review: every mutation changes the stored rows and
 * answers with a copy of the changed question, approving everything releases the checks in review, a regeneration
 * returns a released check to review, and the next load hands out fresh copies of the rows, as two requests would.
 */
function createFakeService(rows: MetisDashboardRowDTO[]): FakeMetisService {
    const findQuestion = (questionId: number) => rows.flatMap((row) => row.questions).find((candidate) => candidate.id === questionId)!;
    // The checks are copied as well: the page must not see a later change of the server through an object it loaded earlier
    const snapshot = () => ({ summary, rows: rows.map((row) => ({ ...row, check: { ...row.check }, questions: row.questions.map((candidate) => ({ ...candidate })) })) });
    return {
        dashboard: vi.fn(() => of(snapshot())),
        approveQuestion: vi.fn((_exerciseId: number, questionId: number) => {
            const target = findQuestion(questionId);
            target.approvedAt = dayjs();
            return of({ ...target });
        }),
        approveAllQuestions: vi.fn(() => {
            rows.flatMap((row) => row.questions)
                .filter((candidate) => candidate.parentQuestionId === undefined && candidate.approvedAt === undefined)
                .forEach((candidate) => (candidate.approvedAt = dayjs()));
            rows.filter((row) => row.check.status === MetisCheckStatus.IN_REVIEW).forEach((row) => (row.check.status = MetisCheckStatus.READY));
            return of(undefined);
        }),
        updateQuestionText: vi.fn((_exerciseId: number, questionId: number, text: string) => {
            const target = findQuestion(questionId);
            target.questionText = text;
            return of({ ...target });
        }),
        regenerateQuestion: vi.fn((_exerciseId: number, questionId: number) => {
            const target = findQuestion(questionId);
            target.questionText = `Regenerated ${questionId}`;
            target.regenerationCount += 1;
            target.approvedAt = undefined;
            const { check } = rows.find((row) => row.questions.includes(target))!;
            if (check.status === MetisCheckStatus.READY) {
                check.status = MetisCheckStatus.IN_REVIEW;
            }
            return of({ ...target });
        }),
    };
}

describe('MetisQuestionReviewComponent', () => {
    let fixture: ComponentFixture<MetisQuestionReviewComponent>;
    let component: MetisQuestionReviewComponent;
    let alertService: AlertService;
    let metisService: FakeMetisService;

    const exercise = { id: 5, title: 'Bubble Sort', shortName: 'bubblesort' } as ProgrammingExercise;
    const route = { snapshot: { data: { exercise } }, parent: { snapshot: { paramMap: convertToParamMap({ courseId: '7' }) } } } as unknown as ActivatedRoute;

    beforeEach(async () => {
        metisService = createFakeService(createRows());
        await TestBed.configureTestingModule({
            imports: [MetisQuestionReviewComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: MetisService, useValue: metisService },
                MockProvider(AlertService),
            ],
        })
            .overrideComponent(MetisQuestionReviewComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe, ArtemisDatePipe] },
                add: { imports: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe, (key: string) => key), MockPipe(ArtemisDatePipe)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(MetisQuestionReviewComponent);
        component = fixture.componentInstance;
        alertService = TestBed.inject(AlertService);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function element(testId: string): HTMLElement | null {
        return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
    }

    function click(testId: string): void {
        const target = element(testId);
        expect(target).not.toBeNull();
        (target as HTMLElement).click();
        fixture.detectChanges();
    }

    function studentIds(): number[] {
        const sections: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('[data-testid^="review-student-"]'));
        return sections.map((section) => Number(section.dataset['testid']?.replace('review-student-', '')));
    }

    /** Opens the select whose trigger carries the id and clicks the option at the index; the option list is rendered in an overlay outside the fixture. */
    function selectOption(triggerId: string, index: number): void {
        (fixture.nativeElement.querySelector(`#${triggerId}`) as HTMLButtonElement).click();
        fixture.detectChanges();
        const options = Array.from(document.querySelectorAll<HTMLElement>('[role="listbox"] [role="option"]'));
        options[index].click();
        fixture.detectChanges();
    }

    /** The key the mocked translate directive on the regenerate button of the question was given. */
    function regenerateLabelKey(questionId: number): string | undefined {
        const label = fixture.debugElement.query(By.css(`[data-testid="review-regenerate-${questionId}"]`)).query(By.directive(TranslateDirective));
        return label.injector.get(TranslateDirective).jhiTranslate();
    }

    it('should load the rows of the resolved exercise and count the review states', () => {
        expect(metisService.dashboard).toHaveBeenCalledOnce();
        expect(metisService.dashboard).toHaveBeenCalledWith(5);
        expect(studentIds()).toEqual([1, 2, 3, 4, 5]);
        expect(component.counts()).toEqual({ PREPARING: 0, AWAITING: 1, RELEASED: 2, SKIPPED: 1, FAILED: 1 });
        expect(element('review-count-awaiting')).not.toBeNull();
        expect(element('review-count-released')).not.toBeNull();
        expect(element('review-count-preparing')).toBeNull();
        expect(element('review-approve-student-2')).not.toBeNull();
        expect(element('review-approve-student-1')).toBeNull();
        // Eva has started, so her check is past its review although one of its questions was never approved
        expect(element('review-status-5')).not.toBeNull();
        expect(element('review-approve-student-5')).toBeNull();
    });

    it('should show a check whose questions are not generated yet as being prepared', () => {
        component.rows.update((rows) => [
            ...rows,
            { participationId: 6, studentName: 'Felix Maier', studentLogin: 'felix', check: { id: 66, participationId: 6, status: MetisCheckStatus.PENDING }, questions: [] },
        ]);
        fixture.detectChanges();

        expect(component.counts().PREPARING).toBe(1);
        expect(element('review-count-preparing')).not.toBeNull();
        expect(element('review-preparing-6')).not.toBeNull();
        expect(element('review-approve-student-6')).toBeNull();
    });

    it('should offer no approval for questions that were released without a review, but keep them editable', () => {
        // Without a review the questions are released at once and never approved; they can still change until the student sees them
        component.rows.update((rows) =>
            rows.map((row) => (row.participationId === 1 ? { ...row, questions: row.questions.map((candidate) => ({ ...candidate, approvedAt: undefined })) } : row)),
        );
        fixture.detectChanges();

        expect(component.counts().RELEASED).toBe(2);
        expect(element('review-approve-student-1')).toBeNull();
        expect(element('review-approve-101')).toBeNull();
        expect(element('review-approved-101')).toBeNull();
        expect(element('review-edit-button-101')).not.toBeNull();
        expect(element('review-regenerate-101')).not.toBeNull();
    });

    it('should translate the failure reason of a failed check', () => {
        expect(element('review-failure-4')?.textContent).toContain('artemisApp.metis.failureReason.LLM_UNAVAILABLE');
        expect(element('review-failure-4')?.textContent).not.toContain('artemisApp.metis.review.failedHint');
    });

    it('should fall back to the hint for a failed check without a reason', () => {
        component.rows.update((rows) => rows.map((row) => (row.participationId === 4 ? { ...row, check: { ...row.check, failureReason: undefined } } : row)));
        fixture.detectChanges();

        expect(element('review-failure-4')?.textContent).toContain('artemisApp.metis.review.failedHint');
    });

    it('should show the translated reason under the hint of a skipped check', () => {
        expect(element('review-student-3')?.querySelector('[jhiTranslate="artemisApp.metis.review.skippedHint"]')).not.toBeNull();
        expect(element('review-skip-reason-3')?.textContent).toContain('artemisApp.metis.failureReason.NO_SUBMISSION');

        component.rows.update((rows) => rows.map((row) => (row.participationId === 3 ? { ...row, check: { ...row.check, failureReason: undefined } } : row)));
        fixture.detectChanges();

        expect(element('review-skip-reason-3')).toBeNull();
    });

    it('should show the file name of a focus file in its chip and keep the full path as its tooltip', () => {
        const repositoryPath = 'src/de/tum/in/www1/example/BubbleSort.java';
        const withRepositoryPath = (question: MetisQuestionDTO): MetisQuestionDTO => (question.id === 201 ? { ...question, focusFilePath: repositoryPath } : question);
        component.rows.update((rows) => rows.map((row) => ({ ...row, questions: row.questions.map(withRepositoryPath) })));
        fixture.detectChanges();

        const chip = element('review-question-201')?.querySelector('[data-testid="metis-focus-chip"]');
        expect(chip?.querySelector('[data-testid="metis-focus-chip-file"]')?.textContent).toBe('BubbleSort.java');
        expect(chip?.getAttribute('title')).toBe(repositoryPath);
        expect(element('review-question-202')?.querySelector('[data-testid="metis-focus-chip-file"]')?.textContent).toBe('SortUtils.java');
    });

    it('should approve a single question with the exercise and patch it into its row without a reload', () => {
        expect(element('review-approved-201')).toBeNull();
        const otherRow = component.rows()[0];

        click('review-approve-201');

        expect(metisService.approveQuestion).toHaveBeenCalledOnce();
        expect(metisService.approveQuestion).toHaveBeenCalledWith(5, 201);
        expect(metisService.dashboard).toHaveBeenCalledOnce();
        expect(element('review-approved-201')).not.toBeNull();
        expect(element('review-approve-201')).toBeNull();
        expect(component.counts().AWAITING).toBe(1);
        // Only the row of the question is replaced.
        expect(component.rows()[0]).toBe(otherRow);
    });

    it('should approve everything that is still open for one student and patch every result', () => {
        click('review-approve-student-2');

        expect(metisService.approveQuestion).toHaveBeenCalledTimes(3);
        expect(metisService.approveQuestion.mock.calls).toEqual([
            [5, 201],
            [5, 202],
            [5, 203],
        ]);
        expect(metisService.dashboard).toHaveBeenCalledOnce();
        // The server releases the check with the last approval; the page only patched the questions, which suffices
        expect(component.counts()).toEqual({ PREPARING: 0, AWAITING: 0, RELEASED: 3, SKIPPED: 1, FAILED: 1 });
        expect(element('review-approve-student-2')).toBeNull();
    });

    it('should not call the service for a student without open questions', () => {
        component.approveStudent(component.visibleStudents()[0]);

        expect(metisService.approveQuestion).not.toHaveBeenCalled();
    });

    it('should approve all remaining questions of the exercise and reload once', () => {
        component.approveAllRemaining();
        fixture.detectChanges();

        expect(metisService.approveAllQuestions).toHaveBeenCalledOnce();
        expect(metisService.approveAllQuestions).toHaveBeenCalledWith(5);
        expect(metisService.dashboard).toHaveBeenCalledTimes(2);
        expect(component.counts().AWAITING).toBe(0);
        expect(element('review-approved-203')).not.toBeNull();
    });

    it('should edit the text of a question and save it without a reload', () => {
        click('review-edit-button-201');

        const textarea = element('review-edit-201') as HTMLTextAreaElement;
        expect(textarea).not.toBeNull();
        expect(component.editDraft()).toBe('Question 201');

        textarea.value = 'What does the inner loop bound guarantee?';
        textarea.dispatchEvent(new Event('input'));
        fixture.detectChanges();
        click('review-save-201');

        expect(metisService.updateQuestionText).toHaveBeenCalledOnce();
        expect(metisService.updateQuestionText).toHaveBeenCalledWith(5, 201, 'What does the inner loop bound guarantee?');
        expect(metisService.dashboard).toHaveBeenCalledOnce();
        expect(component.editingQuestionId()).toBeUndefined();
        expect(element('review-text-201')?.textContent).toContain('What does the inner loop bound guarantee?');
    });

    it('should keep an empty text out of the service and close the editor on cancel', () => {
        click('review-edit-button-202');
        component.editDraft.set('   ');
        fixture.detectChanges();

        expect((element('review-save-202') as HTMLButtonElement).disabled).toBe(true);
        component.saveText(component.visibleStudents()[1].mainQuestions[1]);
        expect(metisService.updateQuestionText).not.toHaveBeenCalled();

        click('review-cancel-202');

        expect(component.editingQuestionId()).toBeUndefined();
        expect(element('review-edit-202')).toBeNull();
    });

    it('should keep the editor open and reload once when the server refuses the new text', () => {
        metisService.updateQuestionText.mockReturnValue(throwError(() => new Error('409 metis.notEditable')));
        click('review-edit-button-201');

        click('review-save-201');

        expect(metisService.dashboard).toHaveBeenCalledTimes(2);
        expect(component.editingQuestionId()).toBe(201);
    });

    it('should regenerate a question with the exercise, announce the new wording and load the rows again', () => {
        const infoSpy = vi.spyOn(alertService, 'info');

        click('review-regenerate-201');

        expect(metisService.regenerateQuestion).toHaveBeenCalledOnce();
        expect(metisService.regenerateQuestion).toHaveBeenCalledWith(5, 201);
        expect(metisService.dashboard).toHaveBeenCalledTimes(2);
        expect(element('review-text-201')?.textContent).toContain('Regenerated 201');
        expect(element('review-regeneration-count-201')).not.toBeNull();
        expect(infoSpy).toHaveBeenCalledWith('artemisApp.metis.review.regenerated', { count: 1 });
        expect(component.regeneratingIds().has(201)).toBe(false);
    });

    it('should show the check in review again when the new wording withdraws the approval that released it', () => {
        // The editor approves everything while the model still writes Ben's first question: the server releases Ben's check, and the page loads it as released
        const regeneration = new Subject<MetisQuestionDTO>();
        metisService.regenerateQuestion.mockReturnValueOnce(regeneration);
        click('review-regenerate-201');
        component.approveAllRemaining();
        fixture.detectChanges();
        expect(component.rows()[1].check.status).toBe(MetisCheckStatus.READY);
        expect(component.counts().AWAITING).toBe(0);

        // The model answers: the server stores the new wording without the approval and returns the check to review, and the pending request answers with it
        metisService.regenerateQuestion(5, 201).subscribe(regeneration);
        fixture.detectChanges();

        expect(element('review-text-201')?.textContent).toContain('Regenerated 201');
        expect(component.counts().AWAITING).toBe(1);
        expect(element('review-approve-201')).not.toBeNull();
    });

    it('should reload once and release the regenerate button when the regeneration fails', () => {
        const infoSpy = vi.spyOn(alertService, 'info');
        metisService.regenerateQuestion.mockReturnValue(throwError(() => new Error('500 metis.llmUnavailable')));

        click('review-regenerate-201');

        expect(metisService.dashboard).toHaveBeenCalledTimes(2);
        expect(infoSpy).not.toHaveBeenCalled();
        expect(component.regeneratingIds().has(201)).toBe(false);
    });

    it('should reload once when the server refuses an approval', () => {
        metisService.approveQuestion.mockReturnValue(throwError(() => new Error('404')));

        click('review-approve-201');

        expect(metisService.dashboard).toHaveBeenCalledTimes(2);
    });

    it('should offer edit and regenerate only while the server still changes the question', () => {
        // Eva has started: her open question can no longer be approved, edited or regenerated.
        expect(element('review-approve-502')).toBeNull();
        expect(element('review-edit-button-502')).toBeNull();
        expect(element('review-regenerate-502')).toBeNull();
        expect(element('review-edit-button-201')).not.toBeNull();
        expect(element('review-regenerate-201')).not.toBeNull();

        const [anna, ben, , , eva] = component.rows();
        expect(component.isEditable(ben, ben.questions[0])).toBe(true);
        expect(component.isEditable(anna, anna.questions[0])).toBe(true);
        expect(component.isEditable(ben, { ...ben.questions[0], shownAt: dayjs() })).toBe(false);
        expect(component.isEditable(eva, eva.questions[1])).toBe(false);
        expect(component.isEditable({ ...ben, check: { ...ben.check, status: MetisCheckStatus.COMPLETED } }, ben.questions[0])).toBe(false);
    });

    it('should disable the regenerate button and say so until the new wording is there', () => {
        const infoSpy = vi.spyOn(alertService, 'info');
        const regeneration = new Subject<MetisQuestionDTO>();
        metisService.regenerateQuestion.mockReturnValue(regeneration);

        click('review-regenerate-201');

        expect((element('review-regenerate-201') as HTMLButtonElement).disabled).toBe(true);
        expect(regenerateLabelKey(201)).toBe('artemisApp.metis.review.regenerating');
        expect(component.regeneratingIds().has(201)).toBe(true);
        expect(infoSpy).not.toHaveBeenCalled();

        regeneration.next(question({ id: 201, checkId: 22, questionOrder: 1, questionText: 'Regenerated 201', regenerationCount: 1 }));
        regeneration.complete();
        fixture.detectChanges();

        expect(infoSpy).toHaveBeenCalledWith('artemisApp.metis.review.regenerated', { count: 1 });
        expect(component.regeneratingIds().has(201)).toBe(false);
        expect((element('review-regenerate-201') as HTMLButtonElement).disabled).toBe(false);
        expect(regenerateLabelKey(201)).toBe('artemisApp.metis.review.regenerate');
    });

    it('should filter by review state and tutorial group', () => {
        // The status select lists all, awaiting, released, being prepared, skipped and failed; the group select lists all groups, Gruppe 1 and Gruppe 2.
        selectOption('metis-review-status-filter', 4);
        expect(component.statusFilter()).toBe('SKIPPED');
        expect(studentIds()).toEqual([3]);

        selectOption('metis-review-status-filter', 0);
        selectOption('metis-review-group-filter', 2);
        expect(component.statusFilter()).toBe('ALL');
        expect(component.groupFilter()).toBe('Gruppe 2');
        expect(studentIds()).toEqual([2, 4]);

        component.onStatusFilterChange('FAILED');
        fixture.detectChanges();
        expect(studentIds()).toEqual([4]);

        component.onGroupFilterChange('Gruppe 1');
        fixture.detectChanges();
        expect(studentIds()).toEqual([]);
        expect(element('review-empty')).not.toBeNull();
    });

    it('should offer the groups and review states as filter options', () => {
        expect(component['groupOptions']().map((option) => option.value)).toEqual(['ALL', 'Gruppe 1', 'Gruppe 2']);
        expect(component['statusOptions']().map((option) => option.value)).toEqual(['ALL', 'AWAITING', 'RELEASED', 'PREPARING', 'SKIPPED', 'FAILED']);
        expect(component.statusSeverity('FAILED')).toBe('danger');
        expect(component.statusSeverity('RELEASED')).toBe('success');
        expect(component.statusSeverity('PREPARING')).toBe('info');
    });
});
