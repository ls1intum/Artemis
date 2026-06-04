import { Injectable, inject, isDevMode } from '@angular/core';
import { Router } from '@angular/router';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';
import {
    INTRO_JAVA_ALL_EXERCISES,
    INTRO_JAVA_FILE_UPLOAD_EXERCISES,
    INTRO_JAVA_MODELING_EXERCISES,
    INTRO_JAVA_PROGRAMMING_EXERCISES,
    INTRO_JAVA_QUIZ_EXERCISES,
    INTRO_JAVA_TEXT_EXERCISES,
} from 'app/core/course/manage/exercises/mock/intro-to-programming-java-exercises';

function toJson<T>(value: T): T {
    return JSON.parse(JSON.stringify(value));
}

function mockResponse<T>(body: T): Observable<HttpEvent<T>> {
    return of(new HttpResponse<T>({ status: 200, body: toJson(body) }));
}

const EXERCISE_ROUTES: Array<{ pattern: RegExp; data: () => unknown }> = [
    { pattern: /^api\/programming\/courses\/\d+\/programming-exercises$/, data: () => INTRO_JAVA_PROGRAMMING_EXERCISES },
    { pattern: /^api\/modeling\/courses\/\d+\/modeling-exercises$/, data: () => INTRO_JAVA_MODELING_EXERCISES },
    { pattern: /^api\/text\/courses\/\d+\/text-exercises$/, data: () => INTRO_JAVA_TEXT_EXERCISES },
    { pattern: /^api\/fileupload\/courses\/\d+\/file-upload-exercises$/, data: () => INTRO_JAVA_FILE_UPLOAD_EXERCISES },
    { pattern: /^api\/quiz\/courses\/\d+\/quiz-exercises$/, data: () => INTRO_JAVA_QUIZ_EXERCISES },
    // The exercise detail view requests competency contributions per exercise. Mock exercise ids may
    // collide with real, inaccessible exercises in the dev DB, which would return 403 and surface a
    // "not authorized" alert. Return an empty list so the detail view stays quiet.
    { pattern: /^api\/atlas\/exercises\/\d+\/contributions$/, data: () => [] },
];

// The /original and /experimental exercise views (both the instructor management view and the
// student overview) are backed by mock data. The default /exercises route hits the real backend.
const VERSIONED_VIEW = /\/exercises\/(?:original|experimental)(?:[/?#]|$)/;

// The student exercise overview reads its exercises from the course returned by /for-dashboard,
// not from the per-type endpoints the instructor view uses. We let the real request through and
// graft the mock catalogue onto the course so the student view shows the same exercises.
const FOR_DASHBOARD = /^api\/core\/courses\/\d+\/for-dashboard$/;

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

    intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
        if (!isDevMode() || req.method !== 'GET' || !VERSIONED_VIEW.test(this.router.url)) {
            return next.handle(req);
        }

        if (FOR_DASHBOARD.test(req.url)) {
            return next.handle(req).pipe(map((event) => this.injectMockExercises(event)));
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
            return mockResponse(match.data());
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
        const body = toJson(event.body) as { course?: { exercises?: unknown[] } };
        if (body.course) {
            body.course.exercises = toJson(INTRO_JAVA_ALL_EXERCISES);
        }
        return event.clone({ body });
    }
}
