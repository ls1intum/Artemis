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
        const check: MetisStudentCheckDTO = {
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
        // As the server derives it from the window end, for questions that need at least a minute
        return { newQuestionsUntil: check.windowEnd?.subtract(1, 'minute'), ...check };
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

    it('should drop a reload that is still on its way when the start comes back, so the check before the start does not return', () => {
        const firstQuestion = question({ shownAt: duringWindow });
        metisService.startCheck.mockReturnValue(of(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, currentQuestion: firstQuestion })));
        createPanel(studentCheck());
        // A reload, for instance at the window start, read the check before the start and answers only after it
        const reload = new Subject<MetisStudentCheckDTO>();
        metisService.studentCheck.mockReturnValueOnce(reload);
        component['load']();

        button('metis-start-button').click();
        fixture.detectChanges();
        reload.next(studentCheck());
        fixture.detectChanges();

        expect(component['check']()?.status).toBe(MetisCheckStatus.IN_PROGRESS);
        expect(component['view']()).toBe('answering');
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

    it('should send one start for a double click, and the next one once a refused start came back', () => {
        const refused = new Subject<MetisStudentCheckDTO>();
        const started = new Subject<MetisStudentCheckDTO>();
        metisService.startCheck.mockReturnValueOnce(refused).mockReturnValueOnce(started);
        createPanel(studentCheck());
        metisService.studentCheck.mockReturnValue(of(studentCheck()));

        // A second start could come back after an answer given through the first and bring back the check before that answer
        button('metis-start-button').click();
        button('metis-start-button').click();
        fixture.detectChanges();

        expect(metisService.startCheck).toHaveBeenCalledOnce();
        expect(button('metis-start-button').disabled).toBe(true);

        refused.error(new HttpErrorResponse({ status: 409, error: { errorKey: 'metis.windowClosed' } }));
        fixture.detectChanges();
        expect(button('metis-start-button').disabled).toBe(false);

        button('metis-start-button').click();
        started.next(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, currentQuestion: question({ shownAt: duringWindow }) }));
        started.complete();
        fixture.detectChanges();

        expect(metisService.startCheck).toHaveBeenCalledTimes(2);
        expect(component['view']()).toBe('answering');
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

        it('should give a panel created anew while the question is open the draft that the panel before it kept', () => {
            typeAnswer('The inner loop stops once');

            // The split view builds its panels again when it switches its layout, for instance when a tablet is turned
            fixture.destroy();
            render();
            fixture.detectChanges();

            expect(component['draft']()).toBe('The inner loop stops once');
            expect(answerField().value).toBe('The inner loop stops once');
            expect(button('metis-submit-button').disabled).toBe(false);
        });

        it('should give a panel created anew no draft for a question other than the one it was typed for', () => {
            typeAnswer('About the first question');
            fixture.destroy();

            // The server moved on meanwhile, for instance after the answer was sent in another tab
            metisService.studentCheck.mockReturnValue(
                of(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, answeredQuestions: [answered('Sent elsewhere', 3)], currentQuestion: followUp })),
            );
            render();
            fixture.detectChanges();

            expect(component['draft']()).toBe('');
            expect(answerField().value).toBe('');
        });

        it('should give a panel created anew no draft once the answer it was typed for is stored', () => {
            metisService.submitAnswer.mockReturnValue(
                of(studentCheck({ status: MetisCheckStatus.COMPLETED, answeredQuestions: [answered('The last i positions are final.', 3)] })),
            );
            typeAnswer('The last i positions are final.');
            button('metis-submit-button').click();
            fixture.detectChanges();
            fixture.destroy();

            // A reload that still shows the question, as a response read before the answer was stored would
            render();
            fixture.detectChanges();

            expect(component['draft']()).toBe('');
        });

        it('should drop a reload that is still on its way when an answer comes back, so the check before the answer does not return', () => {
            // A reload read the check before the answer was stored and answers only after it
            const reload = new Subject<MetisStudentCheckDTO>();
            metisService.studentCheck.mockReturnValueOnce(reload);
            component['load']();
            metisService.submitAnswer.mockReturnValue(of(studentCheck({ status: MetisCheckStatus.COMPLETED, answeredQuestions: [answered('Done', 5)] })));
            typeAnswer('Done');

            button('metis-submit-button').click();
            fixture.detectChanges();
            reload.next(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, currentQuestion: firstQuestion }));
            fixture.detectChanges();

            expect(component['check']()?.status).toBe(MetisCheckStatus.COMPLETED);
            expect(component['view']()).toBe('done');
            expect(element('metis-answer-box')).toBeNull();
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

        it('should leave an Enter that confirms the text of an input method editor to it and send the draft only with the next Enter', () => {
            metisService.submitAnswer.mockReturnValue(of(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, currentQuestion: followUp })));
            typeAnswer('Weil der größte Wert');

            const composing = new KeyboardEvent('keydown', { key: 'Enter', isComposing: true, cancelable: true });
            answerField().dispatchEvent(composing);
            // Safari confirms a candidate after compositionend with keyCode 229 and isComposing=false
            const confirming = new KeyboardEvent('keydown', { key: 'Enter', keyCode: 229, cancelable: true });
            answerField().dispatchEvent(confirming);
            fixture.detectChanges();
            expect(composing.defaultPrevented).toBe(false);
            expect(confirming.defaultPrevented).toBe(false);
            expect(metisService.submitAnswer).not.toHaveBeenCalled();

            answerField().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', cancelable: true }));
            fixture.detectChanges();
            expect(metisService.submitAnswer).toHaveBeenCalledOnce();
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

        it('should release the submit lock and keep the draft when the server refuses the answer', () => {
            metisService.submitAnswer.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
            typeAnswer('Draft');

            button('metis-submit-button').click();
            fixture.detectChanges();

            // A refusal is not sent again
            expect(metisService.submitAnswer).toHaveBeenCalledOnce();
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

    describe('when the window leaves no time for a question', () => {
        const lastSeconds = windowEnd.subtract(30, 'second');

        it.each([MetisCheckStatus.PENDING, MetisCheckStatus.GENERATING, MetisCheckStatus.IN_REVIEW, MetisCheckStatus.READY])(
            'should close a check that was not started while it is %s, since the server would refuse the start',
            (status) => {
                createPanel(studentCheck({ status }), lastSeconds);

                expect(component['view']()).toBe('closed');
                expect(translated('metis-state-closed', 'artemisApp.metis.student.closingText')).not.toBeNull();
                expect(translated('metis-state-closed', 'artemisApp.metis.student.closedTextGroup')).toBeNull();
                expect(statusTagText()).toContain('artemisApp.metis.student.closed');
                expect(element('metis-start-button')).toBeNull();
            },
        );

        it('should keep the open question of a started check until the window ends', () => {
            createPanel(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, currentQuestion: question({ shownAt: lastSeconds.subtract(1, 'minute') }) }), lastSeconds);

            expect(component['view']()).toBe('answering');
            expect(answerField()).not.toBeNull();
        });

        it('should not ask for a decision that can no longer lead to questions', () => {
            accountService.userIdentity.set({ login: 'student1' } as User);

            createPanel(studentCheck({ status: MetisCheckStatus.SKIPPED, failureReason: 'NO_AI_CONSENT' }), lastSeconds);

            expect(component['view']()).toBe('unavailable');
            expect(element('metis-state-consent')).toBeNull();
        });

        it('should still ask for the decision that the answer to the open question needs', () => {
            metisService.submitAnswer.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
            createPanel(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, currentQuestion: question({ shownAt: lastSeconds.subtract(1, 'minute') }) }), lastSeconds);
            typeAnswer('My answer');

            button('metis-submit-button').click();
            fixture.detectChanges();

            expect(component['view']()).toBe('consent');
            expect(component['draft']()).toBe('My answer');
        });
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

        it('should load the check again when the student decides about AI in another tab', () => {
            accountService.userIdentity.set({ login: 'student1' } as User);
            createPanel(studentCheck({ status: MetisCheckStatus.SKIPPED, failureReason: 'NO_AI_CONSENT' }));
            expect(component['view']()).toBe('consent');
            metisService.studentCheck.mockReturnValue(of(studentCheck({ status: MetisCheckStatus.PENDING })));

            // The Iris tab next to the panel stores the decision; the split panel keeps this panel alive meanwhile.
            accountService.setUserLLMSelectionDecision(LLMSelectionDecision.CLOUD_AI);
            fixture.detectChanges();

            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            expect(component['view']()).toBe('preparing');
            expect(element('metis-state-consent')).toBeNull();
        });

        it('should offer the start again once the decision changed in another tab after a refused start', () => {
            accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.LOCAL_AI } as User);
            metisService.startCheck.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
            createPanel(studentCheck());
            button('metis-start-button').click();
            fixture.detectChanges();
            expect(component['view']()).toBe('consent');

            accountService.setUserLLMSelectionDecision(LLMSelectionDecision.CLOUD_AI);
            fixture.detectChanges();

            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            expect(component['view']()).toBe('ready');
            expect(element('metis-start-button')).not.toBeNull();
        });

        it('should not load the check again when the account changes without a new decision', () => {
            createPanel(studentCheck());

            accountService.userIdentity.set({ login: 'student1', selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User);
            fixture.detectChanges();

            expect(metisService.studentCheck).toHaveBeenCalledOnce();
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

        it('should reload once the window leaves no time for a question while the check is ready, show it closed, and reload at the window end', () => {
            createPanelAt(studentCheck(), windowEnd.subtract(10, 'minute'));
            expect(component['view']()).toBe('ready');

            advance(9 * 60 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledOnce();
            advance(1000);
            fixture.detectChanges();

            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            expect(component['view']()).toBe('closed');
            expect(translated('metis-state-closed', 'artemisApp.metis.student.closingText')).not.toBeNull();
            expect(element('metis-start-button')).toBeNull();

            advance(59 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            advance(1000);
            fixture.detectChanges();

            expect(metisService.studentCheck).toHaveBeenCalledTimes(3);
            expect(translated('metis-state-closed', 'artemisApp.metis.student.closedTextGroup')).not.toBeNull();

            // Nothing polls once the window has ended.
            advance(5 * 60 * 60 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(3);
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

        it('should load the check again after every attempt of a timed answer failed, as its timer runs out only once', () => {
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
            // The answer goes out three more times within the grace of the server, and each attempt fails as well.
            advance(10 * 1000);
            expect(metisService.submitAnswer).toHaveBeenCalledTimes(4);
            expect(metisService.submitAnswer).toHaveBeenNthCalledWith(4, exerciseId, 1, '');
            expect(metisService.studentCheck).toHaveBeenCalledOnce();

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

        it('should send an answer sent by hand again within the grace when it fails after the timer ran out while it was on its way', () => {
            createPanelAt(
                studentCheck({ status: MetisCheckStatus.IN_PROGRESS, windowEnd: undefined, currentQuestion: question({ id: 1, shownAt: duringWindow, timeLimitSeconds: 60 }) }),
                duringWindow.add(30, 'second'),
            );
            const answer = new Subject<MetisStudentCheckDTO>();
            metisService.submitAnswer.mockReturnValueOnce(answer);
            typeAnswer('The outer loop shrinks the unsorted part.');
            button('metis-submit-button').click();
            // The timer runs out while the answer is on its way, so it sends nothing of its own
            advance(31 * 1000);
            fixture.debugElement.query(By.directive(QuestionTimerComponent)).componentInstance.expired.emit();
            expect(metisService.submitAnswer).toHaveBeenCalledOnce();

            // The answer fails on its way, so it goes out again within the grace of the server, not after the reload wait that would leave it empty
            metisService.submitAnswer.mockReturnValue(of(studentCheck({ status: MetisCheckStatus.COMPLETED, windowEnd: undefined })));
            answer.error(new HttpErrorResponse({ status: 504 }));
            advance(999);
            expect(metisService.submitAnswer).toHaveBeenCalledOnce();
            advance(1);

            expect(metisService.submitAnswer).toHaveBeenCalledTimes(2);
            expect(metisService.submitAnswer).toHaveBeenNthCalledWith(2, exerciseId, 1, 'The outer loop shrinks the unsorted part.');
            expect(metisService.studentCheck).toHaveBeenCalledOnce();
        });

        it('should keep sending an answer again when a reload at the window end settles during the wait, and unlock the answer box afterwards', () => {
            // The deadline of the last question is the end of the window, and the panel reloads just after that end while the answer is on its way
            const lastQuestion = question({ id: 1, shownAt: windowEnd.subtract(2, 'minute'), timeLimitSeconds: 240 });
            createPanelAt(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, currentQuestion: lastQuestion }), windowEnd.subtract(2, 'second'));
            const answer = new Subject<MetisStudentCheckDTO>();
            const reload = new Subject<MetisStudentCheckDTO>();
            metisService.submitAnswer.mockReturnValueOnce(answer);
            metisService.studentCheck.mockReturnValueOnce(reload);
            typeAnswer('The outer loop shrinks the unsorted part.');
            button('metis-submit-button').click();
            advance(3 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);

            // The answer fails while the reload is on its way, and the reload settles before the answer goes out again: the window has ended, so the server
            // presents no open question any more
            answer.error(new HttpErrorResponse({ status: 502 }));
            reload.next(studentCheck({ status: MetisCheckStatus.IN_PROGRESS }));
            reload.complete();
            metisService.submitAnswer.mockReturnValue(of(studentCheck({ status: MetisCheckStatus.IN_PROGRESS })));
            advance(1000);

            // Within the grace of the server, which stores it as written
            expect(metisService.submitAnswer).toHaveBeenCalledTimes(2);
            expect(metisService.submitAnswer).toHaveBeenNthCalledWith(2, exerciseId, 1, 'The outer loop shrinks the unsorted part.');
            expect(component['submitting']()).toBe(false);
        });

        it('should keep the answer box locked while an answer sent by hand goes out again, and unlock it with the draft once every attempt failed', () => {
            createPanelAt(
                studentCheck({ status: MetisCheckStatus.IN_PROGRESS, windowEnd: undefined, currentQuestion: question({ id: 1, shownAt: duringWindow, timeLimitSeconds: 600 }) }),
                duringWindow.add(30, 'second'),
            );
            metisService.submitAnswer.mockReturnValue(throwError(() => new Error('offline')));
            typeAnswer('Draft');

            button('metis-submit-button').click();
            fixture.detectChanges();

            // The answer goes out three more times within the grace of the server, after 1, 4 and 10 seconds, and nothing else can be sent meanwhile
            expect(component['submitting']()).toBe(true);
            expect(answerField().disabled).toBe(true);
            advance(10 * 1000 - 1);
            expect(metisService.submitAnswer).toHaveBeenCalledTimes(3);
            expect(component['submitting']()).toBe(true);
            advance(1);
            fixture.detectChanges();

            expect(metisService.submitAnswer).toHaveBeenCalledTimes(4);
            expect(metisService.submitAnswer).toHaveBeenNthCalledWith(4, exerciseId, 1, 'Draft');
            expect(component['submitting']()).toBe(false);
            expect(component['draft']()).toBe('Draft');
            expect(answerField().disabled).toBe(false);
        });

        it('should send an answer sent by hand again at once when its response got lost, and show the question the server opened meanwhile', () => {
            createPanelAt(
                studentCheck({ status: MetisCheckStatus.IN_PROGRESS, windowEnd: undefined, currentQuestion: question({ id: 1, shownAt: duringWindow, timeLimitSeconds: 60 }) }),
                duringWindow.add(30, 'second'),
            );
            // The server stored the answer and opened the next question, whose timer runs from now, but the response got lost on its way
            const answered = { ...question({ id: 1, shownAt: duringWindow }), answerText: 'The outer loop shrinks the unsorted part.', answeredAt: duringWindow.add(30, 'second') };
            const nextQuestion = question({ id: 2, questionOrder: 2, shownAt: duringWindow.add(30, 'second'), timeLimitSeconds: 30 });
            metisService.submitAnswer
                .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 504 })))
                .mockReturnValue(of(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, windowEnd: undefined, answeredQuestions: [answered], currentQuestion: nextQuestion })));
            typeAnswer('The outer loop shrinks the unsorted part.');
            button('metis-submit-button').click();

            advance(999);
            expect(metisService.submitAnswer).toHaveBeenCalledOnce();
            advance(1);
            fixture.detectChanges();

            // The server keeps the first answer and hands back the check as it has it, so the next question appears at once, not after the retry wait
            expect(metisService.submitAnswer).toHaveBeenCalledTimes(2);
            expect(metisService.submitAnswer).toHaveBeenNthCalledWith(2, exerciseId, 1, 'The outer loop shrinks the unsorted part.');
            expect(component['currentEntry']()?.question.id).toBe(2);
            expect(metisService.studentCheck).toHaveBeenCalledOnce();
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
            // The answer goes out three more times within the grace of the server; all of them fail as well.
            advance(10 * 1000);
            fixture.detectChanges();
            expect(metisService.submitAnswer).toHaveBeenCalledTimes(4);
            // The answer box is open again while the panel waits; what the student types meanwhile is kept for the same question.
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

            expect(metisService.submitAnswer).toHaveBeenCalledTimes(5);
            expect(metisService.submitAnswer).toHaveBeenNthCalledWith(1, exerciseId, 1, '');
            expect(metisService.submitAnswer).toHaveBeenNthCalledWith(5, exerciseId, 1, 'Typed after the timer ran out');
            // It fails again on every attempt, so the next reload waits again instead of following at once.
            advance(10 * 1000);
            expect(metisService.submitAnswer).toHaveBeenCalledTimes(8);
            expect(metisService.submitAnswer).toHaveBeenNthCalledWith(8, exerciseId, 1, 'Typed after the timer ran out');
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
            // The gateway keeps failing during the attempts within the grace, so the check is loaded after the retry wait.
            advance(10 * 1000 + 30 * 1000);
            fixture.detectChanges();

            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            expect(component['currentEntry']()?.question.id).toBe(2);
            // The draft answered the first question, which the server closed; kept, it would go out as the answer to the follow-up.
            expect(component['draft']()).toBe('');
            expect(answerField().value).toBe('');

            fixture.debugElement.query(By.directive(QuestionTimerComponent)).componentInstance.expired.emit();

            expect(metisService.submitAnswer).toHaveBeenCalledTimes(5);
            expect(metisService.submitAnswer).toHaveBeenNthCalledWith(1, exerciseId, 1, 'The last i positions are final.');
            expect(metisService.submitAnswer).toHaveBeenNthCalledWith(4, exerciseId, 1, 'The last i positions are final.');
            expect(metisService.submitAnswer).toHaveBeenNthCalledWith(5, exerciseId, 2, '');
        });

        it('should send a timed answer again within the grace of the server when it failed on its way', () => {
            const expiredQuestion = question({ id: 1, shownAt: duringWindow, timeLimitSeconds: 60 });
            const timedOut = duringWindow.add(60, 'second');
            createPanelAt(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, windowEnd: undefined, currentQuestion: expiredQuestion }), timedOut);
            typeAnswer('The outer loop shrinks the unsorted part.');
            const followUp = question({ id: 2, parentQuestionId: 1, questionOrder: 2, timeLimitSeconds: 90, shownAt: timedOut.add(1, 'second') });
            const answeredQuestions = [{ ...expiredQuestion, answerText: 'The outer loop shrinks the unsorted part.', answeredAt: timedOut.add(1, 'second') }];
            metisService.submitAnswer
                .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })))
                .mockReturnValueOnce(of(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, windowEnd: undefined, answeredQuestions, currentQuestion: followUp })));

            fixture.debugElement.query(By.directive(QuestionTimerComponent)).componentInstance.expired.emit();
            fixture.detectChanges();
            // The answer box stays locked while the answer goes out again, so it keeps the text it had when the timer ran out.
            expect(answerField().disabled).toBe(true);

            advance(999);
            expect(metisService.submitAnswer).toHaveBeenCalledOnce();
            advance(1);
            fixture.detectChanges();

            expect(metisService.submitAnswer).toHaveBeenCalledTimes(2);
            expect(metisService.submitAnswer).toHaveBeenNthCalledWith(2, exerciseId, 1, 'The outer loop shrinks the unsorted part.');
            expect(component['currentEntry']()?.question.id).toBe(2);
            expect(answerField().disabled).toBe(false);
            // The answer got through, so no reload follows.
            advance(5 * 60 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledOnce();
        });

        it('should not send a timed answer again that the server refused, and load the check after the retry wait', () => {
            createPanelAt(
                studentCheck({ status: MetisCheckStatus.IN_PROGRESS, windowEnd: undefined, currentQuestion: question({ id: 1, shownAt: duringWindow, timeLimitSeconds: 60 }) }),
                duringWindow.add(60, 'second'),
            );
            metisService.submitAnswer.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));

            fixture.debugElement.query(By.directive(QuestionTimerComponent)).componentInstance.expired.emit();
            fixture.detectChanges();
            advance(30 * 1000 - 1);

            expect(metisService.submitAnswer).toHaveBeenCalledOnce();
            expect(metisService.studentCheck).toHaveBeenCalledOnce();
            advance(1);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
        });

        it('should reload a check whose shown ratings are incomplete, less often each time, until none is missing', () => {
            const unrated = { ...question(), answerText: 'A', answeredAt: duringWindow };
            createPanelAt(studentCheck({ status: MetisCheckStatus.COMPLETED, ratingsVisible: true, answeredQuestions: [unrated] }), windowEnd.add(1, 'minute'));
            expect(component['view']()).toBe('results');

            advance(10 * 1000 - 1);
            expect(metisService.studentCheck).toHaveBeenCalledOnce();
            advance(1);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            // The rating is still missing, so the next reload waits twice as long.
            advance(20 * 1000 - 1);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            metisService.studentCheck.mockReturnValue(
                of(studentCheck({ status: MetisCheckStatus.COMPLETED, ratingsVisible: true, answeredQuestions: [{ ...unrated, llmScore: 4 }] })),
            );
            advance(1);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(3);

            // Every shown answer has its rating now, so nothing polls any more.
            advance(5 * 60 * 60 * 1000);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(3);
        });

        it('should reload a closed check while the ratings it shows are incomplete', () => {
            const unrated = { ...question(), answerText: 'A', answeredAt: duringWindow };
            createPanelAt(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, ratingsVisible: true, answeredQuestions: [unrated] }), windowEnd.add(1, 'minute'));
            expect(component['view']()).toBe('closed');

            advance(10 * 1000);

            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
        });

        it('should not reload for ratings that the student does not see', () => {
            const unrated = { ...question(), answerText: 'A', answeredAt: duringWindow };
            createPanelAt(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, ratingsVisible: false, answeredQuestions: [unrated] }), windowEnd.add(1, 'minute'));
            expect(component['view']()).toBe('closed');

            advance(5 * 60 * 60 * 1000);

            expect(metisService.studentCheck).toHaveBeenCalledOnce();
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

        it('should reload the consent request once the window leaves no time for a question, after which no decision leads to questions', () => {
            createPanelAt(studentCheck({ status: MetisCheckStatus.SKIPPED, failureReason: 'NO_AI_CONSENT' }), duringWindow);
            expect(component['view']()).toBe('consent');

            advance(windowEnd.subtract(1, 'minute').diff(duringWindow));
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

        it('should not load the check again when the server refused the first load', () => {
            metisService.studentCheck.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
            clock = duringWindow;
            vi.spyOn(serverDateService, 'now').mockImplementation(() => clock);
            render();

            advance(5 * 60 * 60 * 1000);

            expect(metisService.studentCheck).toHaveBeenCalledOnce();
        });

        it('should load the check again after the retry wait when the first load of a panel created anew broke off, and give back the kept draft', () => {
            const openQuestion = question({ id: 1, shownAt: duringWindow, timeLimitSeconds: 240 });
            createPanelAt(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, windowEnd: undefined, currentQuestion: openQuestion }), duringWindow.add(30, 'second'));
            typeAnswer('The inner loop stops once');
            fixture.destroy();

            // The split view builds the panel anew, and its first load fails on its way
            metisService.studentCheck.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })));
            render();
            expect(component['view']()).toBe('unavailable');
            advance(30 * 1000 - 1);
            expect(metisService.studentCheck).toHaveBeenCalledTimes(2);
            advance(1);
            fixture.detectChanges();

            expect(metisService.studentCheck).toHaveBeenCalledTimes(3);
            expect(component['view']()).toBe('answering');
            expect(component['draft']()).toBe('The inner loop stops once');
            expect(answerField().value).toBe('The inner loop stops once');
        });

        it('should stop reloading once the panel is destroyed', () => {
            createPanelAt(studentCheck({ status: MetisCheckStatus.PENDING }), windowStart.add(1, 'minute'));

            fixture.destroy();
            advance(60 * 1000);

            expect(metisService.studentCheck).toHaveBeenCalledOnce();
        });
    });

    // The exercise page can hand the panel the next exercise without creating it anew, when the tab stays open while the student moves on
    describe('when the host hands it another exercise', () => {
        const otherExerciseId = 8;

        function switchExercise(): void {
            fixture.componentRef.setInput('exerciseId', otherExerciseId);
            fixture.detectChanges();
        }

        it('should load the check of the new exercise and show nothing of the previous one meanwhile', () => {
            createPanel(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, currentQuestion: question({ id: 1, shownAt: duringWindow }) }));
            typeAnswer('The last i positions are final.');
            const otherCheck = new Subject<MetisStudentCheckDTO>();
            metisService.studentCheck.mockReturnValue(otherCheck);

            switchExercise();

            // Until the check of the new exercise arrives, the panel loads instead of offering the question of the previous one
            expect(metisService.studentCheck).toHaveBeenLastCalledWith(otherExerciseId);
            expect(component['view']()).toBe('loading');
            expect(answerField()).toBeNull();

            otherCheck.next(
                studentCheck({ status: MetisCheckStatus.IN_PROGRESS, currentQuestion: question({ id: 5, shownAt: duringWindow, questionText: 'Why is the merge stable?' }) }),
            );
            fixture.detectChanges();

            expect(component['currentEntry']()?.question.id).toBe(5);
            expect(answerField().value).toBe('');
        });

        it('should not send an answer to the previous exercise again after the switch', () => {
            vi.useFakeTimers();
            createPanel(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, currentQuestion: question({ id: 1, shownAt: duringWindow, timeLimitSeconds: 600 }) }));
            metisService.submitAnswer.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 504 })));
            typeAnswer('The outer loop shrinks the unsorted part.');
            button('metis-submit-button').click();
            expect(metisService.submitAnswer).toHaveBeenCalledOnce();
            metisService.studentCheck.mockReturnValue(of(studentCheck({ status: MetisCheckStatus.READY })));

            switchExercise();
            vi.advanceTimersByTime(20 * 1000);

            // The resend was due after a second, and it would have sent question 1 of the previous exercise against the new one
            expect(metisService.submitAnswer).toHaveBeenCalledOnce();
            expect(component['submitting']()).toBe(false);
        });

        it('should ignore a start of the previous exercise that comes back after the switch', () => {
            createPanel(studentCheck({ status: MetisCheckStatus.READY }));
            const start = new Subject<MetisStudentCheckDTO>();
            metisService.startCheck.mockReturnValue(start);
            button('metis-start-button').click();
            const otherCheck = studentCheck({ status: MetisCheckStatus.READY, mainQuestionCount: 2 });
            metisService.studentCheck.mockReturnValue(of(otherCheck));

            switchExercise();
            start.next(studentCheck({ status: MetisCheckStatus.IN_PROGRESS, currentQuestion: question({ id: 1, shownAt: duringWindow }) }));
            fixture.detectChanges();

            // The started check belongs to the previous exercise; the panel keeps the check of the new one, which the student can start
            expect(component['check']()).toEqual(otherCheck);
            expect(component['view']()).toBe('ready');
            expect(component['starting']()).toBe(false);
        });
    });
});
