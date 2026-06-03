import { Injectable, inject, isDevMode } from '@angular/core';
import { Router } from '@angular/router';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import {
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
];

// The /original and /experimental exercise-management views are backed by mock data.
// The default /exercises route hits the real backend.
const VERSIONED_VIEW = /\/exercises\/(?:original|experimental)(?:[/?#]|$)/;

@Injectable()
export class MockCourseInterceptor implements HttpInterceptor {
    private readonly router = inject(Router);

    intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
        if (!isDevMode() || req.method !== 'GET' || !VERSIONED_VIEW.test(this.router.url)) {
            return next.handle(req);
        }

        const match = EXERCISE_ROUTES.find(({ pattern }) => pattern.test(req.url));
        if (match) {
            return mockResponse(match.data());
        }

        return next.handle(req);
    }
}
