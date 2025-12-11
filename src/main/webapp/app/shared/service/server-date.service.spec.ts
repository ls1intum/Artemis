import { HttpTestingController } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';

describe('ArtemisServerDateService', () => {
    let service: ArtemisServerDateService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        jest.useFakeTimers();
        jest.setSystemTime(new Date('2024-01-01T00:00:00.000Z'));

        TestBed.configureTestingModule({
            providers: [provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()],
        });

        service = TestBed.inject(ArtemisServerDateService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        jest.useRealTimers();
    });

    it('should request server time when sync is needed', () => {
        service.updateTime();

        const req = httpMock.expectOne('api/core/public/time');
        expect(req.request.method).toBe('GET');

        const serverDate = dayjs().add(1, 'second').toISOString();
        req.flush(serverDate);

        expect(service.recentOffsets).toContain(1000);
        expect(service.recentClientDates).toHaveLength(1);
    });

    it('should not request server time when recent syncs are fresh', () => {
        const now = dayjs();
        service.recentClientDates = [now, now, now, now, now];
        service.recentOffsets = [1, 2, 3, 4, 5];

        service.updateTime();

        httpMock.expectNone('api/core/public/time');
    });

    it('should keep only the last five offsets and client dates', () => {
        service.recentOffsets = [1, 2, 3, 4, 5];
        service.recentClientDates = [
            dayjs().subtract(5, 'seconds'),
            dayjs().subtract(4, 'seconds'),
            dayjs().subtract(3, 'seconds'),
            dayjs().subtract(2, 'seconds'),
            dayjs().subtract(1, 'seconds'),
        ];

        const serverDate = dayjs().add(2, 'seconds').toISOString();
        service.setServerDate(serverDate);

        expect(service.recentOffsets).toHaveLength(5);
        expect(service.recentOffsets[0]).toBe(2); // first entry dropped
        expect(service.recentClientDates).toHaveLength(5);
    });

    it('should return client date when no offsets are available', () => {
        const now = service.now();
        expect(now.isSame(dayjs())).toBeTrue();
    });

    it('should average offsets when five values exist', () => {
        service.recentOffsets = [100, 200, 300, 400, 500];
        const adjusted = service.now();
        expect(adjusted.diff(dayjs(), 'ms')).toBe(300);
    });
});
