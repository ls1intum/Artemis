import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LogsService } from 'app/admin/logs/logs.service';
import { Log } from 'app/admin/logs/log.model';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

describe('Logs Service', () => {
    let service: LogsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });

        service = TestBed.inject(LogsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        it('should call correct URL', fakeAsync(() => {
            const resourceUrl = SERVER_API_URL + 'management/loggers';

            service.findAll().subscribe(() => {});

            const req = httpMock.expectOne({ method: 'GET' });
            expect(req.request.url).toEqual(resourceUrl);
            tick();
        }));

        it('should return Logs', fakeAsync(() => {
            const log = new Log('main', 'ERROR');

            service.findAll().subscribe((resp) => expect(resp).toEqual(log));

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(log);
            tick();
        }));

        it('should change log level', fakeAsync(() => {
            const log = new Log('new', 'ERROR');

            service.changeLevel(log.name, log.level).subscribe((received) => expect(received).toEqual(log));

            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(log);
            tick();
        }));
    });
});
