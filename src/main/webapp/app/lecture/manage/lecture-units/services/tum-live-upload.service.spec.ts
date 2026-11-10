import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TumLiveTransferEvent, TumLiveUploadService } from 'app/lecture/manage/lecture-units/services/tum-live-upload.service';

/** A stand-in for the browser's XMLHttpRequest that the test drives by hand. */
class FakeXhr {
    static last: FakeXhr;
    method?: string;
    url?: string;
    withCredentials = true;
    readyState = 1;
    status = 0;
    responseText = '';
    body?: FormData;
    aborted = false;
    readonly upload: { onprogress?: (event: { lengthComputable: boolean; loaded: number; total: number }) => void } = {};
    onload?: () => void;
    onerror?: () => void;
    requestHeaders: Record<string, string> = {};

    constructor() {
        FakeXhr.last = this;
    }

    open(method: string, url: string) {
        this.method = method;
        this.url = url;
    }

    setRequestHeader(name: string, value: string) {
        this.requestHeaders[name] = value;
    }

    send(body: FormData) {
        this.body = body;
    }

    abort() {
        this.aborted = true;
    }

    respond(status: number, responseText = '') {
        this.status = status;
        this.responseText = responseText;
        this.readyState = 4;
        this.onload?.();
    }
}

describe('TumLiveUploadService', () => {
    let service: TumLiveUploadService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(TumLiveUploadService);
        httpMock = TestBed.inject(HttpTestingController);
        vi.stubGlobal('XMLHttpRequest', Object.assign(FakeXhr, { DONE: 4 }));
    });

    afterEach(() => {
        httpMock.verify();
        vi.unstubAllGlobals();
    });

    it('calls the unit and course endpoints', () => {
        service.getState(7).subscribe();
        service.start(7).subscribe();
        service.discard(7, 'reference-a-0000').subscribe();
        service.getUploadedUnits(3).subscribe();

        expect(httpMock.expectOne({ method: 'GET', url: 'api/lecture/lecture-units/7/tumlive-upload' })).toBeTruthy();
        expect(httpMock.expectOne({ method: 'POST', url: 'api/lecture/lecture-units/7/tumlive-upload' })).toBeTruthy();
        const discard = httpMock.expectOne((request) => request.method === 'DELETE' && request.url === 'api/lecture/lecture-units/7/tumlive-upload');
        expect(discard.request.params.get('ref')).toBe('reference-a-0000');
        expect(httpMock.expectOne({ method: 'GET', url: 'api/lecture/courses/3/tumlive-uploads' })).toBeTruthy();
    });

    it('sends the file straight to TUM.Live without credentials and reports progress', () => {
        const events: TumLiveTransferEvent[] = [];
        let completed = false;
        const file = new File(['video'], 'lecture.mp4', { type: 'video/mp4' });

        service.transfer('https://tum.live/api/v2/integration/uploads/ticket', file).subscribe({ next: (event) => events.push(event), complete: () => (completed = true) });
        const xhr = FakeXhr.last;
        xhr.upload.onprogress?.({ lengthComputable: true, loaded: 25, total: 100 });
        xhr.respond(200, '{}');

        expect(xhr.method).toBe('POST');
        expect(xhr.url).toBe('https://tum.live/api/v2/integration/uploads/ticket');
        expect(xhr.withCredentials).toBe(false);
        expect(xhr.requestHeaders).toEqual({});
        expect((xhr.body!.get('file') as File).name).toBe('lecture.mp4');
        expect(events).toEqual([{ type: 'progress', percent: 25 }, { type: 'done' }]);
        expect(completed).toBe(true);
    });

    it('reports TUM.Live errors and network failures', () => {
        const errors: string[] = [];
        const file = new File(['video'], 'lecture.mp4');

        service.transfer('https://tum.live/u', file).subscribe({ error: (error: Error) => errors.push(error.message) });
        FakeXhr.last.respond(400, '{"code":3,"message":"runner: no file in request"}');
        service.transfer('https://tum.live/u', file).subscribe({ error: (error: Error) => errors.push(error.message) });
        FakeXhr.last.respond(413, '<html>Request Entity Too Large</html>');
        service.transfer('https://tum.live/u', file).subscribe({ error: (error: Error) => errors.push(error.message) });
        FakeXhr.last.onerror?.();

        expect(errors).toEqual(['runner: no file in request', 'HTTP 413', 'network']);
    });

    it('aborts the transfer when unsubscribed', () => {
        const subscription = service.transfer('https://tum.live/u', new File(['video'], 'lecture.mp4')).subscribe();

        subscription.unsubscribe();

        expect(FakeXhr.last.aborted).toBe(true);
    });
});
