import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { ApplicationRef, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, RouterOutlet, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockProvider } from 'ng-mocks';
import { EMPTY, of } from 'rxjs';
import { CourseExerciseDetailsComponent } from 'app/course/overview/exercise-details/course-exercise-details.component';
import { ExerciseSplitPanelComponent } from 'app/course/overview/exercise-details/exercise-split-panel/exercise-split-panel.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { TeamService } from 'app/exercise/team/team.service';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { ScienceService } from 'app/foundation/science/science.service';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';

@Component({ template: '' })
class EmptyRouteComponent {}

describe('ExerciseSplitPanelComponent browser history', () => {
    let harness: RouterTestingHarness;
    let router: Router;
    let location: Location;

    beforeEach(async () => {
        const exercise = Object.assign(new QuizExercise(undefined, undefined), { id: 1, studentParticipations: [] });
        await TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([
                    { path: 'courses', component: EmptyRouteComponent },
                    {
                        path: 'courses/:courseId',
                        children: [
                            {
                                path: 'exercises',
                                children: [
                                    { path: ':exerciseId', component: CourseExerciseDetailsComponent, pathMatch: 'full' },
                                    {
                                        path: 'quiz-exercises/:exerciseId',
                                        component: CourseExerciseDetailsComponent,
                                        children: [
                                            { path: 'live', component: EmptyRouteComponent },
                                            { path: 'practice', component: EmptyRouteComponent },
                                            { path: 'practice/:participationId', component: EmptyRouteComponent },
                                        ],
                                    },
                                ],
                            },
                        ],
                    },
                ]),
                provideLocationMocks(),
                { provide: AccountService, useClass: MockAccountService },
                { provide: IrisChatService, useValue: { openChat: vi.fn() } },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                MockProvider(ExerciseService, { getExerciseDetails: () => of(new HttpResponse({ body: { exercise } })) }),
                MockProvider(TeamService, { teamAssignmentUpdates: Promise.resolve(EMPTY) }),
                MockProvider(QuizExerciseService),
                MockProvider(ComplaintService),
                MockProvider(AlertService),
                MockProvider(ScienceService),
                MockProvider(IrisSettingsService),
            ],
        })
            .overrideComponent(CourseExerciseDetailsComponent, {
                set: {
                    template: '@if (exercise) { <jhi-exercise-split-panel [exercise]="exercise" [courseId]="courseId" [participationMode]="participationMode()" /> }',
                    templateUrl: undefined,
                    imports: [ExerciseSplitPanelComponent],
                },
            })
            .overrideComponent(ExerciseSplitPanelComponent, { set: { template: '<router-outlet />', templateUrl: undefined, imports: [RouterOutlet] } })
            .compileComponents();

        vi.spyOn(TestBed.inject(ParticipationWebsocketService), 'getParticipationsForExercise').mockReturnValue([]);
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
        const details = await harness.navigateByUrl('/courses/1/exercises/quiz-exercises/1/live', CourseExerciseDetailsComponent);
        details.participationMode.set('practice');
        harness.detectChanges();
        await TestBed.inject(ApplicationRef).whenStable();
        expect(router.url).toBe('/courses/1/exercises/quiz-exercises/1/practice');

        location.back();
        await vi.waitFor(() => expect(router.url).toBe('/courses/1/exercises/quiz-exercises/1/live'));
        expect(details.participationMode()).toBe('graded');
        location.forward();
        await vi.waitFor(() => expect(router.url).toBe('/courses/1/exercises/quiz-exercises/1/practice'));
        expect(details.participationMode()).toBe('practice');
    });

    it.each(['practice', 'practice/20'])('keeps the parent in practice mode when directly opening the %s quiz route', async (path) => {
        const url = `/courses/1/exercises/quiz-exercises/1/${path}`;
        const details = await harness.navigateByUrl(url, CourseExerciseDetailsComponent);
        await TestBed.inject(ApplicationRef).whenStable();

        expect(router.url).toBe(url);
        expect(details.participationMode()).toBe('practice');
    });
});
