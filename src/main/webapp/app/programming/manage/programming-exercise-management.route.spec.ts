import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { describe, expect, it } from 'vitest';
import { routes } from './programming-exercise-management.route';

@Component({ template: '' })
class GenerationPageStub {}

describe('programming exercise generation routes', () => {
    it.each([
        ['generation/runs/exact-run', 'exact-run'],
        ['generation?run=exact-run', 'exact-run'],
        ['generation', 'latest'],
    ])('opens the exercise dialog for legacy %s links', async (legacyPath, runId) => {
        const legacyRoutes = routes.filter((route) => route.path?.startsWith('programming-exercises/:exerciseId/generation'));
        TestBed.configureTestingModule({
            providers: [
                provideRouter([
                    {
                        path: 'course-management/:courseId',
                        children: [...legacyRoutes, { path: 'programming-exercises/:exerciseId', component: GenerationPageStub }],
                    },
                ]),
            ],
        });
        const harness = await RouterTestingHarness.create();
        await harness.navigateByUrl(`/course-management/7/programming-exercises/12/${legacyPath}`, GenerationPageStub);
        expect(TestBed.inject(Router).url).toBe(`/course-management/7/programming-exercises/12?aiRun=authoring:12:${runId}`);
    });
});
