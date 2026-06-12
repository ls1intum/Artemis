import { Injectable, inject, isDevMode } from '@angular/core';
import { Router } from '@angular/router';
import { HttpEvent, HttpHandler, HttpHeaders, HttpInterceptor, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';
import { MockDataService } from 'app/core/interceptor/mock-data.service';
import { getMockGroupChannel, getMockGroupPosts } from 'app/core/course/manage/exercises/mock/intro-to-programming-java-communication';
import {
    INTRO_JAVA_ALL_EXERCISES,
    INTRO_JAVA_FILE_UPLOAD_EXERCISES,
    INTRO_JAVA_MODELING_EXERCISES,
    INTRO_JAVA_PROGRAMMING_EXERCISES,
    INTRO_JAVA_QUIZ_EXERCISES,
    INTRO_JAVA_TEXT_EXERCISES,
} from 'app/core/course/manage/exercises/mock/intro-to-programming-java-exercises';
import {
    MOCK_COMPETENCY_PROGRESS,
    MOCK_COURSE_COMPETENCY_RESPONSES,
    getMockCompetencyContributions,
} from 'app/core/course/manage/exercises/mock/intro-to-programming-java-competencies';
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
    // The exercise detail view requests competency contributions per exercise. Mock exercise ids may
    // collide with real, inaccessible exercises in the dev DB, which would return 403 and surface a
    // "not authorized" alert. Return an empty list so the detail view stays quiet.
    { pattern: /^api\/atlas\/exercises\/\d+\/contributions$/, data: () => [] },
];

// The exercise detail view requests competency contributions per exercise (shown below the problem
// statement). Returns the mock competencies linked to that exercise, or an empty list otherwise — the
// latter also avoids a 403 "not authorized" alert when a mock id collides with a real exercise.
const COMPETENCY_CONTRIBUTIONS = /^api\/atlas\/exercises\/(\d+)\/contributions$/;

// The group's Communication panel uses the real discussion component, which loads a channel for the
// exercise and then its messages. Both are mocked so the panel shows a populated discussion thread.
const GROUP_CHANNEL = /^\/?api\/communication\/courses\/\d+\/exercises\/\d+\/channel$/;
const GROUP_MESSAGES = /^\/?api\/communication\/courses\/\d+\/messages$/;

// Competency endpoints used by both the instructor management page and the student overview/detail.
const COURSE_COMPETENCIES_LIST = /^api\/atlas\/courses\/\d+\/course-competencies$/;
const COURSE_COMPETENCIES_PROGRESS = /^api\/atlas\/courses\/\d+\/course-competencies\/course-progress$/;
const COURSE_COMPETENCY_DETAIL = /^api\/atlas\/courses\/\d+\/course-competencies\/(\d+)$/;

// The student exercise overview reads its exercises from the course returned by /for-dashboard,
// not from the per-type endpoints the instructor view uses. We let the real request through and
// graft the mock catalogue onto the course so the student view shows the same exercises.
const FOR_DASHBOARD = /^api\/course\/courses\/\d+\/for-dashboard$/;

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
        if (!isDevMode() || req.method !== 'GET' || !this.mockDataService.enabled()) {
            return next.handle(req);
        }

        if (FOR_DASHBOARD.test(req.url)) {
            return next.handle(req).pipe(map((event) => this.injectMockExercises(event)));
        }

        if (COURSE_COMPETENCIES_PROGRESS.test(req.url)) {
            return mockResponse(MOCK_COMPETENCY_PROGRESS);
        }

        const competencyDetailMatch = COURSE_COMPETENCY_DETAIL.exec(req.url);
        if (competencyDetailMatch) {
            const found = MOCK_COURSE_COMPETENCY_RESPONSES.find((c) => c.id === Number(competencyDetailMatch[1]));
            return mockResponse(found ?? null);
        }

        if (COURSE_COMPETENCIES_LIST.test(req.url)) {
            return mockResponse(MOCK_COURSE_COMPETENCY_RESPONSES);
        }

        const competencyMatch = COMPETENCY_CONTRIBUTIONS.exec(req.url);
        if (competencyMatch) {
            return mockResponse(getMockCompetencyContributions(Number(competencyMatch[1])));
        }

        if (GROUP_CHANNEL.test(req.url)) {
            return mockResponse(getMockGroupChannel());
        }

        if (GROUP_MESSAGES.test(req.url)) {
            const posts = getMockGroupPosts();
            return of(new HttpResponse({ status: 200, body: toJson(posts), headers: new HttpHeaders({ 'X-Total-Count': String(posts.length) }) }));
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

    private injectMockExercises(event: HttpEvent<unknown>): HttpEvent<unknown> {
        if (!(event instanceof HttpResponse) || !event.body) {
            return event;
        }
        const body = toJson(event.body) as { course?: { exercises?: unknown[]; numberOfCompetencies?: number } };
        if (body.course) {
            body.course.exercises = toJson(INTRO_JAVA_ALL_EXERCISES);
            body.course.numberOfCompetencies = MOCK_COURSE_COMPETENCY_RESPONSES.length;
        }
        return event.clone({ body });
    }
}
