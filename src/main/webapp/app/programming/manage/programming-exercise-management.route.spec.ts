import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { describe, expect, it } from 'vitest';
import { routes } from './programming-exercise-management.route';

@Component({ template: '' })
class GenerationPageStub {}

describe('programming exercise generation routes', () => {
    it('preserves the exact run when redirecting a legacy deep link to the shallow generation page', async () => {
        const legacyRoute = routes.find((route) => route.path === 'programming-exercises/:exerciseId/generation/runs/:runId')!;
        TestBed.configureTestingModule({
            providers: [
                provideRouter([
                    {
                        path: 'course-management/:courseId',
                        children: [legacyRoute, { path: 'programming-exercises/:exerciseId/generation', component: GenerationPageStub }],
                    },
                ]),
            ],
        });
        const harness = await RouterTestingHarness.create();
        await harness.navigateByUrl('/course-management/7/programming-exercises/12/generation/runs/exact-run', GenerationPageStub);
        expect(TestBed.inject(Router).url).toBe('/course-management/7/programming-exercises/12/generation?run=exact-run');
    });
});
