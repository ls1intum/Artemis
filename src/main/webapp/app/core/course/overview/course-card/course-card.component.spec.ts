import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseCardComponent } from 'app/core/course/overview/course-card/course-card.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';
import { SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { ScoresStorageService } from 'app/core/course/manage/course-scores/scores-storage.service';
import { CourseScores } from 'app/core/course/manage/course-scores/course-scores';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';

describe('CourseCardComponent', () => {
    setupTestBed({ zoneless: true });

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
        const mockCourseScores: CourseScores = new CourseScores(0, 20, 0, { absoluteScore: 4, relativeScore: 0.3, currentRelativeScore: 0.2, presentationScore: 0 });
        vi.spyOn(scoresStorageService, 'getStoredTotalScores').mockReturnValue(mockCourseScores);

        fixture.detectChanges();
        TestBed.tick();

        expect(component.totalRelativeScore()).toBe(0.2);
        expect(component.totalAbsoluteScore()).toBe(4);
        expect(component.totalReachableScore()).toBe(20);
    });
});
