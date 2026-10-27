import { afterEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Observable, Subject, of, throwError } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Course } from 'app/course/shared/entities/course.model';
import { QuizQuestionPoolComponent } from './quiz-question-pool.component';
import { QuizQuestionPoolService } from './quiz-question-pool.service';
import { PoolProgress, PoolQuestion, PoolSettings } from './quiz-question-pool.types';

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
            question: { id: `pool-${id}`, type: 'single-choice', title: `Q${id}`, questionText: 'Q?', options: [{ text: 'A', correct: true }] },
            decision: { accepted: true, aggregateScore: 1, verdicts: [], rationale: 'ok' },
            updatedAt: '2026-10-13T10:00:00Z',
        };
    }

    const questions = (): PoolQuestion[] => [question(1, 9, 'Arrays'), question(2, 9, 'Arrays'), question(3, 10, 'Loops')];

    function setup(course: Partial<Course>, params: Observable<Record<string, string>> = of({ courseId: '1' })): void {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: { params } },
            ],
        });
        poolService = TestBed.inject(QuizQuestionPoolService);
        courseManagementService = TestBed.inject(CourseManagementService);
        alertService = TestBed.inject(AlertService);
        vi.spyOn(courseManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: { id: 1, ...course } as Course })));
        vi.spyOn(poolService, 'getProgress').mockReturnValue(of(progress));
        vi.spyOn(poolService, 'getQuestions').mockReturnValue(of(questions()));
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
        expect(component.groups().map((group) => group.id)).toEqual([10]);

        component.selectCompetency(null);
        expect(component.groups()).toHaveLength(2);
    });

    it('should drop the previous course and its polling when the route moves to another course', () => {
        vi.useFakeTimers();
        const params = new Subject<Record<string, string>>();
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true }, params.asObservable());
        const progressSpy = vi.spyOn(poolService, 'getProgress').mockReturnValue(of({ ...progress, running: true }));
        params.next({ courseId: '1' });
        component.selectCompetency(9);
        component.setSingleChoiceQuestionsPerLectureUnit(30);
        expect(component.courseId()).toBe(1);
        expect(component.questions()).toHaveLength(3);
        expect(component.isRunning()).toBe(true);
        const pollsOfFirstCourse = progressSpy.mock.calls.filter((call) => call[0] === 1).length;

        progressSpy.mockReturnValue(of({ ...progress, running: false }));
        vi.spyOn(poolService, 'getQuestions').mockReturnValue(of([]));
        params.next({ courseId: '2' });
        vi.advanceTimersByTime(6000);

        expect(component.courseId()).toBe(2);
        expect(component.questions()).toEqual([]);
        expect(component.selectedCompetencyId()).toBeUndefined();
        expect(component.isSettingsDirty()).toBe(false);
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
        component.setSingleChoiceQuestionsPerLectureUnit(30);
        component.saveSettings();

        params.next({ courseId: '2' });
        const pollsOfFirstCourse = progressSpy.mock.calls.filter((call) => call[0] === 1).length;
        lateStart.next({ started: true, enqueued: 12 });
        lateStart.complete();
        lateSave.next({ ...settings, poolGenerationEnabled: true, singleChoiceQuestionsPerLectureUnit: 30 });
        lateSave.complete();
        vi.advanceTimersByTime(6000);

        expect(progressSpy.mock.calls.filter((call) => call[0] === 1)).toHaveLength(pollsOfFirstCourse);
        expect(component.settings()).toEqual(settings);
        expect(component.isSettingsDirty()).toBe(false);
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

        expect(successSpy).toHaveBeenCalledWith('artemisApp.hyperion.quizQuestionPool.progress.started', { count: 12 });
        expect(component['pollingSubscription']).toBeDefined();

        vi.advanceTimersByTime(3000);
        expect(component.progress()?.running).toBe(true);
        expect(component['pollingSubscription']).toBeDefined();

        vi.advanceTimersByTime(3000);
        expect(component.progress()?.running).toBe(false);
        expect(component['pollingSubscription']).toBeUndefined();
    });

    it('should warn when a build is already running', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        vi.spyOn(poolService, 'startBuild').mockReturnValue(of({ started: false, enqueued: 0 }));
        const warningSpy = vi.spyOn(alertService, 'warning');

        component.startBuild();

        expect(warningSpy).toHaveBeenCalledWith('artemisApp.hyperion.quizQuestionPool.progress.alreadyRunning');
        component.ngOnDestroy();
        expect(component['pollingSubscription']).toBeUndefined();
    });

    it('should report a failed build start', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        vi.spyOn(poolService, 'startBuild').mockReturnValue(throwError(() => new Error('boom')));
        const errorSpy = vi.spyOn(alertService, 'error');

        component.startBuild();

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.hyperion.quizQuestionPool.progress.buildFailed');
        expect(component.isStartingBuild()).toBe(false);
    });

    it('should track dirty settings and save them', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const changed = { ...settings, poolGenerationEnabled: true, singleChoiceQuestionsPerLectureUnit: 30 };
        const updateSpy = vi.spyOn(poolService, 'updateSettings').mockReturnValue(of(changed));

        expect(component.isSettingsDirty()).toBe(false);
        expect(component.canDeactivate()).toBe(true);

        component.setSingleChoiceQuestionsPerLectureUnit(30);
        component.setPoolGenerationEnabled(true);
        expect(component.isSettingsDirty()).toBe(true);
        expect(component.canDeactivate()).toBe(false);

        component.saveSettings();

        expect(updateSpy).toHaveBeenCalledWith(1, changed);
        expect(component.isSettingsDirty()).toBe(false);
        expect(component.canDeactivate()).toBe(true);
    });

    it('should lock the settings controls while a save is pending', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const pending = new Subject<PoolSettings>();
        vi.spyOn(poolService, 'updateSettings').mockReturnValue(pending.asObservable());
        component.setSingleChoiceQuestionsPerLectureUnit(30);
        component.saveSettings();
        fixture.detectChanges();

        const lockedInput = fixture.nativeElement.querySelector('#pool-single-choice-questions') as HTMLInputElement;
        const lockedToggle = fixture.nativeElement.querySelector('#pool-generation-enabled') as HTMLInputElement;
        expect({
            saving: component.isSavingSettings(),
            input: lockedInput?.disabled,
            toggle: lockedToggle?.disabled,
            inputTag: lockedInput?.tagName,
            toggleTag: lockedToggle?.tagName,
        }).toEqual({
            saving: true,
            input: true,
            toggle: true,
            inputTag: 'INPUT',
            toggleTag: 'INPUT',
        });

        pending.next({ ...settings, singleChoiceQuestionsPerLectureUnit: 30 });
        pending.complete();
        fixture.detectChanges();

        expect(component.isSavingSettings()).toBe(false);
        expect((fixture.nativeElement.querySelector('#pool-single-choice-questions') as HTMLInputElement).disabled).toBe(false);
    });

    it('should refuse to save settings outside the allowed range', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const updateSpy = vi.spyOn(poolService, 'updateSettings');

        component.setSingleChoiceQuestionsPerLectureUnit(0);
        expect(component.isSettingsValid()).toBe(false);
        component.saveSettings();

        expect(updateSpy).not.toHaveBeenCalled();
    });

    it('should show the multiple-choice count only while multiple-choice questions are enabled', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        expect(fixture.nativeElement.querySelector('#pool-multiple-choice-questions')).toBeNull();

        component.setMultipleChoiceEnabled(true);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('#pool-multiple-choice-questions')).not.toBeNull();
        component.setMultipleChoiceQuestionsPerLectureUnit(61);
        expect(component.isSettingsValid()).toBe(false);
        component.setMultipleChoiceQuestionsPerLectureUnit(5);
        expect(component.isSettingsValid()).toBe(true);
    });

    it('should refuse a difficulty mix that does not add up to 100 percent', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        expect(fixture.nativeElement.querySelector('[data-testid="pool-difficulty-mix-error"]')).toBeNull();

        component.setEasyPercent(30);
        fixture.detectChanges();

        expect(component.difficultyPercentSum()).toBe(110);
        expect(component.isSettingsValid()).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="pool-difficulty-mix-error"]')).not.toBeNull();

        component.setHardPercent(20);
        fixture.detectChanges();

        expect(component.isSettingsValid()).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="pool-difficulty-mix-error"]')).toBeNull();
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
        const staleList = new Subject<PoolQuestion[]>();
        vi.spyOn(poolService, 'getQuestions').mockReturnValue(staleList.asObservable());
        component.refresh();
        const reviewed = { ...questions()[0], reviewState: 'APPROVED' as const };
        vi.spyOn(poolService, 'review').mockReturnValue(of(reviewed));
        component.review({ questionId: 1, reviewState: 'APPROVED' });
        expect(component.questions().find((question) => question.id === 1)?.reviewState).toBe('APPROVED');

        staleList.next(questions());
        staleList.complete();

        expect(component.questions().find((question) => question.id === 1)?.reviewState).toBe('APPROVED');
        expect(component.questions().find((question) => question.id === 2)?.reviewState).toBe('PENDING');
    });

    it('should keep the newest question list when an older request answers later', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const first = new Subject<PoolQuestion[]>();
        const second = new Subject<PoolQuestion[]>();
        vi.spyOn(poolService, 'getQuestions').mockReturnValueOnce(first.asObservable()).mockReturnValueOnce(second.asObservable());
        component.refresh();
        component.refresh();
        const judged = questions();
        const generating = judged.map((question) => ({ ...question, state: 'GENERATING' as const, question: undefined, decision: undefined }));

        second.next(judged);
        second.complete();
        first.next(generating);
        first.complete();

        expect(component.questions().map((question) => question.state)).toEqual(['FILTERED', 'FILTERED', 'FILTERED']);
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
        expect(poolService.getQuestions).toHaveBeenCalledTimes(2);
    });
});
