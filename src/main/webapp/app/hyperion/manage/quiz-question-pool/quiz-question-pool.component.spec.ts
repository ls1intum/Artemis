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

    const progress: PoolProgress = { running: false, total: 10, toGenerate: 0, toJudge: 0, judged: 9, accepted: 7, rejected: 2, failed: 1, modelConfigured: true };
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
            materialChanged: false,
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
        vi.spyOn(poolService, 'getPlan').mockReturnValue(of({ planned: 10, missing: 0 }));
        vi.spyOn(poolService, 'getLectureUnits').mockReturnValue(of([]));
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

    it('should drop the old list at once when the competency changes and not append to it while the new one loads', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const all = Array.from({ length: 60 }, (_, index) => question(index + 1, 9, 'Arrays'));
        const firstPage = new Subject<PoolQuestionPage>();
        const listSpy = vi.spyOn(poolService, 'getQuestions').mockImplementation((_courseId, query) => {
            if (query.flagged) {
                return of(page([]));
            }
            if (query.competencyId === 10) {
                return firstPage.asObservable();
            }
            return of(page(all.slice(query.page * query.size, (query.page + 1) * query.size), all.length));
        });
        component.refresh();
        component.loadMoreQuestions();
        expect(component.questions()).toHaveLength(40);

        component.selectCompetency(10);
        expect(component.questions()).toHaveLength(0);
        const callsBefore = listCalls(listSpy).length;
        component.loadMoreQuestions();
        expect(listCalls(listSpy)).toHaveLength(callsBefore);

        firstPage.next(
            page(
                all.slice(0, 20).map((item) => ({ ...item, competencyId: 10, competencyTitle: 'Loops' })),
                60,
            ),
        );
        firstPage.complete();
        component.loadMoreQuestions();

        expect(listCalls(listSpy).at(-1)).toEqual([1, { competencyId: 10, page: 1, size: 20 }]);
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

    it('should count the pending flagged questions of the whole queue, not only the loaded ones', () => {
        const loaded = Array.from({ length: 20 }, (_, index) => ({ ...question(index + 1, 9, 'Arrays'), flagged: true }));
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        vi.spyOn(poolService, 'getQuestions').mockImplementation((_courseId, query) => of(query.flagged ? { ...page(loaded, 40), pendingCount: 40 } : page(questions())));
        component.refresh();
        expect(component.pendingInQueue()).toBe(40);

        for (const item of loaded) {
            vi.spyOn(poolService, 'review').mockReturnValue(of({ ...item, reviewState: 'APPROVED' }));
            component.review({ questionId: item.id, reviewState: 'APPROVED' });
        }

        expect(component.pendingInQueue()).toBe(20);
        expect(component.allFlaggedReviewed()).toBe(false);
        expect(component.queueCollapsed()).toBe(false);
        expect(component.remainingNeedsReview()).toBe(20);
    });

    it('should reach the undecided questions behind a reviewed first page with Load more', () => {
        const all = Array.from({ length: 40 }, (_, index) => ({ ...question(index + 1, 9, 'Arrays'), flagged: true }));
        const decided = new Set<number>();
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const questionsSpy = vi.spyOn(poolService, 'getQuestions').mockImplementation((_courseId, query) => {
            if (!query.flagged) {
                return of(page(questions()));
            }
            const ordered = [...all.filter((item) => !decided.has(item.id)), ...all.filter((item) => decided.has(item.id))].map((item) =>
                decided.has(item.id) ? { ...item, reviewState: 'APPROVED' as const } : item,
            );
            return of({ ...page(ordered.slice(query.page * query.size, (query.page + 1) * query.size), 40), pendingCount: 40 - decided.size });
        });
        component.refresh();
        for (const item of component.needsReviewQuestions()) {
            decided.add(item.id);
            vi.spyOn(poolService, 'review').mockReturnValue(of({ ...item, reviewState: 'APPROVED' }));
            component.review({ questionId: item.id, reviewState: 'APPROVED' });
        }

        component.loadMoreNeedsReview();

        expect(questionsSpy).toHaveBeenLastCalledWith(1, { flagged: true, page: 0, size: 40 });
        expect(component.needsReviewQuestions()).toHaveLength(40);
        expect(component.needsReviewQuestions().filter((item) => item.reviewState === 'PENDING')).toHaveLength(20);
        expect(component.pendingInQueue()).toBe(20);
    });

    it('should reach unseen undecided questions past the largest reloadable range', () => {
        const all = Array.from({ length: 520 }, (_, index) => ({ ...question(index + 1, 9, 'Arrays'), flagged: true }));
        const decided = new Set<number>();
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const questionsSpy = vi.spyOn(poolService, 'getQuestions').mockImplementation((_courseId, query) => {
            if (!query.flagged) {
                return of(page(questions()));
            }
            const ordered = [...all.filter((item) => !decided.has(item.id)), ...all.filter((item) => decided.has(item.id))].map((item) =>
                decided.has(item.id) ? { ...item, reviewState: 'APPROVED' as const } : item,
            );
            return of({ ...page(ordered.slice(query.page * query.size, (query.page + 1) * query.size), 520), pendingCount: 520 - decided.size });
        });
        component.refresh();
        while (component.needsReviewQuestions().length < 500) {
            component.loadMoreNeedsReview();
        }
        for (const item of component.needsReviewQuestions().slice(0, 20)) {
            decided.add(item.id);
            vi.spyOn(poolService, 'review').mockReturnValue(of({ ...item, reviewState: 'APPROVED' }));
            component.review({ questionId: item.id, reviewState: 'APPROVED' });
        }

        component.loadMoreNeedsReview();

        expect(questionsSpy).toHaveBeenLastCalledWith(1, { flagged: true, page: 0, size: 500 });
        expect(component.needsReviewQuestions().every((item) => item.reviewState === 'PENDING')).toBe(true);
        expect(component.needsReviewQuestions().map((item) => item.id)).toContain(501);

        component.loadMoreNeedsReview();

        expect(questionsSpy).toHaveBeenLastCalledWith(1, { flagged: true, page: 25, size: 20 });
        expect(component.needsReviewQuestions()).toHaveLength(520);
        expect(
            component
                .needsReviewQuestions()
                .slice(500)
                .every((item) => item.reviewState === 'APPROVED'),
        ).toBe(true);
        expect(component.remainingNeedsReview()).toBe(0);
        expect(component.pendingInQueue()).toBe(500);
    });

    it('should reach the decided questions of a fully decided queue past the largest reloadable range', () => {
        const all = Array.from({ length: 520 }, (_, index) => ({ ...question(index + 1, 9, 'Arrays'), flagged: true, reviewState: 'APPROVED' as const }));
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const questionsSpy = vi
            .spyOn(poolService, 'getQuestions')
            .mockImplementation((_courseId, query) =>
                of(query.flagged ? { ...page(all.slice(query.page * query.size, (query.page + 1) * query.size), 520), pendingCount: 0 } : page(questions())),
            );
        component.refresh();
        while (component.needsReviewQuestions().length < 500) {
            component.loadMoreNeedsReview();
        }

        component.loadMoreNeedsReview();

        expect(questionsSpy).toHaveBeenLastCalledWith(1, { flagged: true, page: 25, size: 20 });
        expect(component.needsReviewQuestions()).toHaveLength(520);
        expect(component.remainingNeedsReview()).toBe(0);
    });

    it('should keep a decision taken while an appended queue page was loading in the pending count', () => {
        const all = Array.from({ length: 520 }, (_, index) => ({ ...question(index + 1, 9, 'Arrays'), flagged: true }));
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const appended = new Subject<PoolQuestionPage>();
        vi.spyOn(poolService, 'getQuestions').mockImplementation((_courseId, query) => {
            if (!query.flagged) {
                return of(page(questions()));
            }
            if (query.page === 25) {
                return appended.asObservable();
            }
            return of({ ...page(all.slice(query.page * query.size, (query.page + 1) * query.size), 520), pendingCount: 520 });
        });
        component.refresh();
        while (component.needsReviewQuestions().length < 500) {
            component.loadMoreNeedsReview();
        }
        component.loadMoreNeedsReview();
        vi.spyOn(poolService, 'review').mockReturnValue(of({ ...all[0], reviewState: 'APPROVED' }));
        component.review({ questionId: 1, reviewState: 'APPROVED' });
        expect(component.pendingInQueue()).toBe(519);

        appended.next({ ...page(all.slice(500), 520), pendingCount: 520 });
        appended.complete();

        expect(component.needsReviewQuestions()).toHaveLength(520);
        expect(component.pendingInQueue()).toBe(519);
    });

    it('should recount the queue when a flagged question outside the loaded queue is reviewed', () => {
        const queued = { ...question(1, 9, 'Arrays'), flagged: true, reviewState: 'APPROVED' as const };
        const unloaded = { ...question(21, 9, 'Arrays'), flagged: true, reviewState: 'APPROVED' as const };
        let pending = 0;
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const questionsSpy = vi
            .spyOn(poolService, 'getQuestions')
            .mockImplementation((_courseId, query) => of(query.flagged ? { ...page([queued], 2), pendingCount: pending } : page([queued, unloaded])));
        component.refresh();
        expect(component.allFlaggedReviewed()).toBe(true);
        const queueCallsBefore = questionsSpy.mock.calls.filter((call) => call[1].flagged).length;

        pending = 1;
        vi.spyOn(poolService, 'review').mockReturnValue(of({ ...unloaded, reviewState: 'PENDING' }));
        component.review({ questionId: 21, reviewState: 'PENDING' });

        expect(questionsSpy.mock.calls.filter((call) => call[1].flagged)).toHaveLength(queueCallsBefore + 1);
        expect(component.pendingInQueue()).toBe(1);
        expect(component.allFlaggedReviewed()).toBe(false);
    });

    it('should keep the newest review queue when an older queue response arrives later', () => {
        const flagged = { ...question(1, 9, 'Arrays'), flagged: true };
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const older = new Subject<PoolQuestionPage>();
        const newer = new Subject<PoolQuestionPage>();
        const queueResponses = [older, newer];
        vi.spyOn(poolService, 'getQuestions').mockImplementation((_courseId, query) => (query.flagged ? queueResponses.shift()!.asObservable() : of(page(questions()))));
        component.refresh();
        component.refresh();

        newer.next({ ...page([flagged]), pendingCount: 1 });
        newer.complete();
        older.next({ ...page([]), pendingCount: 0 });
        older.complete();

        expect(component.needsReviewQuestions().map((item) => item.id)).toEqual([1]);
        expect(component.pendingInQueue()).toBe(1);
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

    it('should keep polling until the final review queue arrives as well', () => {
        vi.useFakeTimers();
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        vi.spyOn(poolService, 'startBuild').mockReturnValue(of({ started: true, enqueued: 12 }));
        const progressSpy = vi.spyOn(poolService, 'getProgress').mockReturnValue(of({ ...progress, running: false }));
        let queueFailedOnce = false;
        vi.spyOn(poolService, 'getQuestions').mockImplementation((_courseId, query) => {
            if (query.flagged && !queueFailedOnce) {
                queueFailedOnce = true;
                return throwError(() => new Error('network'));
            }
            return of(page(questions()));
        });
        component.startBuild();
        const progressCallsAtStart = progressSpy.mock.calls.length;

        vi.advanceTimersByTime(3000);
        vi.advanceTimersByTime(3000);
        expect(progressSpy.mock.calls.length).toBe(progressCallsAtStart + 2);
        vi.advanceTimersByTime(6000);

        expect(progressSpy.mock.calls.length).toBe(progressCallsAtStart + 2);
    });

    it('should ignore a progress response requested before another build started', () => {
        vi.useFakeTimers();
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        vi.spyOn(poolService, 'startBuild').mockReturnValue(of({ started: true, enqueued: 3 }));
        const delayed = new Subject<PoolProgress>();
        const progressSpy = vi.spyOn(poolService, 'getProgress').mockReturnValue(delayed.asObservable());
        component.startBuild();
        vi.advanceTimersByTime(3000);

        component.startBuild();
        delayed.next({ ...progress, running: false });
        delayed.complete();

        expect(component.isRunning()).toBe(false);
        progressSpy.mockClear();
        progressSpy.mockReturnValue(of({ ...progress, running: true }));
        vi.advanceTimersByTime(3000);

        expect(progressSpy).toHaveBeenCalledOnce();
        expect(component.isRunning()).toBe(true);
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

    it('should measure a running build by its judged and failed items', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });

        component.progress.set({ ...progress, running: true, judged: 4, failed: 1 });
        expect(component.plannedCount()).toBe(10);
        expect(component.progressPercent()).toBe(50);
        component.progress.set({ ...progress, running: true, total: 0 });
        expect(component.progressPercent()).toBe(0);
    });

    it('should measure an idle pool against the questions it holds after the next build', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        component.plan.set({ planned: 40, missing: 30 });
        fixture.detectChanges();

        expect(component.plannedCount()).toBe(40);
        expect(component.progressPercent()).toBe(25);
        expect(component.missingInNextBuild()).toBe(30);
        expect(fixture.nativeElement.querySelector('[data-testid="pool-next-build-missing"]')).not.toBeNull();

        component.plan.set({ planned: 4, missing: 0 });
        expect(component.plannedCount()).toBe(10);
        expect(component.progressPercent()).toBe(100);

        component.plan.set({ planned: 40, missing: 30 });
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="pool-next-build-missing"]')).not.toBeNull();

        component.progress.set({ ...progress, running: true });
        fixture.detectChanges();

        expect(component.plannedCount()).toBe(10);
        expect(component.missingInNextBuild()).toBe(0);
        expect(fixture.nativeElement.querySelector('[data-testid="pool-next-build-missing"]')).toBeNull();
    });

    it('should reload the plan once a build ends and after settings are saved', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const planSpy = vi.spyOn(poolService, 'getPlan').mockReturnValue(of({ planned: 20, missing: 0 }));
        component.progress.set({ ...progress, running: true });
        vi.spyOn(poolService, 'getProgress').mockReturnValue(of({ ...progress, running: false }));

        component.refresh();

        expect(planSpy).toHaveBeenCalledWith(1);
        expect(component.plan()).toEqual({ planned: 20, missing: 0 });

        planSpy.mockClear();
        vi.spyOn(poolService, 'updateSettings').mockReturnValue(of(settings));
        component.saveSettings(settings);

        expect(planSpy).toHaveBeenCalledWith(1);
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

    it('should reload the progress instead of adding its own alert when the server has no model configured', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        vi.spyOn(poolService, 'startBuild').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
        const progressSpy = vi.spyOn(poolService, 'getProgress').mockReturnValue(of({ ...progress, modelConfigured: false }));
        const errorSpy = vi.spyOn(alertService, 'error');

        component.startBuild();
        fixture.detectChanges();

        expect(errorSpy).not.toHaveBeenCalled();
        expect(progressSpy).toHaveBeenCalledWith(1);
        expect(component.isModelMissing()).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="pool-model-missing"]')).not.toBeNull();
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

    it('should explain the missing local model and offer no build while the server has none configured', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        component.progress.set({ ...progress, total: 0, modelConfigured: false });
        fixture.detectChanges();

        expect(component.isModelMissing()).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="pool-model-missing"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="pool-progress-empty"] button').disabled).toBe(true);

        component.progress.set({ ...progress, total: 0 });
        fixture.detectChanges();

        expect(component.isModelMissing()).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="pool-model-missing"]')).toBeNull();
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
        expect(component.runningBuildFacts().map((fact) => fact.values['count'])).toEqual([10, 5, 12, undefined]);
        expect(component.settingsFacts().map((fact) => fact.changed)).toEqual([false, true, true, false, false]);
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

    it('should show the last build with its trigger, counts and failure', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        component.progress.set({
            ...progress,
            lastBuild: {
                id: 3,
                trigger: 'SCHEDULED',
                startedAt: '2026-11-02T02:00:00Z',
                finishedAt: '2026-11-02T02:10:00Z',
                enqueued: 12,
                generated: 14,
                judged: 12,
                accepted: 10,
                rejected: 2,
                failed: 0,
                failure: 'model unavailable',
            },
        });
        fixture.detectChanges();

        expect(component.triggerLabelKey('SCHEDULED')).toBe('artemisApp.hyperion.quizQuestionPool.progress.trigger.SCHEDULED');
        expect(fixture.nativeElement.querySelector('[data-testid="pool-last-build"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="pool-last-build-failure"]')).not.toBeNull();
    });

    it('should list the lecture units per competency and exclude one only after confirmation', () => {
        const units = [
            {
                competencyId: 9,
                competencyTitle: 'Arrays',
                lectureUnitId: 4,
                lectureUnitName: 'Arrays in Java',
                excluded: false,
                materialChanged: true,
                questionCount: 12,
                approvedCount: 3,
            },
            {
                competencyId: 9,
                competencyTitle: 'Arrays',
                lectureUnitId: 6,
                lectureUnitName: 'Arrays, continued',
                excluded: true,
                materialChanged: false,
                questionCount: 0,
                approvedCount: 0,
            },
            {
                competencyId: 10,
                competencyTitle: 'Loops',
                lectureUnitId: 4,
                lectureUnitName: 'Arrays in Java',
                excluded: false,
                materialChanged: true,
                questionCount: 12,
                approvedCount: 3,
            },
        ];
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        vi.spyOn(poolService, 'getLectureUnits').mockReturnValue(of(units));
        const relevanceSpy = vi.spyOn(poolService, 'updateLectureUnitRelevance').mockReturnValue(of(true));
        const confirmationService = fixture.debugElement.injector.get(TumAetUiConfirmationService);
        component.refresh();
        fixture.detectChanges();

        expect(component.lectureUnitGroups().map((group) => [group.competencyId, group.units.length])).toEqual([
            [9, 2],
            [10, 1],
        ]);
        expect(component.excludedUnitCount()).toBe(1);
        expect(component.changedUnitCount()).toBe(1);

        component.toggleLectureUnit(units[0]);
        expect(relevanceSpy).not.toHaveBeenCalled();
        confirmationService.request(undefined)!.accept();
        expect(relevanceSpy).toHaveBeenCalledWith(1, 4, true);

        component.toggleLectureUnit(units[1]);
        expect(relevanceSpy).toHaveBeenLastCalledWith(1, 6, false);
    });

    it('should explain a refused exclusion while a build runs', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        vi.spyOn(poolService, 'updateLectureUnitRelevance').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
        const errorSpy = vi.spyOn(alertService, 'error');
        const confirmationService = fixture.debugElement.injector.get(TumAetUiConfirmationService);

        component.toggleLectureUnit({
            competencyId: 9,
            competencyTitle: 'Arrays',
            lectureUnitId: 4,
            lectureUnitName: 'Arrays in Java',
            excluded: false,
            materialChanged: false,
            questionCount: 1,
            approvedCount: 0,
        });
        confirmationService.request(undefined)!.accept();

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.hyperion.quizQuestionPool.errors.buildRunning');
        expect(component.busyLectureUnitIds().size).toBe(0);
    });

    it('should fold and unfold the lecture units from anywhere in their header row', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const header = document.createElement('div');
        header.className = 'tumaet-ui-panel-header';
        const title = document.createElement('span');
        header.append(title);

        expect(component.unitsCollapsed()).toBe(true);
        component.toggleUnitsFromHeader({ target: title } as unknown as MouseEvent);
        expect(component.unitsCollapsed()).toBe(false);
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

    it('should not let a list response requested before a deletion bring the deleted question back', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const pendingList = new Subject<PoolQuestionPage>();
        const pendingQueue = new Subject<PoolQuestionPage>();
        const flaggedTwo = { ...question(2, 9, 'Arrays'), flagged: true };
        vi.spyOn(poolService, 'getQuestions').mockImplementation((_courseId, query) => (query.flagged ? pendingQueue.asObservable() : pendingList.asObservable()));
        component.refresh();
        vi.spyOn(poolService, 'deleteQuestion').mockReturnValue(of(undefined));
        const confirmationService = fixture.debugElement.injector.get(TumAetUiConfirmationService);

        component.confirmDeleteQuestion(2);
        confirmationService.request(undefined)!.accept();
        pendingList.next(page(questions()));
        pendingQueue.next({ ...page([flaggedTwo]), pendingCount: 1 });

        expect(component.questions().map((item) => item.id)).not.toContain(2);
        expect(component.needsReviewQuestions().map((item) => item.id)).not.toContain(2);
    });

    it('should refill the review queue when its last loaded question is deleted and more remain', () => {
        const flagged = Array.from({ length: 40 }, (_, index) => ({ ...question(index + 1, 9, 'Arrays'), flagged: true }));
        let remaining = flagged;
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        vi.spyOn(poolService, 'getQuestions').mockImplementation((_courseId, query) =>
            of(query.flagged ? { ...page(remaining.slice(0, query.size), remaining.length), pendingCount: remaining.length } : page(questions())),
        );
        vi.spyOn(poolService, 'deleteQuestion').mockReturnValue(of(undefined));
        const confirmationService = fixture.debugElement.injector.get(TumAetUiConfirmationService);
        component.refresh();
        expect(component.needsReviewQuestions()).toHaveLength(20);

        for (const item of flagged.slice(0, 20)) {
            remaining = remaining.filter((candidate) => candidate.id !== item.id);
            component.confirmDeleteQuestion(item.id);
            confirmationService.request(undefined)!.accept();
        }

        expect(component.needsReviewQuestions().map((item) => item.id)).toEqual(flagged.slice(20).map((item) => item.id));
        expect(component.showNeedsReviewQueue()).toBe(true);
        expect(component.pendingInQueue()).toBe(20);
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
