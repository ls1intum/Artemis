import { afterEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
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
    const settings: PoolSettings = { poolGenerationEnabled: false, candidatesPerCompetency: 20 };

    function question(id: number, competencyId: number, title: string): PoolQuestion {
        return {
            id,
            competencyId,
            competencyTitle: title,
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

    function setup(course: Partial<Course>): void {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: { params: of({ courseId: '1' }) } },
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

    it('should group questions by competency and filter by the selected one', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });

        expect(component.competencyOptions()).toEqual([
            { id: 9, title: 'Arrays' },
            { id: 10, title: 'Loops' },
        ]);
        expect(component.groups().map((group) => [group.id, group.questions.length])).toEqual([
            [9, 2],
            [10, 1],
        ]);

        component.selectCompetency(10);
        expect(component.groups().map((group) => group.id)).toEqual([10]);

        component.selectCompetency(null);
        expect(component.groups()).toHaveLength(2);
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
        const updateSpy = vi.spyOn(poolService, 'updateSettings').mockReturnValue(of({ poolGenerationEnabled: true, candidatesPerCompetency: 30 }));

        expect(component.isSettingsDirty()).toBe(false);
        expect(component.canDeactivate()).toBe(true);

        component.setCandidatesPerCompetency(30);
        component.setPoolGenerationEnabled(true);
        expect(component.isSettingsDirty()).toBe(true);
        expect(component.canDeactivate()).toBe(false);

        component.saveSettings();

        expect(updateSpy).toHaveBeenCalledWith(1, { poolGenerationEnabled: true, candidatesPerCompetency: 30 });
        expect(component.isSettingsDirty()).toBe(false);
        expect(component.canDeactivate()).toBe(true);
    });

    it('should refuse to save settings outside the allowed range', () => {
        setup({ isAtLeastEditor: true, isAtLeastInstructor: true });
        const updateSpy = vi.spyOn(poolService, 'updateSettings');

        component.setCandidatesPerCompetency(0);
        expect(component.isSettingsValid()).toBe(false);
        component.saveSettings();

        expect(updateSpy).not.toHaveBeenCalled();
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
