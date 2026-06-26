import { Injectable, inject, isDevMode } from '@angular/core';
import { Router } from '@angular/router';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { MockDataService } from 'app/core/interceptor/mock-data.service';
import {
    INTRO_JAVA_ALL_EXERCISES,
    INTRO_JAVA_FILE_UPLOAD_EXERCISES,
    INTRO_JAVA_MODELING_EXERCISES,
    INTRO_JAVA_PROGRAMMING_EXERCISES,
    INTRO_JAVA_QUIZ_EXERCISES,
    INTRO_JAVA_TEXT_EXERCISES,
} from 'app/core/course/manage/exercises/mock/intro-to-programming-java-exercises';
import { getMockCompetencyContributions } from 'app/core/course/manage/exercises/mock/intro-to-programming-java-competencies';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';

function toJson<T>(value: T): T {
    return JSON.parse(JSON.stringify(value));
}

function mockResponse<T>(body: T): Observable<HttpEvent<T>> {
    return of(new HttpResponse<T>({ status: 200, body: toJson(body) }));
}

function extractId(url: string): number {
    return parseInt(url.match(/\/(\d+)/)?.[1] ?? '', 10);
}

/** Wraps a list of mock exercises in the {@link SearchResult} shape the import dialog's paging services expect. */
function mockSearchResult<T>(results: T[]): { resultsOnPage: T[]; numberOfPages: number } {
    return { resultsOnPage: results, numberOfPages: 1 };
}

function mockStats(): ExerciseManagementStatisticsDto {
    return {
        averageScoreOfExercise: 0,
        maxPointsOfExercise: 0,
        scoreDistribution: [],
        numberOfExerciseScores: 0,
        numberOfParticipations: 0,
        numberOfStudentsOrTeamsInCourse: 0,
        numberOfPosts: 0,
        numberOfResolvedPosts: 0,
    };
}

const EXERCISE_ROUTES: Array<{ pattern: RegExp; data: (url: string) => unknown }> = [
    // Exercise lists
    { pattern: /^api\/programming\/courses\/\d+\/programming-exercises$/, data: () => INTRO_JAVA_PROGRAMMING_EXERCISES },
    { pattern: /^api\/modeling\/courses\/\d+\/modeling-exercises$/, data: () => INTRO_JAVA_MODELING_EXERCISES },
    { pattern: /^api\/text\/courses\/\d+\/text-exercises$/, data: () => INTRO_JAVA_TEXT_EXERCISES },
    { pattern: /^api\/fileupload\/courses\/\d+\/file-upload-exercises$/, data: () => INTRO_JAVA_FILE_UPLOAD_EXERCISES },
    { pattern: /^api\/quiz\/courses\/\d+\/quiz-exercises$/, data: () => INTRO_JAVA_QUIZ_EXERCISES },
    // Import-dialog paging search (the develop import dialog calls these and expects a SearchResult). Query params
    // (page, search term, filters) live in HttpParams, so req.url is the bare resource URL with no trailing id.
    { pattern: /^api\/programming\/programming-exercises$/, data: () => mockSearchResult(INTRO_JAVA_PROGRAMMING_EXERCISES) },
    { pattern: /^api\/modeling\/modeling-exercises$/, data: () => mockSearchResult(INTRO_JAVA_MODELING_EXERCISES) },
    { pattern: /^api\/text\/text-exercises$/, data: () => mockSearchResult(INTRO_JAVA_TEXT_EXERCISES) },
    { pattern: /^api\/fileupload\/file-upload-exercises$/, data: () => mockSearchResult(INTRO_JAVA_FILE_UPLOAD_EXERCISES) },
    { pattern: /^api\/quiz\/quiz-exercises$/, data: () => mockSearchResult(INTRO_JAVA_QUIZ_EXERCISES) },
    // Individual exercise fetches — exact id match, no sub-path
    { pattern: /^api\/programming\/programming-exercises\/\d+$/, data: (url) => INTRO_JAVA_PROGRAMMING_EXERCISES.find((e) => e.id === extractId(url)) ?? null },
    { pattern: /^api\/modeling\/modeling-exercises\/\d+$/, data: (url) => INTRO_JAVA_MODELING_EXERCISES.find((e) => e.id === extractId(url)) ?? null },
    { pattern: /^api\/text\/text-exercises\/\d+$/, data: (url) => INTRO_JAVA_TEXT_EXERCISES.find((e) => e.id === extractId(url)) ?? null },
    { pattern: /^api\/fileupload\/file-upload-exercises\/\d+$/, data: (url) => INTRO_JAVA_FILE_UPLOAD_EXERCISES.find((e) => e.id === extractId(url)) ?? null },
    { pattern: /^api\/quiz\/quiz-exercises\/\d+$/, data: (url) => INTRO_JAVA_QUIZ_EXERCISES.find((e) => e.id === extractId(url)) ?? null },
    // Programming exercise sub-resources needed by the detail view
    {
        pattern: /^api\/programming\/programming-exercises\/\d+\/with-template-and-solution-participation$/,
        data: (url) => INTRO_JAVA_PROGRAMMING_EXERCISES.find((e) => e.id === extractId(url)) ?? null,
    },
    {
        // submission-policy: return null body — component handles undefined gracefully
        pattern: /^api\/programming\/programming-exercises\/\d+\/submission-policy$/,
        data: () => null,
    },
    {
        // consistency check: return empty array (no errors)
        pattern: /^api\/exercise\/programming-exercises\/\d+\/consistency-check$/,
        data: () => [],
    },
    {
        // template/solution repository file contents — query param is part of req.url string
        pattern: /^api\/programming\/programming-exercises\/\d+\/template-files-content/,
        data: () => ({}),
    },
    {
        pattern: /^api\/programming\/programming-exercises\/\d+\/solution-files-content/,
        data: () => ({}),
    },
    {
        // grading test cases — return empty array
        pattern: /^api\/programming\/programming-exercises\/\d+\/test-cases/,
        data: () => [],
    },
    {
        // participation latest-pending-submission — participation id may be numeric or "undefined"
        pattern: /^api\/programming\/programming-exercise-participations\/[^/]+\/latest-pending-submission/,
        data: () => null,
    },
    // Exercise statistics used by all detail views (exerciseId is a query param, not in the path)
    { pattern: /^api\/core\/management\/statistics\/exercise-statistics$/, data: () => mockStats() },
];

