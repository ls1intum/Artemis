import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { NEVER, Subject, of, throwError } from 'rxjs';
import { type Mock, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComprehensionCheckPanelComponent } from 'app/metis/overview/comprehension-check-panel/comprehension-check-panel.component';
import { QuestionTimerComponent } from 'app/metis/overview/question-timer/question-timer.component';
import { MetisCheckStatus, MetisQuestionDTO, MetisStudentCheckDTO } from 'app/metis/shared/entities/metis.model';
import { RatingSquareComponent } from 'app/metis/shared/rating-square/rating-square.component';
import { MetisService } from 'app/metis/shared/services/metis.service';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { UserService } from 'app/account/user/shared/user.service';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockArtemisServerDateService } from 'test/helpers/mocks/service/mock-server-date.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ComprehensionCheckPanelComponent', () => {
    const exerciseId = 7;
    const windowStart = dayjs('2026-11-03T10:15:00');
    const windowEnd = dayjs('2026-11-03T10:35:00');
    const duringWindow = windowStart.add(1, 'minute');

    let fixture: ComponentFixture<ComprehensionCheckPanelComponent>;
    let component: ComprehensionCheckPanelComponent;
    let accountService: MockAccountService;
    let serverDateService: ArtemisServerDateService;
    let metisService: { studentCheck: Mock; startCheck: Mock; submitAnswer: Mock };
    let userService: { updateLLMSelectionDecision: Mock };
    let llmModalService: { open: Mock };

    function question(overrides: Partial<MetisQuestionDTO> = {}): MetisQuestionDTO {
        return {
            id: 1,
            checkId: 1,
            questionOrder: 1,
            focusFilePath: 'BubbleSort.java',
            focusFileChangedLines: 12,
            questionText: 'Why can the inner loop stop early?',
            timeLimitSeconds: 240,
            regenerationCount: 0,
            ...overrides,
        };
    }

    function studentCheck(overrides: Partial<MetisStudentCheckDTO> = {}): MetisStudentCheckDTO {
        return {
            status: MetisCheckStatus.READY,
            windowStart,
            windowEnd,
            tutorialGroupTitle: 'Gruppe 2',
            mainQuestionCount: 3,
            likertScalePoints: 5,
            answeredQuestions: [],
            ratingsVisible: false,
            ...overrides,
        };
    }

    beforeEach(async () => {
        metisService = { studentCheck: vi.fn(), startCheck: vi.fn(), submitAnswer: vi.fn() };
        userService = { updateLLMSelectionDecision: vi.fn() };
        llmModalService = { open: vi.fn() };
        await TestBed.configureTestingModule({
            imports: [ComprehensionCheckPanelComponent],
            providers: [
                { provide: MetisService, useValue: metisService },
                { provide: UserService, useValue: userService },
                { provide: LLMSelectionModalService, useValue: llmModalService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ArtemisServerDateService, useClass: MockArtemisServerDateService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(ComprehensionCheckPanelComponent, {
                remove: {
                    imports: [TranslateDirective, ArtemisTranslatePipe, ArtemisDatePipe, ArtemisTimeAgoPipe, MarkdownDirective, QuestionTimerComponent, RatingSquareComponent],
                },
                add: {
                    imports: [
                        MockDirective(TranslateDirective),
                        // Returns the key, so the rendered texts show which key a state uses.
                        MockPipe(ArtemisTranslatePipe, (key) => key ?? ''),
                        MockPipe(ArtemisDatePipe),
                        MockPipe(ArtemisTimeAgoPipe),
                        MockDirective(MarkdownDirective),
                        MockComponent(QuestionTimerComponent),
                        MockComponent(RatingSquareComponent),
                    ],
                },
            })
            .compileComponents();
        accountService = TestBed.inject(AccountService) as unknown as MockAccountService;
        // Most students have agreed to AI processing; the consent cases change the decision.
        accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User);
        serverDateService = TestBed.inject(ArtemisServerDateService);
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    /** Renders the panel for the check the service answers with, at the given server time. */
    function createPanel(check: MetisStudentCheckDTO, now = duringWindow): void {
        metisService.studentCheck.mockReturnValue(of(check));
        vi.spyOn(serverDateService, 'now').mockReturnValue(now);
        render();
    }

    function render(): void {
        fixture = TestBed.createComponent(ComprehensionCheckPanelComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exerciseId', exerciseId);
        fixture.detectChanges();
    }

    function element(testId: string): HTMLElement | null {
        return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
    }

    function elements(testId: string): HTMLElement[] {
        return Array.from(fixture.nativeElement.querySelectorAll(`[data-testid="${testId}"]`));
    }

    function button(testId: string): HTMLButtonElement {
        return element(testId) as HTMLButtonElement;
    }

    function answerField(): HTMLTextAreaElement {
        return element('metis-answer-field') as HTMLTextAreaElement;
    }

    function typeAnswer(text: string): void {
        const field = answerField();
        field.value = text;
        field.dispatchEvent(new Event('input'));
        fixture.detectChanges();
    }

    function statusTagText(): string {
        return element('metis-status-tag')?.textContent ?? '';
    }

    /** The element inside the test id that the mocked translate directive renders the key on. */
    function translated(testId: string, key: string): Element | null {
        return element(testId)?.querySelector(`[jhiTranslate="${key}"]`) ?? null;
    }

    it('should load the check of the exercise once', () => {
        createPanel(studentCheck());

        expect(metisService.studentCheck).toHaveBeenCalledOnce();
        expect(metisService.studentCheck).toHaveBeenCalledWith(exerciseId);
    });

    it('should render the header without a status while the check is still loading', () => {
        metisService.studentCheck.mockReturnValue(NEVER);
        vi.spyOn(serverDateService, 'now').mockReturnValue(duringWindow);
        render();

        expect(component['view']()).toBe('loading');
        expect(element('metis-panel-header')).not.toBeNull();
        expect(element('metis-status-tag')).toBeNull();
        expect(element('metis-panel-body')).toBeNull();
    });

    it('should say that there is no check when it cannot be loaded', () => {
        metisService.studentCheck.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        vi.spyOn(serverDateService, 'now').mockReturnValue(duringWindow);
        render();

        expect(component['view']()).toBe('unavailable');
        expect(element('metis-panel-body')).not.toBeNull();
        expect(element('metis-state-unavailable')).not.toBeNull();
        expect(element('metis-failure-reason')).toBeNull();
        expect(element('metis-status-tag')).toBeNull();
    });

    it('should show only the opt-out notice for a student who declined AI features', () => {
        accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.NO_AI } as User);

        createPanel(studentCheck({ status: MetisCheckStatus.SKIPPED, failureReason: 'NO_AI_CONSENT' }));

        expect(element('metis-ai-declined')).not.toBeNull();
        expect(element('metis-panel-header')).toBeNull();
        expect(element('metis-panel-body')).toBeNull();
        expect(element('metis-state-consent')).toBeNull();
        expect(fixture.nativeElement.children).toHaveLength(1);
    });

    it('should announce the window before it opens with a disabled start button', () => {
        createPanel(studentCheck(), windowStart.subtract(2, 'day'));

        expect(component['view']()).toBe('before');
        expect(element('metis-state-before')).not.toBeNull();
        expect(translated('metis-state-before', 'artemisApp.metis.student.beforeTextGroup')).not.toBeNull();
        expect(statusTagText()).toContain('artemisApp.metis.student.opensAt');
        expect(button('metis-start-button').disabled).toBe(true);
        expect(metisService.startCheck).not.toHaveBeenCalled();
    });

    it('should use the group-free copy before the window when the student is in no tutorial group', () => {
        createPanel(studentCheck({ tutorialGroupTitle: undefined }), windowStart.subtract(1, 'day'));

        expect(translated('metis-state-before', 'artemisApp.metis.student.beforeText')).not.toBeNull();
        expect(translated('metis-state-before', 'artemisApp.metis.student.beforeTextGroup')).toBeNull();
    });

    it('should start the check from the ready state and show the first question with its timer', () => {
        const firstQuestion = question({ shownAt: duringWindow });
        metisService.startCheck.mockReturnValue(of(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, currentQuestion: firstQuestion })));
        createPanel(studentCheck());
        expect(element('metis-state-ready')).not.toBeNull();
        expect(statusTagText()).toContain('artemisApp.metis.student.openUntil');
        expect(button('metis-start-button').disabled).toBe(false);

        button('metis-start-button').click();
        fixture.detectChanges();

        expect(metisService.startCheck).toHaveBeenCalledOnce();
        expect(metisService.startCheck).toHaveBeenCalledWith(exerciseId);
        expect(component['view']()).toBe('answering');
        expect(element('metis-state-ready')).toBeNull();
        expect(elements('metis-question-bubble')).toHaveLength(1);
        expect(element('metis-focus-chip')).not.toBeNull();
        expect(fixture.debugElement.query(By.directive(QuestionTimerComponent))).not.toBeNull();
        expect(component['currentDeadline']()?.isSame(duringWindow.add(240, 'second'))).toBe(true);
        expect(answerField()).not.toBeNull();
        expect(answerField().value).toBe('');
    });

    it('should show the file name of the focus file in the chip of the question and keep the full path as its tooltip', () => {
        const repositoryPath = 'src/de/tum/in/www1/example/BubbleSort.java';
        createPanel(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, currentQuestion: question({ focusFilePath: repositoryPath, shownAt: duringWindow }) }));

        const chip = element('metis-focus-chip');
        expect(chip?.querySelector('[data-testid="metis-focus-chip-file"]')?.textContent).toBe('BubbleSort.java');
        expect(chip?.getAttribute('title')).toBe(repositoryPath);
        expect(chip?.querySelector('[data-testid="metis-focus-chip-path"]')?.textContent).toBe(repositoryPath);
    });

    it('should load the check again when the server refuses the start', () => {
        metisService.startCheck.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { errorKey: 'metis.windowClosed' } })));
        createPanel(studentCheck());
        metisService.studentCheck.mockReturnValue(of(studentCheck({ windowEnd: duringWindow })));

        button('metis-start-button').click();
        fixture.detectChanges();

        expect(metisService.startCheck).toHaveBeenCalledOnce();
        expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
        expect(component['view']()).toBe('closed');
        expect(element('metis-state-closed')).not.toBeNull();
    });

    it('should time a question without a shown-at date from the moment the check was loaded', () => {
        createPanel(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, currentQuestion: question({ shownAt: undefined, timeLimitSeconds: 150 }) }));

        expect(component['view']()).toBe('answering');
        expect(component['currentDeadline']()?.isSame(duringWindow.add(150, 'second'))).toBe(true);
    });

    it('should cut the deadline of the current question to the window end', () => {
        createPanel(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, currentQuestion: question({ shownAt: windowEnd.subtract(1, 'minute'), timeLimitSeconds: 240 }) }));

        expect(component['currentDeadline']()?.isSame(windowEnd)).toBe(true);
        expect(fixture.debugElement.query(By.directive(QuestionTimerComponent)).componentInstance.endDate().isSame(windowEnd)).toBe(true);
    });

    it('should keep the deadline of the current question without a window end', () => {
        createPanel(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, windowEnd: undefined, currentQuestion: question({ shownAt: duringWindow }) }));

        expect(component['currentDeadline']()?.isSame(duringWindow.add(240, 'second'))).toBe(true);
    });

    describe('while answering', () => {
        const firstQuestion = question({ id: 1, shownAt: duringWindow });
        const followUp = question({
            id: 2,
            parentQuestionId: 1,
            questionOrder: 2,
            questionText: 'Which line of your code guarantees that?',
            timeLimitSeconds: 90,
            shownAt: duringWindow.add(1, 'minute'),
        });

        function answered(answerText: string, llmScore?: number): MetisQuestionDTO {
            return { ...firstQuestion, answerText, answeredAt: duringWindow.add(1, 'minute'), llmScore };
        }

        beforeEach(() => {
            createPanel(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, currentQuestion: firstQuestion }));
        });

        it('should submit the draft with the button and continue with the follow-up', () => {
            const next = studentCheck({
                status: MetisCheckStatus.IN_PROGRESS,
                answeredQuestions: [answered('The last i positions are final.', 3)],
                currentQuestion: followUp,
            });
            metisService.submitAnswer.mockReturnValue(of(next));
            expect(button('metis-submit-button').disabled).toBe(true);

            typeAnswer('The last i positions are final.');
            expect(component['draft']()).toBe('The last i positions are final.');
            expect(button('metis-submit-button').disabled).toBe(false);

            button('metis-submit-button').click();
            fixture.detectChanges();

            expect(metisService.submitAnswer).toHaveBeenCalledOnce();
            expect(metisService.submitAnswer).toHaveBeenCalledWith(exerciseId, 1, 'The last i positions are final.');
            expect(component['draft']()).toBe('');
            expect(elements('metis-question-bubble')).toHaveLength(2);
            expect(elements('metis-answer-bubble')).toHaveLength(1);
            expect(element('metis-answer-received')).not.toBeNull();
            expect(element('metis-answer-timeup')).toBeNull();
            // The follow-up is labelled above the timer and in the thread.
            expect(fixture.nativeElement.querySelectorAll('[jhiTranslate="artemisApp.metis.student.followUp"]')).toHaveLength(2);
            expect(component['currentDeadline']()?.isSame(followUp.shownAt!.add(90, 'second'))).toBe(true);
        });

        it('should submit on Enter and leave Shift+Enter to the browser for a line break', () => {
            const next = studentCheck({
                status: MetisCheckStatus.IN_PROGRESS,
                answeredQuestions: [answered('Because the largest value bubbles to the end.')],
                currentQuestion: followUp,
            });
            metisService.submitAnswer.mockReturnValue(of(next));
            typeAnswer('Because the largest value bubbles to the end.');

            const shiftEnter = new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true, cancelable: true });
            answerField().dispatchEvent(shiftEnter);
            fixture.detectChanges();
            expect(shiftEnter.defaultPrevented).toBe(false);
            expect(metisService.submitAnswer).not.toHaveBeenCalled();

            const enter = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true });
            answerField().dispatchEvent(enter);
            fixture.detectChanges();
            expect(enter.defaultPrevented).toBe(true);
            expect(metisService.submitAnswer).toHaveBeenCalledOnce();
            expect(metisService.submitAnswer).toHaveBeenCalledWith(exerciseId, 1, 'Because the largest value bubbles to the end.');
        });

        it('should not submit an empty draft by hand', () => {
            typeAnswer('   ');
            answerField().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', cancelable: true }));
            button('metis-submit-button').click();
            fixture.detectChanges();

            expect(metisService.submitAnswer).not.toHaveBeenCalled();
            expect(button('metis-submit-button').disabled).toBe(true);
        });

        it('should submit the draft as it is when the timer runs out and say so', () => {
            const next = studentCheck({
                status: MetisCheckStatus.IN_PROGRESS,
                answeredQuestions: [answered('Half an')],
                currentQuestion: followUp,
            });
            metisService.submitAnswer.mockReturnValue(of(next));
            typeAnswer('Half an');

            fixture.debugElement.query(By.directive(QuestionTimerComponent)).componentInstance.expired.emit();
            fixture.detectChanges();

            expect(metisService.submitAnswer).toHaveBeenCalledWith(exerciseId, 1, 'Half an');
            expect(element('metis-answer-timeup')).not.toBeNull();
            expect(element('metis-answer-received')).toBeNull();
            expect(elements('metis-question-bubble')).toHaveLength(2);
        });

        it('should submit an empty answer when the timer runs out before anything was typed', () => {
            metisService.submitAnswer.mockReturnValue(of(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, answeredQuestions: [answered('')], currentQuestion: followUp })));

            fixture.debugElement.query(By.directive(QuestionTimerComponent)).componentInstance.expired.emit();
            fixture.detectChanges();

            expect(metisService.submitAnswer).toHaveBeenCalledWith(exerciseId, 1, '');
            expect(element('metis-answer-bubble')?.getAttribute('jhiTranslate')).toBe('artemisApp.metis.student.noAnswer');
        });

        it('should finish with the thank-you state after the last answer', () => {
            metisService.submitAnswer.mockReturnValue(of(studentCheck({ status: MetisCheckStatus.COMPLETED, answeredQuestions: [answered('Done', 5)] })));
            typeAnswer('Done');

            button('metis-submit-button').click();
            fixture.detectChanges();

            expect(component['view']()).toBe('done');
            expect(element('metis-state-done')).not.toBeNull();
            expect(element('metis-state-waiting')).toBeNull();
            expect(translated('metis-state-done', 'artemisApp.metis.student.doneText')).not.toBeNull();
            expect(element('metis-answer-box')).toBeNull();
            expect(element('metis-current-question')).toBeNull();
            expect(elements('metis-answer-bubble')).toHaveLength(1);
            expect(statusTagText()).toContain('artemisApp.metis.student.completed');

            // Nothing is left to answer, so a late timer expiry must not send another answer.
            component['onTimerExpired']();
            expect(metisService.submitAnswer).toHaveBeenCalledOnce();
        });

        it('should say that the answer is saved while the check waits for its rating', () => {
            metisService.submitAnswer.mockReturnValue(of(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, answeredQuestions: [answered('Saved', 4)] })));
            typeAnswer('Saved');

            button('metis-submit-button').click();
            fixture.detectChanges();

            expect(component['view']()).toBe('done');
            expect(element('metis-state-waiting')).not.toBeNull();
            expect(translated('metis-state-waiting', 'artemisApp.metis.student.waitingTitle')).not.toBeNull();
            expect(translated('metis-state-waiting', 'artemisApp.metis.student.waitingText')).not.toBeNull();
            expect(element('metis-state-done')).toBeNull();
            expect(element('metis-answer-box')).toBeNull();
            expect(statusTagText()).toContain('artemisApp.metis.student.openUntil');
        });

        it('should release the submit lock and keep the draft when the request fails', () => {
            metisService.submitAnswer.mockReturnValue(throwError(() => new Error('offline')));
            typeAnswer('Draft');

            button('metis-submit-button').click();
            fixture.detectChanges();

            expect(component['submitting']()).toBe(false);
            expect(component['draft']()).toBe('Draft');
            expect(answerField().disabled).toBe(false);
        });

        it('should lock the answer box while the answer is on its way and send it once', () => {
            const submission = new Subject<MetisStudentCheckDTO>();
            metisService.submitAnswer.mockReturnValue(submission);
            typeAnswer('On its way');

            button('metis-submit-button').click();
            fixture.detectChanges();

            expect(metisService.submitAnswer).toHaveBeenCalledWith(exerciseId, 1, 'On its way');
            expect(component['submitting']()).toBe(true);
            expect(answerField().disabled).toBe(true);
            expect(button('metis-submit-button').disabled).toBe(true);

            // Enter while the answer is still on its way must not send it a second time.
            answerField().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', cancelable: true }));
            fixture.detectChanges();
            expect(metisService.submitAnswer).toHaveBeenCalledOnce();

            submission.next(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, answeredQuestions: [answered('On its way', 3)], currentQuestion: followUp }));
            fixture.detectChanges();

            expect(component['submitting']()).toBe(false);
            expect(component['draft']()).toBe('');
            expect(answerField().value).toBe('');
            expect(answerField().disabled).toBe(false);
            expect(elements('metis-answer-bubble')).toHaveLength(1);
        });
    });

    describe('after the window', () => {
        const answeredQuestions: MetisQuestionDTO[] = [
            { ...question({ id: 1 }), answerText: 'A', answeredAt: duringWindow, llmScore: 2, llmRationale: 'Repeats what the code does without saying why.' },
            { ...question({ id: 2, parentQuestionId: 1, questionOrder: 2 }), answerText: 'B', answeredAt: duringWindow, llmScore: 3, llmRationale: 'Partly correct.' },
            { ...question({ id: 3, questionOrder: 3, focusFilePath: 'SortUtils.java' }), answerText: 'C', answeredAt: duringWindow, llmScore: 5, llmRationale: 'Complete.' },
        ];

        it('should show one result card per question once the window closed and the ratings are visible', () => {
            createPanel(studentCheck({ status: MetisCheckStatus.COMPLETED, ratingsVisible: true, answeredQuestions, likertScalePoints: 7 }), windowEnd.add(1, 'hour'));

            expect(component['view']()).toBe('results');
            expect(elements('metis-result-card')).toHaveLength(3);
            const ratingSquares = fixture.debugElement.queryAll(By.directive(RatingSquareComponent));
            expect(ratingSquares).toHaveLength(3);
            // The squares and the "Rating n of m." lines read the rating against the scale of the check, not a fixed five.
            expect(component['points']()).toBe(7);
            expect(ratingSquares.map((square) => square.componentInstance.points())).toEqual([7, 7, 7]);
            expect(ratingSquares.map((square) => square.componentInstance.score())).toEqual([2, 3, 5]);
            expect(fixture.nativeElement.querySelectorAll('[jhiTranslate="artemisApp.metis.student.rating"]')).toHaveLength(3);
            expect(fixture.nativeElement.querySelectorAll('[jhiTranslate="artemisApp.metis.student.followUp"]')).toHaveLength(1);
            expect(statusTagText()).toContain('artemisApp.metis.student.closedRated');
            expect(element('metis-thread')).toBeNull();
        });

        it('should show the file names of the focus files on the result cards and keep the full paths as their tooltips', () => {
            const repositoryPath = 'src/de/tum/in/www1/example/SortUtils.java';
            const questions = answeredQuestions.map((answered) => (answered.id === 3 ? { ...answered, focusFilePath: repositoryPath } : answered));
            createPanel(studentCheck({ status: MetisCheckStatus.COMPLETED, ratingsVisible: true, answeredQuestions: questions }), windowEnd.add(1, 'hour'));

            const chips = elements('metis-focus-chip');
            const fileNames = chips.map((chip) => chip.querySelector('[data-testid="metis-focus-chip-file"]')?.textContent);
            expect(fileNames).toEqual(['BubbleSort.java', 'BubbleSort.java', 'SortUtils.java']);
            expect(chips[2].getAttribute('title')).toBe(repositoryPath);
        });

        it('should keep the ratings hidden until the window closes', () => {
            createPanel(studentCheck({ status: MetisCheckStatus.COMPLETED, ratingsVisible: false, answeredQuestions: [answeredQuestions[0]] }));

            expect(component['view']()).toBe('done');
            expect(element('metis-result-card')).toBeNull();
            expect(elements('metis-answer-bubble')).toHaveLength(1);
        });

        it('should tell the student without a window end that the instructor can see the answers', () => {
            createPanel(
                studentCheck({ status: MetisCheckStatus.COMPLETED, windowEnd: undefined, answeredQuestions: [{ ...question(), answerText: 'A', answeredAt: duringWindow }] }),
            );

            expect(translated('metis-state-done', 'artemisApp.metis.student.doneTextNoDate')).not.toBeNull();
        });

        it('should report a missed window as closed with the answered count', () => {
            createPanel(studentCheck(), windowEnd.add(1, 'minute'));

            expect(component['view']()).toBe('closed');
            expect(translated('metis-state-closed', 'artemisApp.metis.student.closedTextGroup')).not.toBeNull();
            expect(component['answeredMainCount']()).toBe(0);
            expect(statusTagText()).toContain('artemisApp.metis.student.closed');
            expect(element('metis-start-button')).toBeNull();
            expect(element('metis-closed-results')).toBeNull();
        });

        it.each([MetisCheckStatus.PENDING, MetisCheckStatus.GENERATING, MetisCheckStatus.IN_REVIEW, MetisCheckStatus.READY])(
            'should close a check that was never started once the window ended while it was %s',
            (status) => {
                createPanel(studentCheck({ status }), windowEnd);

                expect(component['view']()).toBe('closed');
                expect(element('metis-state-closed')).not.toBeNull();
                expect(element('metis-state-preparing')).toBeNull();
                expect(element('metis-state-review')).toBeNull();
            },
        );

        it('should close a check that was still in progress when the window ended', () => {
            createPanel(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, tutorialGroupTitle: undefined, currentQuestion: question() }), windowEnd.add(1, 'minute'));

            expect(component['view']()).toBe('closed');
            expect(translated('metis-state-closed', 'artemisApp.metis.student.closedText')).not.toBeNull();
            expect(element('metis-answer-box')).toBeNull();
            expect(element('metis-closed-results')).toBeNull();
        });

        it('should show the ratings of the answered questions under a closed check', () => {
            createPanel(
                studentCheck({
                    status: MetisCheckStatus.IN_PROGRESS,
                    ratingsVisible: true,
                    answeredQuestions: answeredQuestions.slice(0, 2),
                    currentQuestion: question({ id: 4, questionOrder: 4 }),
                }),
                windowEnd.add(1, 'minute'),
            );

            expect(component['view']()).toBe('closed');
            expect(element('metis-state-closed')).not.toBeNull();
            expect(element('metis-closed-results')).not.toBeNull();
            // The question that was still open has no answer and therefore no card.
            expect(elements('metis-result-card')).toHaveLength(2);
            expect(component['answeredMainCount']()).toBe(1);
            expect(fixture.debugElement.queryAll(By.directive(RatingSquareComponent)).map((square) => square.componentInstance.score())).toEqual([2, 3]);
        });
    });

    it.each<[MetisCheckStatus, string]>([
        [MetisCheckStatus.PENDING, 'metis-state-preparing'],
        [MetisCheckStatus.GENERATING, 'metis-state-preparing'],
        [MetisCheckStatus.IN_REVIEW, 'metis-state-review'],
    ])('should show the waiting copy while the check is %s', (status, testId) => {
        createPanel(studentCheck({ status }), windowStart.subtract(1, 'hour'));

        expect(element(testId)).not.toBeNull();
        expect(statusTagText()).toContain('artemisApp.metis.student.preparing');
        expect(element('metis-start-button')).toBeNull();
    });

    it('should tell the student to come back later while the review has no window yet', () => {
        createPanel(studentCheck({ status: MetisCheckStatus.IN_REVIEW, windowStart: undefined }), windowStart.subtract(1, 'hour'));

        expect(translated('metis-state-review', 'artemisApp.metis.student.reviewTextNoDate')).not.toBeNull();
    });

    it.each([MetisCheckStatus.FAILED, MetisCheckStatus.SKIPPED])('should explain that no check exists when it is %s', (status) => {
        createPanel(studentCheck({ status }));

        expect(component['view']()).toBe('unavailable');
        expect(element('metis-state-unavailable')).not.toBeNull();
        expect(element('metis-failure-reason')).toBeNull();
        expect(statusTagText()).toContain('artemisApp.metis.student.closed');
    });

    it.each(['NO_SUBMISSION', 'WINDOW_CLOSED', 'NOT_ELIGIBLE', 'NOT_ENABLED', 'INVALID_LLM_RESPONSE'])('should name the reason %s under the unavailable text', (failureReason) => {
        const status = failureReason === 'INVALID_LLM_RESPONSE' ? MetisCheckStatus.FAILED : MetisCheckStatus.SKIPPED;
        createPanel(studentCheck({ status, failureReason }));

        expect(component['view']()).toBe('unavailable');
        expect(element('metis-failure-reason')?.textContent).toContain(`artemisApp.metis.failureReason.${failureReason}`);
    });

    describe('consent', () => {
        it('should ask a student without an AI decision to choose one', () => {
            accountService.userIdentity.set({ login: 'student1' } as User);

            createPanel(studentCheck({ status: MetisCheckStatus.PENDING }), windowStart.subtract(1, 'hour'));

            expect(component['view']()).toBe('consent');
            expect(element('metis-state-consent')).not.toBeNull();
            expect(translated('metis-state-consent', 'artemisApp.metis.student.consentTitle')).not.toBeNull();
            expect(translated('metis-state-consent', 'artemisApp.metis.student.consentText')).not.toBeNull();
            expect(translated('metis-state-consent', 'artemisApp.metis.student.cloudConsentText')).toBeNull();
            expect(statusTagText()).toContain('artemisApp.metis.student.consentTag');
            expect(element('metis-state-preparing')).toBeNull();
            expect(element('metis-consent-button')).not.toBeNull();
        });

        it('should ask again for a check that was skipped without consent', () => {
            createPanel(studentCheck({ status: MetisCheckStatus.SKIPPED, failureReason: 'NO_AI_CONSENT' }));

            expect(component['view']()).toBe('consent');
            expect(translated('metis-state-consent', 'artemisApp.metis.student.consentText')).not.toBeNull();
            expect(element('metis-state-unavailable')).toBeNull();
        });

        it('should explain that the course needs cloud AI when the student chose on-premise AI', () => {
            accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.LOCAL_AI } as User);

            createPanel(studentCheck({ status: MetisCheckStatus.SKIPPED, failureReason: 'CLOUD_AI_REQUIRED' }));

            expect(component['view']()).toBe('consent');
            expect(translated('metis-state-consent', 'artemisApp.metis.student.cloudConsentText')).not.toBeNull();
            expect(translated('metis-state-consent', 'artemisApp.metis.student.consentText')).toBeNull();
        });

        it.each<[string, MetisStudentCheckDTO, string]>([
            ['PENDING', studentCheck({ status: MetisCheckStatus.PENDING }), 'closed'],
            ['skipped without consent', studentCheck({ status: MetisCheckStatus.SKIPPED, failureReason: 'NO_AI_CONSENT' }), 'unavailable'],
        ])('should not ask a student without a decision once the window has ended (%s)', (_, check, view) => {
            accountService.userIdentity.set({ login: 'student1' } as User);

            createPanel(check, windowEnd.add(1, 'minute'));

            expect(component['view']()).toBe(view);
            expect(element('metis-state-consent')).toBeNull();
        });

        it.each<[MetisCheckStatus, string]>([
            [MetisCheckStatus.SKIPPED, 'NO_SUBMISSION'],
            [MetisCheckStatus.SKIPPED, 'NOT_ELIGIBLE'],
            [MetisCheckStatus.FAILED, 'LLM_UNAVAILABLE'],
        ])('should not ask a student without a decision whose check is %s for %s, which no decision changes', (status, failureReason) => {
            accountService.userIdentity.set({ login: 'student1' } as User);

            createPanel(studentCheck({ status, failureReason }));

            expect(component['view']()).toBe('unavailable');
            expect(element('metis-failure-reason')?.textContent).toContain(`artemisApp.metis.failureReason.${failureReason}`);
        });

        it.each([MetisCheckStatus.PENDING, MetisCheckStatus.GENERATING])('should ask a student without a decision while the check is %s', (status) => {
            accountService.userIdentity.set({ login: 'student1' } as User);

            createPanel(studentCheck({ status }));

            expect(component['view']()).toBe('consent');
        });

        it('should show a check that was generated under a decision as it is', () => {
            // This page knows of no decision, but the questions exist, so the student decided in another tab.
            accountService.userIdentity.set({ login: 'student1' } as User);

            createPanel(studentCheck());

            expect(component['view']()).toBe('ready');
            expect(element('metis-start-button')).not.toBeNull();
        });

        it('should ask for the cloud option when the server refuses the start under an on-premise decision', () => {
            accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.LOCAL_AI } as User);
            metisService.startCheck.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
            createPanel(studentCheck());

            button('metis-start-button').click();
            fixture.detectChanges();

            expect(metisService.startCheck).toHaveBeenCalledOnce();
            expect(component['view']()).toBe('consent');
            expect(translated('metis-state-consent', 'artemisApp.metis.student.cloudConsentText')).not.toBeNull();
            expect(element('metis-consent-button')).not.toBeNull();
            expect(element('metis-state-ready')).toBeNull();
            // The refusal says what is missing, so the check is not loaded again only to offer the same start.
            expect(metisService.studentCheck).toHaveBeenCalledOnce();
        });

        it('should ask for a decision when the server refuses an answer for it and keep the draft', () => {
            metisService.submitAnswer.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
            createPanel(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, currentQuestion: question({ shownAt: duringWindow }) }));
            typeAnswer('My answer');

            button('metis-submit-button').click();
            fixture.detectChanges();

            expect(component['view']()).toBe('consent');
            expect(translated('metis-state-consent', 'artemisApp.metis.student.consentText')).not.toBeNull();
            expect(translated('metis-state-consent', 'artemisApp.metis.student.cloudConsentText')).toBeNull();
            expect(element('metis-answer-box')).toBeNull();
            expect(component['submitting']()).toBe(false);
            expect(component['draft']()).toBe('My answer');
        });

        it('should offer the start again once the dialog stored a decision after a refused start', async () => {
            accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.LOCAL_AI } as User);
            metisService.startCheck.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
            llmModalService.open.mockResolvedValue(LLMSelectionDecision.CLOUD_AI);
            userService.updateLLMSelectionDecision.mockReturnValue(of(new HttpResponse<void>({ status: 200 })));
            createPanel(studentCheck());
            button('metis-start-button').click();
            fixture.detectChanges();
            expect(component['view']()).toBe('consent');

            button('metis-consent-button').click();
            await vi.waitFor(() => expect(metisService.studentCheck).toHaveBeenCalledTimes(2));
            fixture.detectChanges();

            expect(llmModalService.open).toHaveBeenCalledWith(LLMSelectionDecision.LOCAL_AI);
            expect(userService.updateLLMSelectionDecision).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
            expect(component['view']()).toBe('ready');
            expect(element('metis-start-button')).not.toBeNull();
        });

        it('should store the choice of the dialog and load the check again', async () => {
            accountService.userIdentity.set({ login: 'student1' } as User);
            const setDecisionSpy = vi.spyOn(accountService, 'setUserLLMSelectionDecision');
            llmModalService.open.mockResolvedValue(LLMSelectionDecision.CLOUD_AI);
            userService.updateLLMSelectionDecision.mockReturnValue(of(new HttpResponse<void>({ status: 200 })));
            createPanel(studentCheck({ status: MetisCheckStatus.SKIPPED, failureReason: 'NO_AI_CONSENT' }));
            metisService.studentCheck.mockReturnValue(of(studentCheck({ status: MetisCheckStatus.PENDING })));

            button('metis-consent-button').click();
            await vi.waitFor(() => expect(metisService.studentCheck).toHaveBeenCalledTimes(2));
            fixture.detectChanges();

            expect(llmModalService.open).toHaveBeenCalledOnce();
            expect(llmModalService.open).toHaveBeenCalledWith(undefined);
            expect(userService.updateLLMSelectionDecision).toHaveBeenCalledOnce();
            expect(userService.updateLLMSelectionDecision).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
            expect(setDecisionSpy).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
            expect(accountService.userIdentity()?.selectedLLMUsage).toBe(LLMSelectionDecision.CLOUD_AI);
            expect(component['view']()).toBe('preparing');
            expect(element('metis-state-consent')).toBeNull();
        });

        it('should hand the dialog the current decision of a student who chose on-premise AI', async () => {
            accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.LOCAL_AI } as User);
            llmModalService.open.mockResolvedValue(LLMSelectionDecision.CLOUD_AI);
            userService.updateLLMSelectionDecision.mockReturnValue(of(new HttpResponse<void>({ status: 200 })));
            createPanel(studentCheck({ status: MetisCheckStatus.SKIPPED, failureReason: 'CLOUD_AI_REQUIRED' }));

            button('metis-consent-button').click();
            await vi.waitFor(() => expect(userService.updateLLMSelectionDecision).toHaveBeenCalledOnce());

            expect(llmModalService.open).toHaveBeenCalledWith(LLMSelectionDecision.LOCAL_AI);
            expect(userService.updateLLMSelectionDecision).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
        });

        it('should store nothing when the dialog is dismissed', async () => {
            accountService.userIdentity.set({ login: 'student1' } as User);
            llmModalService.open.mockResolvedValue(LLM_MODAL_DISMISSED);
            createPanel(studentCheck({ status: MetisCheckStatus.SKIPPED, failureReason: 'NO_AI_CONSENT' }));

            await component['openConsentDialog']();

            expect(llmModalService.open).toHaveBeenCalledOnce();
            expect(userService.updateLLMSelectionDecision).not.toHaveBeenCalled();
            expect(metisService.studentCheck).toHaveBeenCalledOnce();
            expect(component['view']()).toBe('consent');
        });

        it('should store the choice but not load again once the panel is gone', async () => {
            accountService.userIdentity.set({ login: 'student1' } as User);
            let resolveChoice: (choice: LLMSelectionDecision) => void = () => undefined;
            llmModalService.open.mockReturnValue(new Promise<LLMSelectionDecision>((resolve) => (resolveChoice = resolve)));
            userService.updateLLMSelectionDecision.mockReturnValue(of(new HttpResponse<void>({ status: 200 })));
            createPanel(studentCheck({ status: MetisCheckStatus.SKIPPED, failureReason: 'NO_AI_CONSENT' }));

            const dialog = component['openConsentDialog']();
            fixture.destroy();
            resolveChoice(LLMSelectionDecision.NO_AI);
            await dialog;

            expect(userService.updateLLMSelectionDecision).toHaveBeenCalledWith(LLMSelectionDecision.NO_AI);
            expect(metisService.studentCheck).toHaveBeenCalledOnce();
        });
    });

    describe('refresh timer', () => {
        let clock: dayjs.Dayjs;

        beforeEach(() => {
            vi.useFakeTimers();
        });

        /** Renders the panel at a server time that moves with the fake timers. */
        function createPanelAt(check: MetisStudentCheckDTO, now: dayjs.Dayjs): void {
            clock = now;
            metisService.studentCheck.mockReturnValue(of(check));
            vi.spyOn(serverDateService, 'now').mockImplementation(() => clock);
            render();
        }

        function advance(milliseconds: number): void {
            clock = clock.add(milliseconds, 'millisecond');
            vi.advanceTimersByTime(milliseconds);
        }

        it('should reload once at the window start while the questions are prepared and every 30 seconds after it', () => {
            createPanelAt(studentCheck({ status: MetisCheckStatus.PENDING }), windowStart.subtract(15, 'minute'));
            expect(component['view']()).toBe('preparing');

            advance(15 * 60 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledOnce();
            advance(1000);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);

            advance(29 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            advance(1000);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(3);
            advance(30 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(4);
        });

        it('should reload hourly while the window start lies further ahead or is unknown', () => {
            createPanelAt(studentCheck({ status: MetisCheckStatus.IN_REVIEW, windowStart: undefined }), windowStart.subtract(3, 'day'));
            expect(component['view']()).toBe('review');

            advance(60 * 60 * 1000 - 1);
            expect(metisService.studentCheck).toHaveBeenCalledOnce();
            advance(1);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
        });

        it('should reload just after the window start before the window opens', () => {
            createPanelAt(studentCheck(), windowStart.subtract(30, 'minute'));
            expect(component['view']()).toBe('before');

            advance(30 * 60 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledOnce();
            advance(1000);

            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            expect(component['view']()).toBe('ready');
        });

        it('should approach a window start days ahead hour by hour', () => {
            createPanelAt(studentCheck(), windowStart.subtract(2, 'day'));
            expect(component['view']()).toBe('before');

            advance(60 * 60 * 1000 - 1);
            expect(metisService.studentCheck).toHaveBeenCalledOnce();
            advance(1);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            advance(60 * 60 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(3);
        });

        it('should reload at the window end while the check is ready and show it closed', () => {
            createPanelAt(studentCheck(), windowEnd.subtract(10, 'minute'));
            expect(component['view']()).toBe('ready');

            advance(10 * 60 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledOnce();
            advance(1000);
            fixture.detectChanges();

            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            expect(component['view']()).toBe('closed');
            expect(element('metis-state-closed')).not.toBeNull();

            // Nothing polls once the window has ended.
            advance(5 * 60 * 60 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
        });

        it('should reload every 10 seconds while a started check waits for a rating', () => {
            createPanelAt(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, answeredQuestions: [{ ...question(), answerText: 'A', answeredAt: duringWindow }] }), duringWindow);
            expect(component['view']()).toBe('done');

            advance(10 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            advance(10 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(3);
        });

        it('should reload at the window end after the student completed the check', () => {
            createPanelAt(studentCheck({ status: MetisCheckStatus.COMPLETED, answeredQuestions: [{ ...question(), answerText: 'A', answeredAt: duringWindow }] }), duringWindow);
            expect(component['view']()).toBe('done');
            metisService.studentCheck.mockReturnValue(of(studentCheck({ status: MetisCheckStatus.COMPLETED, ratingsVisible: true })));

            advance(windowEnd.diff(duringWindow) + 1000);

            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            expect(component['view']()).toBe('results');
        });

        it('should not reload a completed check without a window end', () => {
            createPanelAt(studentCheck({ status: MetisCheckStatus.COMPLETED, windowEnd: undefined, ratingsVisible: false }), duringWindow);
            expect(component['view']()).toBe('done');

            advance(5 * 60 * 60 * 1000);

            expect(metisService.studentCheck).toHaveBeenCalledOnce();
        });

        it('should reschedule after the student answered instead of keeping the old reload', () => {
            createPanelAt(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, currentQuestion: question({ shownAt: duringWindow }) }), duringWindow);
            metisService.submitAnswer.mockReturnValue(of(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, answeredQuestions: [{ ...question(), answerText: 'A' }] })));
            typeAnswer('A');

            button('metis-submit-button').click();
            advance(10 * 1000);

            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
        });

        it('should load the check again after a timed answer failed, as its timer runs out only once', () => {
            const expiredQuestion = question({ id: 1, shownAt: duringWindow, timeLimitSeconds: 60 });
            const timedOut = duringWindow.add(60, 'second');
            // Without a window end, nothing else reloads the check while a question is open.
            createPanelAt(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, windowEnd: undefined, currentQuestion: expiredQuestion }), timedOut);
            metisService.submitAnswer.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));

            fixture.debugElement.query(By.directive(QuestionTimerComponent)).componentInstance.expired.emit();
            fixture.detectChanges();

            expect(metisService.submitAnswer).toHaveBeenCalledOnce();
            expect(metisService.submitAnswer).toHaveBeenCalledWith(exerciseId, 1, '');
            // The empty draft leaves the student nothing to send by hand.
            expect(button('metis-submit-button').disabled).toBe(true);

            // The server had stored the empty answer and shown the next main question before the response was lost.
            const nextQuestion = question({ id: 2, questionOrder: 2, shownAt: timedOut });
            const answeredQuestions = [{ ...expiredQuestion, answerText: '', answeredAt: timedOut }];
            const movedOn = studentCheck({ status: MetisCheckStatus.IN_PROGRESS, windowEnd: undefined, answeredQuestions, currentQuestion: nextQuestion });
            metisService.studentCheck.mockReturnValue(of(movedOn));
            advance(30 * 1000 - 1);
            expect(metisService.studentCheck).toHaveBeenCalledOnce();
            advance(1);
            fixture.detectChanges();

            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            expect(component['currentEntry']()?.question.id).toBe(2);
            expect(elements('metis-question-bubble')).toHaveLength(2);
            expect(element('metis-answer-timeup')).not.toBeNull();
        });

        it('should load the check again when an answer sent by hand fails after the timer ran out while it was on its way', () => {
            createPanelAt(
                studentCheck({ status: MetisCheckStatus.IN_PROGRESS, windowEnd: undefined, currentQuestion: question({ id: 1, shownAt: duringWindow, timeLimitSeconds: 60 }) }),
                duringWindow.add(30, 'second'),
            );
            const answer = new Subject<MetisStudentCheckDTO>();
            metisService.submitAnswer.mockReturnValue(answer);
            typeAnswer('The outer loop shrinks the unsorted part.');
            button('metis-submit-button').click();
            // The timer runs out while the answer is on its way, so it sends nothing of its own
            fixture.debugElement.query(By.directive(QuestionTimerComponent)).componentInstance.expired.emit();
            expect(metisService.submitAnswer).toHaveBeenCalledOnce();

            answer.error(new HttpErrorResponse({ status: 504 }));
            advance(30 * 1000);

            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
        });

        it('should send the draft again once the reload shows the question of a failed timed answer still open, and wait after every failure', () => {
            const openCheck = () =>
                studentCheck({ status: MetisCheckStatus.IN_PROGRESS, windowEnd: undefined, currentQuestion: question({ id: 1, shownAt: duringWindow, timeLimitSeconds: 60 }) });
            createPanelAt(openCheck(), duringWindow.add(60, 'second'));
            // The server did not store the answer, so the reload shows the same question again, in new objects like a real response.
            metisService.studentCheck.mockReturnValue(of(openCheck()));
            metisService.submitAnswer.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
            const timer = () => fixture.debugElement.query(By.directive(QuestionTimerComponent)).componentInstance;
            const firstEndDate = timer().endDate();

            timer().expired.emit();
            fixture.detectChanges();
            // The answer box stays open while the panel waits; what the student types meanwhile is kept for the same question.
            typeAnswer('Typed after the timer ran out');
            advance(30 * 1000);
            fixture.detectChanges();

            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            expect(component['draft']()).toBe('Typed after the timer ran out');
            // The timer gets a new end date at the same moment, on which the real timer runs out again at once.
            expect(timer().endDate()).not.toBe(firstEndDate);
            expect(timer().endDate().isSame(firstEndDate)).toBe(true);

            timer().expired.emit();
            fixture.detectChanges();

            expect(metisService.submitAnswer).toHaveBeenCalledTimes(2);
            expect(metisService.submitAnswer).toHaveBeenNthCalledWith(1, exerciseId, 1, '');
            expect(metisService.submitAnswer).toHaveBeenNthCalledWith(2, exerciseId, 1, 'Typed after the timer ran out');
            // It failed again, so the next reload waits again instead of following at once.
            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            advance(30 * 1000 - 1);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            advance(1);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(3);
        });

        it('should start the question that a reload shows after a failed timed answer with an empty answer box', () => {
            const expiredQuestion = question({ id: 1, shownAt: duringWindow, timeLimitSeconds: 60 });
            const timedOut = duringWindow.add(60, 'second');
            createPanelAt(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, windowEnd: undefined, currentQuestion: expiredQuestion }), timedOut);
            typeAnswer('The last i positions are final.');
            // The gateway gave up while the answer was rated, but the server stored it and asked a follow-up.
            metisService.submitAnswer.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 504 })));
            fixture.debugElement.query(By.directive(QuestionTimerComponent)).componentInstance.expired.emit();
            fixture.detectChanges();
            expect(component['draft']()).toBe('The last i positions are final.');

            const followUp = question({ id: 2, parentQuestionId: 1, questionOrder: 2, timeLimitSeconds: 90, shownAt: timedOut });
            const answeredQuestions = [{ ...expiredQuestion, answerText: 'The last i positions are final.', answeredAt: timedOut }];
            const movedOn = studentCheck({ status: MetisCheckStatus.IN_PROGRESS, windowEnd: undefined, answeredQuestions, currentQuestion: followUp });
            metisService.studentCheck.mockReturnValue(of(movedOn));
            advance(30 * 1000);
            fixture.detectChanges();

            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            expect(component['currentEntry']()?.question.id).toBe(2);
            // The draft answered the first question, which the server closed; kept, it would go out as the answer to the follow-up.
            expect(component['draft']()).toBe('');
            expect(answerField().value).toBe('');

            fixture.debugElement.query(By.directive(QuestionTimerComponent)).componentInstance.expired.emit();

            expect(metisService.submitAnswer).toHaveBeenCalledTimes(2);
            expect(metisService.submitAnswer).toHaveBeenNthCalledWith(1, exerciseId, 1, 'The last i positions are final.');
            expect(metisService.submitAnswer).toHaveBeenNthCalledWith(2, exerciseId, 2, '');
        });

        it.each<[string, MetisStudentCheckDTO, dayjs.Dayjs]>([
            ['closed', studentCheck(), windowEnd.add(1, 'minute')],
            ['unavailable', studentCheck({ status: MetisCheckStatus.SKIPPED, failureReason: 'NO_SUBMISSION' }), duringWindow],
            ['results', studentCheck({ status: MetisCheckStatus.COMPLETED, ratingsVisible: true }), windowEnd.add(1, 'minute')],
        ])('should not reload a check in the view %s', (view, check, now) => {
            createPanelAt(check, now);
            expect(component['view']()).toBe(view);

            advance(5 * 60 * 60 * 1000);

            expect(metisService.studentCheck).toHaveBeenCalledOnce();
        });

        it('should reload the consent request once at the window end, after which no decision leads to questions', () => {
            createPanelAt(studentCheck({ status: MetisCheckStatus.SKIPPED, failureReason: 'NO_AI_CONSENT' }), duringWindow);
            expect(component['view']()).toBe('consent');

            advance(windowEnd.diff(duringWindow));
            expect(metisService.studentCheck).toHaveBeenCalledOnce();
            advance(1000);
            fixture.detectChanges();

            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            expect(component['view']()).toBe('unavailable');
            expect(element('metis-failure-reason')?.textContent).toContain('artemisApp.metis.failureReason.NO_AI_CONSENT');

            advance(5 * 60 * 60 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
        });

        it('should try a failed reload again after a wait that doubles with every further failure', () => {
            createPanelAt(studentCheck(), windowStart.subtract(30, 'minute'));
            metisService.studentCheck
                .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })))
                .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));

            // The reload at the window start fails, for instance while the whole cohort reloads at once; the panel keeps the last check.
            advance(30 * 60 * 1000 + 1000);
            fixture.detectChanges();
            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            expect(component['view']()).toBe('before');
            expect(element('metis-state-before')).not.toBeNull();

            advance(30 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(3);
            advance(60 * 1000 - 1);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(3);
            advance(1);
            fixture.detectChanges();

            expect(metisService.studentCheck).toHaveBeenCalledTimes(4);
            expect(component['view']()).toBe('ready');
            expect(element('metis-state-ready')).not.toBeNull();
        });

        it('should start the wait over once a reload succeeds', () => {
            createPanelAt(studentCheck({ status: MetisCheckStatus.PENDING }), windowStart.add(1, 'minute'));
            metisService.studentCheck.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));

            advance(30 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            advance(30 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(3);
            metisService.studentCheck.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));

            // The questions are prepared, so the next reload comes after 30 seconds, and its failure is tried again after 30 seconds, not 60.
            advance(30 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(4);
            advance(30 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(5);
        });

        it('should not reload for a student who declined AI features', () => {
            accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.NO_AI } as User);
            createPanelAt(studentCheck({ status: MetisCheckStatus.PENDING }), windowStart.subtract(1, 'minute'));

            advance(5 * 60 * 60 * 1000);

            expect(metisService.studentCheck).toHaveBeenCalledOnce();
        });

        it('should not reload after a failed load', () => {
            metisService.studentCheck.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            clock = duringWindow;
            vi.spyOn(serverDateService, 'now').mockImplementation(() => clock);
            render();

            advance(5 * 60 * 60 * 1000);

            expect(metisService.studentCheck).toHaveBeenCalledOnce();
        });

        it('should stop reloading once the panel is destroyed', () => {
            createPanelAt(studentCheck({ status: MetisCheckStatus.PENDING }), windowStart.add(1, 'minute'));

            fixture.destroy();
            advance(60 * 1000);

            expect(metisService.studentCheck).toHaveBeenCalledOnce();
        });
    });
});
