import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { mockSettings } from 'test/helpers/mocks/iris/mock-settings';
import { provideHttpClient } from '@angular/common/http';

describe('Iris Settings Service', () => {
    let service: IrisSettingsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), IrisSettingsService],
        });
        service = TestBed.inject(IrisSettingsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('should get global settings', fakeAsync(() => {
        const mockedSettings = mockSettings();
        service
            .getGlobalSettings()
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(mockedSettings));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
    }));

    it('should set global settings', fakeAsync(() => {
        const mockedSettings = mockSettings();
        service
            .setGlobalSettings(mockedSettings)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.body).toEqual(mockedSettings));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
    }));

    it('should get uncombined course settings', fakeAsync(() => {
        const mockedSettings = mockSettings();
        service
            .getUncombinedCourseSettings(1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(mockedSettings));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
    }));

    it('should get combined course settings', fakeAsync(() => {
        const mockedSettings = mockSettings();
        service
            .getCombinedCourseSettings(1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(mockedSettings));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
    }));

    it('should reuse pending request when getting combined course settings', fakeAsync(() => {
        const mockedSettings = mockSettings();
        service
            .getCombinedCourseSettings(1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(mockedSettings));
        service
            .getCombinedCourseSettings(1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(mockedSettings));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
    }));

    it('should reuse cached result after getting combined course settings', fakeAsync(() => {
        const mockedSettings = mockSettings();
        service.getCombinedCourseSettings(1).pipe(take(1)).subscribe();
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
        service
            .getCombinedCourseSettings(1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(mockedSettings));
        httpMock.expectNone({ method: 'GET' });
        tick();
    }));

    it('should set course settings', fakeAsync(() => {
        const mockedSettings = mockSettings();
        service
            .setCourseSettings(1, mockedSettings)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.body).toEqual(mockedSettings));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
    }));

    it('should get uncombined programming exercise settings', fakeAsync(() => {
        const mockedSettings = mockSettings();
        service
            .getUncombinedExerciseSettings(1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(mockedSettings));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
    }));

    it('should get combined programming exercise settings', fakeAsync(() => {
        const mockedSettings = mockSettings();
        service
            .getCombinedExerciseSettings(1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(mockedSettings));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
    }));

    it('should reuse pending request when getting combined programming exercise settings', fakeAsync(() => {
        const mockedSettings = mockSettings();
        service
            .getCombinedExerciseSettings(1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(mockedSettings));
        service
            .getCombinedExerciseSettings(1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(mockedSettings));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
    }));

    it('should reuse cached result after getting combined programming exercise settings', fakeAsync(() => {
        const mockedSettings = mockSettings();
        service.getCombinedExerciseSettings(1).pipe(take(1)).subscribe();
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
        service
            .getCombinedExerciseSettings(1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(mockedSettings));
        httpMock.expectNone({ method: 'GET' });
        tick();
    }));

    it('should trigger new request after cache duration for course settings', fakeAsync(() => {
        const mockedSettings = mockSettings();
        service.getCombinedCourseSettings(1).pipe(take(1)).subscribe();
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();

        // Advance time by more than CACHE_DURATION
        tick((IrisSettingsService as any).CACHE_DURATION + 1);

        service.getCombinedCourseSettings(1).pipe(take(1)).subscribe();
        const newReq = httpMock.expectOne({ method: 'GET' });
        newReq.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
    }));

    it('should trigger new request after cache duration for exercise settings', fakeAsync(() => {
        const mockedSettings = mockSettings();
        service.getCombinedExerciseSettings(1).pipe(take(1)).subscribe();
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();

        // Advance time by more than CACHE_DURATION
        tick((IrisSettingsService as any).CACHE_DURATION + 1);

        service.getCombinedExerciseSettings(1).pipe(take(1)).subscribe();
        const newReq = httpMock.expectOne({ method: 'GET' });
        newReq.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
    }));

    it('should set programming exercise settings', fakeAsync(() => {
        const mockedSettings = mockSettings();
        service
            .setExerciseSettings(1, mockedSettings)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.body).toEqual(mockedSettings));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });

    it('should clear pending request and allow retry after failure for course settings', fakeAsync(() => {
        service
            .getCombinedCourseSettings(1)
            .pipe(take(1))
            .subscribe({
                error: (err) => expect(err.status).toBe(500),
            });
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush({}, { status: 500, statusText: 'Internal Server Error' });
        tick();

        // Retry should trigger a new request
        const mockedSettings = mockSettings();
        service
            .getCombinedCourseSettings(1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(mockedSettings));
        const retryReq = httpMock.expectOne({ method: 'GET' });
        retryReq.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
    }));

    it('should clear pending request and allow retry after failure for exercise settings', fakeAsync(() => {
        service
            .getCombinedExerciseSettings(1)
            .pipe(take(1))
            .subscribe({
                error: (err) => expect(err.status).toBe(500),
            });
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush({}, { status: 500, statusText: 'Internal Server Error' });
        tick();

        // Retry should trigger a new request
        const mockedSettings = mockSettings();
        service
            .getCombinedExerciseSettings(1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(mockedSettings));
        const retryReq = httpMock.expectOne({ method: 'GET' });
        retryReq.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
    }));
});
