import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { mockSettings } from './mock-settings';

describe('Iris Settings Service', () => {
    let service: IrisSettingsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [IrisSettingsService],
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
            .getUncombinedProgrammingExerciseSettings(1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(mockedSettings));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
    }));

    it('should get combined programming exercise settings', fakeAsync(() => {
        const mockedSettings = mockSettings();
        service
            .getCombinedProgrammingExerciseSettings(1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(mockedSettings));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
    }));

    it('should set programming exercise settings', fakeAsync(() => {
        const mockedSettings = mockSettings();
        service
            .setProgrammingExerciseSettings(1, mockedSettings)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.body).toEqual(mockedSettings));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(mockedSettings, { status: 200, statusText: 'Ok' });
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
