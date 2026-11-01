import { afterEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Observable, Subject, of, throwError } from 'rxjs';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { TumAetUiConfirmationService } from '@tumaet/ui-angular';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CourseTitleBarTitleComponent } from 'app/course/shared/course-title-bar-title/course-title-bar-title.component';
import { QuizQuestionPoolItemListComponent } from './quiz-question-pool-item-list/quiz-question-pool-item-list.component';
import { QuizQuestionPoolSettingsDialogComponent } from './quiz-question-pool-settings-dialog/quiz-question-pool-settings-dialog.component';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { InfiniteScrollDirective } from 'app/shared-ui/infinite-scroll/infinite-scroll.directive';
import { AlertService } from 'app/foundation/service/alert.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Course } from 'app/course/shared/entities/course.model';
import { QuizQuestionPoolComponent } from './quiz-question-pool.component';
import { QuizQuestionPoolService } from './quiz-question-pool.service';
import { PoolProgress, PoolQuestion, PoolQuestionPage, PoolQuestionQuery, PoolSettings } from './quiz-question-pool.types';

describe('QuizQuestionPoolComponent', () => {
    let fixture: ComponentFixture<QuizQuestionPoolComponent>;
    let component: QuizQuestionPoolComponent;
    let poolService: QuizQuestionPoolService;
    let courseManagementService: CourseManagementService;
    let alertService: AlertService;

    const progress: PoolProgress = { running: false, total: 10, toGenerate: 0, toJudge: 0, judged: 9, accepted: 7, rejected: 2, failed: 1 };
    const settings: PoolSettings = {
        poolGenerationEnabled: false,
        singleChoiceQuestionsPerLectureUnit: 12,
        multipleChoiceEnabled: false,
        multipleChoiceQuestionsPerLectureUnit: 12,
        multipleChoiceMinCorrectOptions: 1,
        multipleChoiceMaxCorrectOptions: 4,
        easyPercent: 20,
        mediumPercent: 50,
        hardPercent: 30,
    };

    function question(id: number, competencyId: number, title: string): PoolQuestion {
        return {
            id,
            competencyId,
            competencyTitle: title,
            lectureUnitId: competencyId * 10,
            lectureUnitName: `Unit ${competencyId}`,
            language: 'en',
            questionType: 'single-choice',
            difficulty: 'MEDIUM',
            state: 'FILTERED',
            reviewState: 'PENDING',
            flagged: false,
            question: { id: `pool-${id}`, type: 'single-choice', title: `Q${id}`, questionText: 'Q?', options: [{ text: 'A', correct: true }] },
            decision: { accepted: true, aggregateScore: 1, verdicts: [], rationale: 'ok' },
            updatedAt: '2026-10-13T10:00:00Z',
        };
    }

    const questions = (): PoolQuestion[] => [question(1, 9, 'Arrays'), question(2, 9, 'Arrays'), question(3, 10, 'Loops')];

    function page(items: PoolQuestion[], totalCount = items.length): PoolQuestionPage {
        const competencies = new Map<number, string>();
        for (const item of items) {
            competencies.set(item.competencyId, item.competencyTitle);
        }
        return { items, totalCount, competencies: [...competencies.entries()].map(([id, title]) => ({ id, title })) };
    }

    /** Answers list requests like the server: the queue holds flagged pending questions, the list follows the competency filter. */
    function pagesOf(all: PoolQuestion[]): (courseId: number, query: PoolQuestionQuery) => Observable<PoolQuestionPage> {
        return (_courseId, query) =>
            of(page(query.flagged ? all.filter((item) => item.flagged) : all.filter((item) => query.competencyId === undefined || item.competencyId === query.competencyId)));
    }

    function listCalls(spy: { mock: { calls: unknown[][] } }): unknown[][] {
        return spy.mock.calls.filter((call) => !(call[1] as PoolQuestionQuery).flagged);
    }

    function setup(course: Partial<Course>, params: Observable<Record<string, string>> = of({ courseId: '1' })): void {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: { params } },
                MockProvider(DeleteDialogService),
            ],
        }).overrideComponent(QuizQuestionPoolComponent, {
            remove: { imports: [CourseTitleBarTitleComponent, QuizQuestionPoolItemListComponent, QuizQuestionPoolSettingsDialogComponent, InfiniteScrollDirective] },
            add: {
                imports: [
                    MockComponent(CourseTitleBarTitleComponent),
                    MockComponent(QuizQuestionPoolItemListComponent),
                    MockComponent(QuizQuestionPoolSettingsDialogComponent),
                    MockDirective(InfiniteScrollDirective),
                ],
            },
        });
        poolService = TestBed.inject(QuizQuestionPoolService);
        courseManagementService = TestBed.inject(CourseManagementService);
        alertService = TestBed.inject(AlertService);
        vi.spyOn(courseManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: { id: 1, ...course } as Course })));
        vi.spyOn(poolService, 'getProgress').mockReturnValue(of(progress));
        vi.spyOn(poolService, 'getQuestions').mockImplementation(pagesOf(questions()));
        vi.spyOn(poolService, 'getSettings').mockReturnValue(of(settings));
        fixture = TestBed.createComponent(QuizQuestionPoolComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    }

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('should load course, progress, questions and settings for an instructor', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });

        expect(component.courseId()).toBe(1);
        expect(component.isInstructor()).toBe(true);
        expect(component.canReview()).toBe(true);
        expect(component.progress()).toEqual(progress);
        expect(component.questions()).toHaveLength(3);
        expect(component.settings()).toEqual(settings);
        expect(poolService.getSettings).toHaveBeenCalledWith(1);
    });

    it('should not load settings for an editor', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: false });

        expect(component.isInstructor()).toBe(false);
        expect(component.canReview()).toBe(true);
        expect(poolService.getSettings).not.toHaveBeenCalled();
        expect(component.settings()).toBeUndefined();
    });

    it('should group questions by competency and lecture unit and filter by the selected competency', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });

        expect(component.competencyOptions()).toEqual([
            { id: 9, title: 'Arrays' },
            { id: 10, title: 'Loops' },
        ]);
        expect(component.groups().map((group) => [group.id, group.units.map((unit) => [unit.id, unit.name, unit.questions.length])])).toEqual([
            [9, [[90, 'Unit 9', 2]]],
            [10, [[100, 'Unit 10', 1]]],
        ]);

        component.selectCompetency(10);
        expect(poolService.getQuestions).toHaveBeenLastCalledWith(1, { competencyId: 10, page: 0, size: 20 });
        expect(component.groups().map((group) => group.id)).toEqual([10]);

        component.selectCompetency(undefined);
        expect(component.groups()).toHaveLength(2);
    });

    it('should load the next page on request and append it', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const all = Array.from({ length: 250 }, (_, index) => question(index + 1, 9, 'Arrays'));
        const listSpy = vi.spyOn(poolService, 'getQuestions').mockImplementation((_courseId, query) => {
            if (query.flagged) {
                return of(page([]));
            }
            return of(page(all.slice(query.page * query.size, (query.page + 1) * query.size), all.length));
        });
        component.refresh();
        expect(component.questions()).toHaveLength(20);
        expect(component.totalQuestionCount()).toBe(250);
        expect(component.remainingQuestions()).toBe(230);

        component.loadMoreQuestions();

        expect(listCalls(listSpy).at(-1)).toEqual([1, { competencyId: undefined, page: 1, size: 20 }]);
        expect(component.questions()).toHaveLength(40);
        expect(component.questions().at(-1)?.id).toBe(40);
        expect(component.remainingQuestions()).toBe(210);
    });

    it('should show flagged questions as a queue that folds once every one of them is reviewed', () => {
        const flagged = { ...question(1, 9, 'Arrays'), flagged: true };
        const all = [flagged, question(2, 9, 'Arrays'), question(3, 10, 'Loops')];
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        vi.spyOn(poolService, 'getQuestions').mockImplementation(pagesOf(all));
        component.refresh();

        fixture.detectChanges();
        expect(component.showNeedsReviewQueue()).toBe(true);
        expect(component.needsReviewQuestions().map((item) => item.id)).toEqual([1]);
        expect(component.pendingInQueue()).toBe(1);
        expect(component.queueCollapsed()).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="pool-needs-review-queue"]')).not.toBeNull();

        vi.spyOn(poolService, 'review').mockReturnValue(of({ ...flagged, reviewState: 'APPROVED' }));
        component.review({ questionId: 1, reviewState: 'APPROVED' });
        fixture.detectChanges();

        expect(component.needsReviewQuestions().map((item) => item.reviewState)).toEqual(['APPROVED']);
        expect(component.pendingInQueue()).toBe(0);
        expect(component.allFlaggedReviewed()).toBe(true);
        expect(component.queueCollapsed()).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="pool-needs-review-done"]')).not.toBeNull();
        expect(component.questions().find((item) => item.id === 1)?.reviewState).toBe('APPROVED');

        component.selectCompetency(9);
        expect(component.showNeedsReviewQueue()).toBe(false);
    });

    it('should fold and unfold the review queue from anywhere in its header row except the toggle button', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const header = document.createElement('div');
        header.className = 'tumaet-ui-panel-header';
        const title = document.createElement('span');
        const toggler = document.createElement('button');
        toggler.className = 'tumaet-ui-panel-toggler';
        header.append(title, toggler);
        const content = document.createElement('div');
        const clickOn = (target: Element) => component.toggleQueueFromHeader({ target } as unknown as MouseEvent);

        clickOn(title);
        expect(component.queueCollapsed()).toBe(true);
        clickOn(header);
        expect(component.queueCollapsed()).toBe(false);
        clickOn(toggler);
        clickOn(content);
        expect(component.queueCollapsed()).toBe(false);
    });

    it('should drop the previous course and its polling when the route moves to another course', () => {
        vi.useFakeTimers();
        const params = new Subject<Record<string, string>>();
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true }, params.asObservable());
        const progressSpy = vi.spyOn(poolService, 'getProgress').mockReturnValue(of({ ...progress, running: true }));
        params.next({ courseId: '1' });
        component.selectCompetency(9);
        component.openSettingsDialog();
        expect(component.courseId()).toBe(1);
        expect(component.questions()).toHaveLength(2);
        expect(component.isRunning()).toBe(true);
        const pollsOfFirstCourse = progressSpy.mock.calls.filter((call) => call[0] === 1).length;

        progressSpy.mockReturnValue(of({ ...progress, running: false }));
        vi.spyOn(poolService, 'getQuestions').mockImplementation(pagesOf([]));
        params.next({ courseId: '2' });
        vi.advanceTimersByTime(6000);

        expect(component.courseId()).toBe(2);
        expect(component.questions()).toEqual([]);
        expect(component.selectedCompetencyId()).toBeUndefined();
        expect(component.settingsDialogVisible()).toBe(false);
        expect(component.isRunning()).toBe(false);
        expect(progressSpy.mock.calls.filter((call) => call[0] === 1)).toHaveLength(pollsOfFirstCourse);
        expect(progressSpy).toHaveBeenCalledWith(2);
    });

    it('should ignore a late build start and a late settings save of a previous course', () => {
        vi.useFakeTimers();
        const params = new Subject<Record<string, string>>();
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true }, params.asObservable());
        const progressSpy = vi.spyOn(poolService, 'getProgress').mockReturnValue(of({ ...progress, running: false }));
        params.next({ courseId: '1' });
        const lateStart = new Subject<{ started: boolean; enqueued: number }>();
        const lateSave = new Subject<PoolSettings>();
        vi.spyOn(poolService, 'startBuild').mockReturnValue(lateStart.asObservable());
        vi.spyOn(poolService, 'updateSettings').mockReturnValue(lateSave.asObservable());
        component.startBuild();
        component.openSettingsDialog();
        component.saveSettings({ ...settings, singleChoiceQuestionsPerLectureUnit: 30 });

        params.next({ courseId: '2' });
        const pollsOfFirstCourse = progressSpy.mock.calls.filter((call) => call[0] === 1).length;
        lateStart.next({ started: true, enqueued: 12 });
        lateStart.complete();
        lateSave.next({ ...settings, poolGenerationEnabled: true, singleChoiceQuestionsPerLectureUnit: 30 });
        lateSave.complete();
        vi.advanceTimersByTime(6000);

        expect(progressSpy.mock.calls.filter((call) => call[0] === 1)).toHaveLength(pollsOfFirstCourse);
        expect(component.settings()).toEqual(settings);
    });

    it('should keep polling when a progress request fails once', () => {
        vi.useFakeTimers();
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        vi.spyOn(poolService, 'startBuild').mockReturnValue(of({ started: true, enqueued: 12 }));
        const progressSpy = vi.spyOn(poolService, 'getProgress');
        progressSpy
            .mockReturnValueOnce(throwError(() => new Error('network')))
            .mockReturnValueOnce(of({ ...progress, running: true }))
            .mockReturnValueOnce(of({ ...progress, running: false }));

        component.startBuild();
        vi.advanceTimersByTime(3000);
        expect(component.isRunning()).toBe(false);
        vi.advanceTimersByTime(3000);
        expect(component.isRunning()).toBe(true);
        vi.advanceTimersByTime(3000);
        expect(component.isRunning()).toBe(false);
        const callsAfterStop = progressSpy.mock.calls.length;
        vi.advanceTimersByTime(6000);

        expect(progressSpy.mock.calls.length).toBe(callsAfterStop);
    });

    it('should skip poll ticks while a progress request is pending and replace one that times out', () => {
        vi.useFakeTimers();
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        vi.spyOn(poolService, 'startBuild').mockReturnValue(of({ started: true, enqueued: 12 }));
        const slow = new Subject<PoolProgress>();
        const hung = new Subject<PoolProgress>();
        const progressSpy = vi.spyOn(poolService, 'getProgress').mockReturnValueOnce(slow.asObservable()).mockReturnValueOnce(hung.asObservable());
        component.startBuild();
        const callsAtStart = progressSpy.mock.calls.length;

        vi.advanceTimersByTime(6000);
        expect(progressSpy.mock.calls.length).toBe(callsAtStart + 1);
        slow.next({ ...progress, running: true });
        slow.complete();
        expect(component.isRunning()).toBe(true);

        vi.advanceTimersByTime(3000);
        expect(progressSpy.mock.calls.length).toBe(callsAtStart + 2);
        vi.advanceTimersByTime(9000);
        expect(progressSpy.mock.calls.length).toBe(callsAtStart + 2);
        vi.advanceTimersByTime(3000);
        expect(progressSpy.mock.calls.length).toBe(callsAtStart + 3);
    });

    it('should keep polling until the final question list arrives', () => {
        vi.useFakeTimers();
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        vi.spyOn(poolService, 'startBuild').mockReturnValue(of({ started: true, enqueued: 12 }));
        const progressSpy = vi.spyOn(poolService, 'getProgress').mockReturnValue(of({ ...progress, running: false }));
        let failedOnce = false;
        const questionsSpy = vi.spyOn(poolService, 'getQuestions').mockImplementation((_courseId, query) => {
            if (!query.flagged && !failedOnce) {
                failedOnce = true;
                return throwError(() => new Error('network'));
            }
            return of(page(questions()));
        });
        component.startBuild();
        const progressCallsAtStart = progressSpy.mock.calls.length;
        const questionCallsAtStart = listCalls(questionsSpy).length;

        vi.advanceTimersByTime(3000);
        expect(listCalls(questionsSpy).length).toBe(questionCallsAtStart + 1);
        vi.advanceTimersByTime(3000);
        expect(listCalls(questionsSpy).length).toBe(questionCallsAtStart + 2);
        expect(progressSpy.mock.calls.length).toBe(progressCallsAtStart + 2);
        vi.advanceTimersByTime(6000);

        expect(progressSpy.mock.calls.length).toBe(progressCallsAtStart + 2);
    });

    it('should keep polling for a build started while the final question list of the previous build is pending', () => {
        vi.useFakeTimers();
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const progressSpy = vi.spyOn(poolService, 'getProgress').mockReturnValue(of({ ...progress, running: true }));
        vi.spyOn(poolService, 'startBuild').mockReturnValue(of({ started: true, enqueued: 3 }));
        component.startBuild();
        vi.advanceTimersByTime(3000);

        const finalList = new Subject<PoolQuestionPage>();
        vi.spyOn(poolService, 'getQuestions').mockImplementation((_courseId, query) => (query.flagged ? of(page([])) : finalList.asObservable()));
        progressSpy.mockReturnValue(of({ ...progress, running: false }));
        vi.advanceTimersByTime(3000);

        component.startBuild();
        finalList.next(page(questions()));
        finalList.complete();

        vi.spyOn(poolService, 'getQuestions').mockImplementation(pagesOf(questions()));
        progressSpy.mockClear();
        progressSpy.mockReturnValue(of({ ...progress, running: true }));
        vi.advanceTimersByTime(3000);

        expect(progressSpy).toHaveBeenCalledOnce();
        expect(component.isRunning()).toBe(true);
    });

    it('should compute the progress percentage from judged and failed items', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });

        expect(component.progressPercent()).toBe(100);
        component.progress.set({ ...progress, judged: 4, failed: 1 });
        expect(component.progressPercent()).toBe(50);
        component.progress.set({ ...progress, total: 0 });
        expect(component.progressPercent()).toBe(0);
    });

    it('should start a build, report the added questions and poll until the build ends', () => {
        vi.useFakeTimers();
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        vi.spyOn(poolService, 'startBuild').mockReturnValue(of({ started: true, enqueued: 12 }));
        const successSpy = vi.spyOn(alertService, 'success');
        const progressSpy = vi.spyOn(poolService, 'getProgress');
        progressSpy.mockReturnValueOnce(of({ ...progress, running: true })).mockReturnValueOnce(of({ ...progress, running: false }));

        component.startBuild();
        const pollsAtStart = progressSpy.mock.calls.length;

        expect(successSpy).toHaveBeenCalledWith('artemisApp.hyperion.quizQuestionPool.progress.started', { count: 12 });

        vi.advanceTimersByTime(3000);
        expect(progressSpy.mock.calls.length).toBe(pollsAtStart + 1);
        expect(component.isRunning()).toBe(true);

        vi.advanceTimersByTime(3000);
        expect(component.isRunning()).toBe(false);
        vi.advanceTimersByTime(6000);
        expect(progressSpy.mock.calls.length).toBe(pollsAtStart + 2);
    });

    it('should cancel a running build after confirmation and leave the cancelling state once the build has stopped', () => {
        vi.useFakeTimers();
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const cancelSpy = vi.spyOn(poolService, 'cancelBuild').mockReturnValue(of(undefined));
        const successSpy = vi.spyOn(alertService, 'success');
        const confirmationService = fixture.debugElement.injector.get(TumAetUiConfirmationService);
        component.progress.set({ ...progress, running: true });

        component.confirmCancelBuild();
        expect(cancelSpy).not.toHaveBeenCalled();
        confirmationService.request(undefined)!.accept();

        expect(cancelSpy).toHaveBeenCalledWith(1);
        expect(component.isCancelling()).toBe(true);
        expect(successSpy).toHaveBeenCalledWith('artemisApp.hyperion.quizQuestionPool.progress.cancelRequested');

        vi.advanceTimersByTime(3000);

        expect(component.isCancelling()).toBe(false);
        expect(component.isRunning()).toBe(false);
    });

    it('should explain a cancel request that finds no running build', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        vi.spyOn(poolService, 'cancelBuild').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
        const warningSpy = vi.spyOn(alertService, 'warning');
        const confirmationService = fixture.debugElement.injector.get(TumAetUiConfirmationService);
        component.progress.set({ ...progress, running: true });

        component.confirmCancelBuild();
        confirmationService.request(undefined)!.accept();

        expect(warningSpy).toHaveBeenCalledWith('artemisApp.hyperion.quizQuestionPool.progress.cancelNotRunning');
        expect(component.isCancelling()).toBe(false);
    });

    it('should warn when a build is already running', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        vi.spyOn(poolService, 'startBuild').mockReturnValue(of({ started: false, enqueued: 0 }));
        const warningSpy = vi.spyOn(alertService, 'warning');

        component.startBuild();

        expect(warningSpy).toHaveBeenCalledWith('artemisApp.hyperion.quizQuestionPool.progress.alreadyRunning');
        component.ngOnDestroy();
        expect(component.isRunning()).toBe(false);
    });

    it('should report a failed build start', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        vi.spyOn(poolService, 'startBuild').mockReturnValue(throwError(() => new Error('boom')));
        const errorSpy = vi.spyOn(alertService, 'error');

        component.startBuild();

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.hyperion.quizQuestionPool.progress.buildFailed');
        expect(component.isStartingBuild()).toBe(false);
    });

    it('should show the settings summary to instructors', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });

        expect(fixture.nativeElement.querySelector('[data-testid="pool-settings-summary"]')).not.toBeNull();
    });

    it('should hide the settings summary from editors', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: false });

        expect(fixture.nativeElement.querySelector('[data-testid="pool-settings-summary"]')).toBeNull();
    });

    it('should show the empty state with a build action while the pool has no items', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const buildSpy = vi.spyOn(poolService, 'startBuild').mockReturnValue(of({ started: true, enqueued: 12 }));
        component.progress.set({ ...progress, total: 0 });
        fixture.detectChanges();

        const emptyState = fixture.nativeElement.querySelector('[data-testid="pool-progress-empty"]');
        expect(emptyState).not.toBeNull();
        emptyState.querySelector('button').click();

        expect(buildSpy).toHaveBeenCalledWith(1);
    });

    it('should open the settings dialog, store the edited settings and close it', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const changed = { ...settings, poolGenerationEnabled: true, singleChoiceQuestionsPerLectureUnit: 30 };
        const updateSpy = vi.spyOn(poolService, 'updateSettings').mockReturnValue(of(changed));
        const successSpy = vi.spyOn(alertService, 'success');

        component.openSettingsDialog();
        expect(component.settingsDialogVisible()).toBe(true);
        component.saveSettings(changed);

        expect(updateSpy).toHaveBeenCalledWith(1, changed);
        expect(component.settings()).toEqual(changed);
        expect(component.settingsDialogVisible()).toBe(false);
        expect(component.isSavingSettings()).toBe(false);
        expect(successSpy).toHaveBeenCalledWith('artemisApp.hyperion.quizQuestionPool.settings.saved');
    });

    it('should ask for confirmation before storing settings while a build runs', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const changed = { ...settings, singleChoiceQuestionsPerLectureUnit: 6 };
        const updateSpy = vi.spyOn(poolService, 'updateSettings').mockReturnValue(of(changed));
        const confirmationService = fixture.debugElement.injector.get(TumAetUiConfirmationService);
        component.progress.set({ ...progress, running: true });

        component.saveSettings(changed);
        expect(updateSpy).not.toHaveBeenCalled();
        confirmationService.request(undefined)!.accept();

        expect(updateSpy).toHaveBeenCalledWith(1, changed);
        expect(component.settings()).toEqual(changed);
    });

    it('should list the settings of the running build above the stored ones while they differ', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const buildSettings = { ...settings, singleChoiceQuestionsPerLectureUnit: 5, multipleChoiceEnabled: true };
        component.progress.set({ ...progress, running: true, buildSettings });
        fixture.detectChanges();

        expect(component.runningBuildSettings()).toEqual(buildSettings);
        expect(component.runningBuildFacts().map((fact) => fact.values['count'])).toEqual([5, 12, undefined]);
        expect(component.settingsFacts().map((fact) => fact.changed)).toEqual([true, true, false, false]);
        expect(fixture.nativeElement.querySelector('[data-testid="pool-build-settings-summary"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelectorAll('[data-testid="pool-settings-summary"] [data-changed]')).toHaveLength(2);

        component.progress.set({ ...progress, running: true, buildSettings: settings });
        fixture.detectChanges();

        expect(component.runningBuildSettings()).toBeUndefined();
        expect(fixture.nativeElement.querySelector('[data-testid="pool-build-settings-summary"]')).toBeNull();
    });

    it('should keep the settings dialog open while a save is pending and after it fails', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const pending = new Subject<PoolSettings>();
        vi.spyOn(poolService, 'updateSettings').mockReturnValue(pending.asObservable());
        const errorSpy = vi.spyOn(alertService, 'error');
        component.openSettingsDialog();

        component.saveSettings({ ...settings, hardPercent: 30 });
        expect(component.isSavingSettings()).toBe(true);
        expect(component.settingsDialogVisible()).toBe(true);

        pending.error(new Error('boom'));

        expect(component.isSavingSettings()).toBe(false);
        expect(component.settingsDialogVisible()).toBe(true);
        expect(component.settings()).toEqual(settings);
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.hyperion.quizQuestionPool.settings.saveFailed');
    });

    it('should replace a question once its review decision is saved', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: false });
        const reviewed = { ...question(1, 9, 'Arrays'), reviewState: 'APPROVED' as const };
        const reviewSpy = vi.spyOn(poolService, 'review').mockReturnValue(of(reviewed));

        component.review({ questionId: 1, reviewState: 'APPROVED' });

        expect(reviewSpy).toHaveBeenCalledWith(1, 1, 'APPROVED');
        expect(component.questions().find((q) => q.id === 1)?.reviewState).toBe('APPROVED');
        expect(component.busyQuestionIds().has(1)).toBe(false);
    });

    it('should keep a review decision stored after a question list request started', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const staleList = new Subject<PoolQuestionPage>();
        vi.spyOn(poolService, 'getQuestions').mockImplementation((_courseId, query) => (query.flagged ? of(page([])) : staleList.asObservable()));
        component.refresh();
        const reviewed = { ...questions()[0], reviewState: 'APPROVED' as const };
        vi.spyOn(poolService, 'review').mockReturnValue(of(reviewed));
        component.review({ questionId: 1, reviewState: 'APPROVED' });
        expect(component.questions().find((question) => question.id === 1)?.reviewState).toBe('APPROVED');

        staleList.next(page(questions()));
        staleList.complete();

        expect(component.questions().find((question) => question.id === 1)?.reviewState).toBe('APPROVED');
        expect(component.questions().find((question) => question.id === 2)?.reviewState).toBe('PENDING');
    });

    it('should keep the newest question list when an older request answers later', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const first = new Subject<PoolQuestionPage>();
        const second = new Subject<PoolQuestionPage>();
        const lists = [first, second];
        vi.spyOn(poolService, 'getQuestions').mockImplementation((_courseId, query) => (query.flagged ? of(page([])) : lists.shift()!.asObservable()));
        component.refresh();
        component.refresh();
        const judged = questions();
        const generating = judged.map((question) => ({ ...question, state: 'GENERATING' as const, question: undefined, decision: undefined }));

        second.next(page(judged));
        second.complete();
        first.next(page(generating));
        first.complete();

        expect(component.questions().map((question) => question.state)).toEqual(['FILTERED', 'FILTERED', 'FILTERED']);
    });

    it('should delete a question after the instructor confirms', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const deleteSpy = vi.spyOn(poolService, 'deleteQuestion').mockReturnValue(of(undefined));
        const successSpy = vi.spyOn(alertService, 'success');
        const confirmationService = fixture.debugElement.injector.get(TumAetUiConfirmationService);

        component.confirmDeleteQuestion(2);
        expect(deleteSpy).not.toHaveBeenCalled();
        confirmationService.request(undefined)!.accept();

        expect(deleteSpy).toHaveBeenCalledWith(1, 2);
        expect(component.questions().map((question) => question.id)).toEqual([1, 3]);
        expect(component.totalQuestionCount()).toBe(2);
        expect(successSpy).toHaveBeenCalledWith('artemisApp.hyperion.quizQuestionPool.list.deleted');
    });

    it('should explain a refused deletion while a build runs', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        vi.spyOn(poolService, 'deleteQuestion').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
        const errorSpy = vi.spyOn(alertService, 'error');
        const confirmationService = fixture.debugElement.injector.get(TumAetUiConfirmationService);

        component.confirmDeleteQuestion(2);
        confirmationService.request(undefined)!.accept();

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.hyperion.quizQuestionPool.errors.buildRunning');
        expect(component.questions()).toHaveLength(3);
    });

    it('should delete the questions of the selected competency after the course title was typed', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true, title: 'Programming' });
        const deleteDialogService = TestBed.inject(DeleteDialogService);
        let dialogOptions: { entityTitle?: string; deleteQuestion?: string; translateValues: Record<string, unknown>; delete: unknown } | undefined;
        vi.spyOn(deleteDialogService, 'openDeleteDialog').mockImplementation((options) => {
            dialogOptions = options;
        });
        const deleteSpy = vi.spyOn(poolService, 'deleteQuestions').mockReturnValue(of(2));
        const successSpy = vi.spyOn(alertService, 'success');
        component.selectCompetency(9);

        fixture.detectChanges();
        fixture.nativeElement.querySelector('[data-testid="pool-delete-competency-9"]').click();

        expect(dialogOptions?.entityTitle).toBe('Programming');
        expect(dialogOptions?.deleteQuestion).toBe('artemisApp.hyperion.quizQuestionPool.deleteDialog.questionCompetency');
        expect(dialogOptions?.translateValues).toEqual({ competency: 'Arrays' });
        (dialogOptions!.delete as () => void)();

        expect(deleteSpy).toHaveBeenCalledWith(1, 9);
        expect(successSpy).toHaveBeenCalledWith('artemisApp.hyperion.quizQuestionPool.deleteDialog.deleted', { count: 2 });
        expect(component.selectedCompetencyId()).toBeUndefined();
        expect(poolService.getQuestions).toHaveBeenLastCalledWith(1, { flagged: true, page: 0, size: 20 });
        expect(component.questions()).toHaveLength(3);
    });

    it('should delete the whole pool from the title bar action with the pool total in the question', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true, title: 'Programming' });
        const deleteDialogService = TestBed.inject(DeleteDialogService);
        let dialogOptions: { deleteQuestion?: string; translateValues: Record<string, unknown>; delete: unknown } | undefined;
        vi.spyOn(deleteDialogService, 'openDeleteDialog').mockImplementation((options) => {
            dialogOptions = options;
        });
        const deleteSpy = vi.spyOn(poolService, 'deleteQuestions').mockReturnValue(of(10));

        component.confirmDeleteQuestions();
        (dialogOptions!.delete as () => void)();

        expect(dialogOptions?.deleteQuestion).toBe('artemisApp.hyperion.quizQuestionPool.deleteDialog.questionAll');
        expect(dialogOptions?.translateValues).toEqual({ count: 10 });
        expect(deleteSpy).toHaveBeenCalledWith(1, undefined);
    });

    it('should fall back to all competencies when the selected one has no questions left', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const remaining = [question(3, 10, 'Loops')];
        vi.spyOn(poolService, 'getQuestions').mockImplementation(pagesOf(remaining));

        component.selectCompetency(9);

        expect(component.selectedCompetencyId()).toBeUndefined();
        expect(component.questions().map((item) => item.id)).toEqual([3]);
    });

    it('should report a failed review decision', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: false });
        vi.spyOn(poolService, 'review').mockReturnValue(throwError(() => new Error('boom')));
        const errorSpy = vi.spyOn(alertService, 'error');

        component.review({ questionId: 1, reviewState: 'EXCLUDED' });

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.hyperion.quizQuestionPool.list.reviewFailed');
        expect(component.questions().find((q) => q.id === 1)?.reviewState).toBe('PENDING');
        expect(component.busyQuestionIds().has(1)).toBe(false);
    });

    it('should reload progress and questions on refresh', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });

        component.refresh();

        expect(poolService.getProgress).toHaveBeenCalledTimes(2);
        expect(listCalls(poolService.getQuestions as unknown as { mock: { calls: unknown[][] } })).toHaveLength(2);
    });
});
