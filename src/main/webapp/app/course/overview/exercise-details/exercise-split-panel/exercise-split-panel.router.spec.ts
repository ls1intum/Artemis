import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { ApplicationRef, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, RouterOutlet, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ExerciseSplitPanelComponent } from 'app/course/overview/exercise-details/exercise-split-panel/exercise-split-panel.component';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ParticipationMode } from 'app/exercise/exercise-headers/participation-mode-toggle/participation-mode-toggle.component';
import { AccountService } from 'app/core/auth/account.service';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

@Component({ template: '' })
class EmptyRouteComponent {}

@Component({
    imports: [ExerciseSplitPanelComponent],
    template: '<jhi-exercise-split-panel [exercise]="exercise" [courseId]="1" [participationMode]="mode()" />',
})
class QuizDetailsRouteComponent {
    readonly exercise = Object.assign(new QuizExercise(undefined, undefined), { id: 1 });
    readonly mode = signal<ParticipationMode>('graded');
}

describe('ExerciseSplitPanelComponent browser history', () => {
    let harness: RouterTestingHarness;
    let router: Router;
    let location: Location;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                provideRouter([
                    { path: 'courses', component: EmptyRouteComponent },
                    {
                        path: 'courses/:courseId/exercises',
                        children: [
                            { path: ':exerciseId', component: QuizDetailsRouteComponent, pathMatch: 'full' },
                            {
                                path: 'quiz-exercises/:exerciseId',
                                component: QuizDetailsRouteComponent,
                                children: [
                                    { path: 'live', component: EmptyRouteComponent },
                                    { path: 'practice', component: EmptyRouteComponent },
                                ],
                            },
                        ],
                    },
                ]),
                provideLocationMocks(),
                { provide: AccountService, useClass: MockAccountService },
                { provide: IrisChatService, useValue: { openChat: vi.fn() } },
            ],
        })
            .overrideComponent(ExerciseSplitPanelComponent, { set: { template: '<router-outlet />', templateUrl: undefined, imports: [RouterOutlet] } })
            .compileComponents();

        harness = await RouterTestingHarness.create('/courses');
        router = TestBed.inject(Router);
        location = TestBed.inject(Location);
        router.setUpLocationChangeListener();
    });

    afterEach(() => {
        TestBed.resetTestingModule();
    });

    it('replaces the automatic quiz route so Back leaves the exercise and Forward returns to the live quiz', async () => {
        await harness.navigateByUrl('/courses/1/exercises/1');
        await TestBed.inject(ApplicationRef).whenStable();
        expect(router.url).toBe('/courses/1/exercises/quiz-exercises/1/live');

        location.back();
        await vi.waitFor(() => expect(router.url).toBe('/courses'));
        location.forward();
        await vi.waitFor(() => expect(router.url).toBe('/courses/1/exercises/quiz-exercises/1/live'));
        location.back();
        await vi.waitFor(() => expect(router.url).toBe('/courses'));
    });

    it('keeps an explicit live-to-practice switch in history', async () => {
        const details = await harness.navigateByUrl('/courses/1/exercises/quiz-exercises/1/live', QuizDetailsRouteComponent);
        details.mode.set('practice');
        harness.detectChanges();
        await TestBed.inject(ApplicationRef).whenStable();
        expect(router.url).toBe('/courses/1/exercises/quiz-exercises/1/practice');

        location.back();
        await vi.waitFor(() => expect(router.url).toBe('/courses/1/exercises/quiz-exercises/1/live'));
        location.forward();
        await vi.waitFor(() => expect(router.url).toBe('/courses/1/exercises/quiz-exercises/1/practice'));
    });
});
