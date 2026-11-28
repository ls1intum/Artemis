import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseCardComponent } from 'app/course/overview/course-card/course-card.component';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';
import { ScoresStorageService } from 'app/course/manage/course-scores/scores-storage.service';
import { CourseScores } from 'app/course/manage/course-scores/course-scores';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { TumAetUiChartTooltipConfig } from '@tumaet/ui-angular/chart';
import { SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission-exercise-type.model';

describe('CourseCardComponent', () => {
    let fixture: ComponentFixture<CourseCardComponent>;
    let component: CourseCardComponent;
    let scoresStorageService: ScoresStorageService;
    const submission: ProgrammingSubmission = {
        submissionExerciseType: SubmissionExerciseType.PROGRAMMING,
        id: 3,
        submitted: true,
        results: [{ successful: true }],
    };
    const pastExercise = { id: 1, dueDate: dayjs().subtract(2, 'days') } as Exercise;
    const nextExercise = { id: 2, dueDate: dayjs().add(2, 'days'), studentParticipations: [{ submissions: [submission] }] } as Exercise;
    const secondNextExercise = { id: 3, dueDate: dayjs().add(4, 'days') } as Exercise;
    const course = { id: 1, exercises: [pastExercise, nextExercise, secondNextExercise], lectures: [], exams: [] } as Course;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseCardComponent);
        component = fixture.componentInstance;
        scoresStorageService = TestBed.inject(ScoresStorageService);
        fixture.componentRef.setInput('course', course);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize component', () => {
        fixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should display the next exercise which is not yet successful', () => {
        fixture.detectChanges();
        TestBed.tick();
        expect(component.nextRelevantExercise()).toEqual(secondNextExercise);
    });

    it('should display the total course scores returned from the scores storage service', () => {
        const mockCourseScores: CourseScores = new CourseScores(20, 10, 0, {
            absoluteScore: 4,
            absoluteScoreTotal: 4,
            relativeScore: 0.3,
            currentRelativeScore: 0.2,
            presentationScore: 0,
        });
        vi.spyOn(scoresStorageService, 'getStoredTotalScores').mockReturnValue(mockCourseScores);

        fixture.detectChanges();
        TestBed.tick();

        expect(component.totalRelativeScore()).toBe(0.3);
        expect(component.totalAbsoluteScore()).toBe(4);
        expect(component.totalMaxPoints()).toBe(20);
    });

    it('uses the full course maximum and relative score when a future manual exercise is not yet reachable', () => {
        const completedExercise = {
            id: 10,
            type: ExerciseType.PROGRAMMING,
            maxPoints: 10,
            includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
        } as Exercise;
        const futureManualExercise = {
            id: 11,
            type: ExerciseType.TEXT,
            maxPoints: 10,
            dueDate: dayjs().add(2, 'days'),
            includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
            studentParticipations: [],
        } as Exercise;
        fixture.componentRef.setInput('course', { ...course, exercises: [completedExercise, futureManualExercise] } as Course);
        vi.spyOn(scoresStorageService, 'getStoredTotalScores').mockReturnValue(
            new CourseScores(20, 10, 0, {
                absoluteScore: 10,
                absoluteScoreTotal: 10,
                relativeScore: 50,
                currentRelativeScore: 100,
                presentationScore: 0,
            }),
        );

        fixture.detectChanges();
        TestBed.tick();

        expect(component.totalRelativeScore()).toBe(50);
        expect(component.totalAbsoluteScore()).toBe(10);
        expect(component.totalMaxPoints()).toBe(20);
        expect(component.doughnutChartEntries().map((entry) => entry.value)).toEqual([10, 10]);
    });

    it('should show the translated chart label as tooltip title and the value as body', () => {
        const tooltip = component.chartConfig().tooltip as TumAetUiChartTooltipConfig;
        const datum = { seriesIndex: 0, index: 0, label: 'missingPointsLabel', value: 400 };

        expect(tooltip.title!([datum])).toBe('artemisApp.courseOverview.statistics.missingPointsLabel');
        expect(tooltip.label!(datum)).toBe('400');
    });
});