// Quiz lifecycle mutations are PUTs (not GETs), so they bypass the read-only mock routes above. The reused
// QuizExerciseLifecycleButtonsComponent calls these and applies the response optimistically, so we return
// plausible bodies to keep the buttons working in mock/demo mode without a backend.
const QUIZ_DATES_MUTATION = /^api\/quiz\/quiz-exercises\/\d+\/(start-now|end-now|set-visible)$/;
const QUIZ_ADD_BATCH = /^api\/quiz\/quiz-exercises\/\d+\/add-batch$/;
const QUIZ_START_BATCH = /^api\/quiz\/quiz-batches\/\d+\/start-batch$/;

function mockQuizDates(): { releaseDate: string; startDate: string; dueDate: string } {
    const now = new Date();
    const due = new Date(now.getTime() + 60 * 60 * 1000);
    return { releaseDate: now.toISOString(), startDate: now.toISOString(), dueDate: due.toISOString() };
}

function mockQuizBatch(): { id: number; started: boolean; password: string } {
    return { id: Math.floor(Math.random() * 1_000_000), started: false, password: Math.random().toString(36).slice(2, 8).toUpperCase() };
}

// The exercise detail view requests competency contributions per exercise (shown below the problem
// statement). Returns the mock competencies linked to that exercise, or an empty list otherwise — the
// latter also avoids a 403 "not authorized" alert when a mock id collides with a real exercise.
const COMPETENCY_CONTRIBUTIONS = /^api\/atlas\/exercises\/(\d+)\/contributions$/;

// Clicking a mock exercise opens its detail (problem statement etc.) which is loaded from this
// endpoint. The matching exercise from the mock catalogue is returned instead of hitting the backend.
const EXERCISE_DETAILS = /^api\/exercise\/exercises\/(\d+)\/details$/;

// Lightweight course reference attached to a mock exercise detail. Intentionally omits `exercises`
// to avoid a circular structure (course -> exercises -> exercise -> course) when serialising.
function mockCourseRef(courseId: number | undefined): { id?: number; title: string; shortName: string } {
    return { id: courseId, title: 'Introduction to Programming in Java', shortName: 'INTRO_JAVA' };
}

@Injectable()
export class MockCourseInterceptor implements HttpInterceptor {
    private readonly router = inject(Router);
    private readonly mockDataService = inject(MockDataService);

    intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
        if (!isDevMode() || !this.mockDataService.enabled()) {
            return next.handle(req);
        }

        // Quiz lifecycle mutations (PUT) — mocked so the reused lifecycle buttons work in demo mode.
        if (req.method === 'PUT') {
            if (QUIZ_DATES_MUTATION.test(req.url)) {
                return mockResponse(mockQuizDates());
            }
            if (QUIZ_ADD_BATCH.test(req.url)) {
                return mockResponse(mockQuizBatch());
            }
            if (QUIZ_START_BATCH.test(req.url)) {
                return mockResponse(null);
            }
        }

        if (req.method !== 'GET') {
            return next.handle(req);
        }

        const competencyMatch = COMPETENCY_CONTRIBUTIONS.exec(req.url);
        if (competencyMatch) {
            return mockResponse(getMockCompetencyContributions(Number(competencyMatch[1])));
        }

        const detailsMatch = EXERCISE_DETAILS.exec(req.url);
        if (detailsMatch) {
            const exercise = INTRO_JAVA_ALL_EXERCISES.find((candidate) => candidate.id === Number(detailsMatch[1]));
            if (exercise) {
                const exerciseWithCourse = toJson(exercise) as { course?: unknown };
                exerciseWithCourse.course = mockCourseRef(this.courseIdFromUrl());
                return mockResponse({ exercise: exerciseWithCourse });
            }
        }

        const match = EXERCISE_ROUTES.find(({ pattern }) => pattern.test(req.url));
        if (match) {
            return mockResponse(match.data(req.url));
        }

        return next.handle(req);
    }

    private courseIdFromUrl(): number | undefined {
        const match = /\/courses\/(\d+)\//.exec(this.router.url);
        return match ? Number(match[1]) : undefined;
    }
}
