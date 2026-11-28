import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RedirectCommand, Router, provideRouter } from '@angular/router';
import appRoutes from 'app/app.routes';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { PROGRAMMING_EXERCISE_EXPORT_ACTION } from 'app/programming/api/programming-exercise-extensions';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resolveSharingImport } from './sharing-import.route';
import { sharingExportProvider } from './sharing.routes';

describe('Sharing integration', () => {
    const active = vi.fn();
    const alerts = { success: vi.fn(), error: vi.fn() };
    let http: HttpTestingController;

    beforeEach(() => {
        active.mockReset();
        vi.clearAllMocks();
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([
                    { path: 'courses', children: [] },
                    appRoutes.find((route) => route.path === 'sharing/import/:basketToken')!,
                    appRoutes.find((route) => route.path === 'course-management' && route.loadChildren)!,
                ]),
                { provide: ProfileService, useValue: { isModuleFeatureActive: active } },
                { provide: AlertService, useValue: alerts },
                sharingExportProvider,
            ],
        });
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it.each(['/sharing/import/token', '/course-management/7/programming-exercises/import-from-sharing'])(
        'redirects the disabled route %s before fetching sharing data',
        async (url) => {
            active.mockReturnValue(false);
            const router = TestBed.inject(Router);
            await router.navigateByUrl(url);
            expect(router.url).toBe('/courses');
            expect(await firstValueFrom(TestBed.inject(PROGRAMMING_EXERCISE_EXPORT_ACTION))).toBeUndefined();
        },
    );

    it('offers the export action only after confirming the configured connection', async () => {
        active.mockReturnValue(true);
        const actions = TestBed.inject(PROGRAMMING_EXERCISE_EXPORT_ACTION);
        const disconnected = firstValueFrom(actions);
        await vi.waitFor(() => http.expectOne('api/core/sharing/config/is-enabled').flush(false));
        expect(await disconnected).toBeUndefined();

        const connected = firstValueFrom(actions);
        await vi.waitFor(() => http.expectOne('api/core/sharing/config/is-enabled').flush(true));
        const action = await connected;
        expect(action?.label).toBe('artemisApp.sharing.export');
        action!.run(7);
        const exported = http.expectOne('api/programming/sharing/export?exerciseId=7');
        expect(exported.request.body).toBe(window.location.href);
        exported.flush(null);
        expect(alerts.error).toHaveBeenCalledWith('artemisApp.sharing.error.noRedirect');

        action!.run(7);
        http.expectOne('api/programming/sharing/export?exerciseId=7').flush({ message: 'Unavailable' }, { status: 503, statusText: 'Unavailable' });
        expect(alerts.error).toHaveBeenCalledWith('artemisApp.sharing.error.export', { message: 'Unavailable' });
    });

    it('submits the edited exercise with the selected basket entry and destination course', async () => {
        const route = new ActivatedRouteSnapshot();
        route.queryParams = { basketToken: 'token', apiBaseURL: 'https://platform.test/api', returnURL: '/return', checksum: 'signed', selectedExercise: '2' };
        const result = firstValueFrom(TestBed.runInInjectionContext(() => resolveSharingImport(route)));
        const request = http.expectOne('api/programming/sharing/import/basket/exercise-details');
        const signedBasket = { ...route.queryParams, selectedExercise: 2 };
        expect(request.request.body).toMatchObject(signedBasket);
        request.flush({ title: 'Original' });
        const resolved = await result;
        if (resolved instanceof RedirectCommand) throw new Error('Unexpected redirect');
        expect(resolved.exercise.title).toBe('Original');
        const edited = new ProgrammingExercise(undefined, undefined);
        edited.title = 'Renamed';
        const saved = firstValueFrom(resolved.save(edited, 7));
        const setup = http.expectOne('api/programming/sharing/setup-import');
        expect(setup.request.body).toMatchObject({ courseId: 7, sharingInfo: signedBasket, exercise: { title: 'Renamed' } });
        setup.flush({ id: 8, title: 'Renamed' });
        expect((await saved).body?.id).toBe(8);
        expect(alerts.success).toHaveBeenCalledWith('artemisApp.programmingExercise.created', { param: 'Renamed' });
    });

    it('returns to the course when the selected exercise cannot be loaded', async () => {
        const route = new ActivatedRouteSnapshot();
        route.params = { courseId: '7' };
        route.queryParams = { basketToken: 'token', apiBaseURL: 'https://platform.test/api', returnURL: '/return', checksum: 'signed', selectedExercise: '0' };
        const result = firstValueFrom(TestBed.runInInjectionContext(() => resolveSharingImport(route)));
        http.expectOne('api/programming/sharing/import/basket/exercise-details').flush(null, { status: 404, statusText: 'Not found' });
        const redirect = await result;
        expect(redirect).toBeInstanceOf(RedirectCommand);
        expect((redirect as RedirectCommand).redirectTo.toString()).toBe('/course-management/7/exercises');
        expect(alerts.error).toHaveBeenCalledWith('artemisApp.sharing.error.loadingDetails', { message: expect.any(String) });
    });
});
