import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TumLiveTransferError, TumLiveTransferEvent, TumLiveUploadService } from 'app/lecture/manage/lecture-units/services/tum-live-upload.service';

/** A stand-in for the browser's XMLHttpRequest that the test drives by hand. */
class FakeXhr {
    static last: FakeXhr;
    method?: string;
    url?: string;
    readyState = 1;
    status = 0;
    responseText = '';
    body?: File;
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

    send(body: File) {
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

    it.each([true, false])('reports course upload availability (%s)', (available) => {
        const result = vi.fn();
        service.isAvailable(3).subscribe(result);

        httpMock.expectOne({ method: 'GET', url: '/api/lecture/courses/3/tumlive-upload-availability' }).flush({ available });
        expect(result).toHaveBeenCalledExactlyOnceWith(available);
    });

    it('sends the raw file to Artemis and reports progress', () => {
        const events: TumLiveTransferEvent[] = [];
        let completed = false;
        const file = new File(['video'], 'lecture.mp4', { type: 'video/mp4' });

        service
            .transfer('/api/lecture/lecture-units/7/tumlive-upload/file?ref=reference-a-0000', file)
            .subscribe({ next: (event) => events.push(event), complete: () => (completed = true) });
        const xhr = FakeXhr.last;
        xhr.upload.onprogress?.({ lengthComputable: true, loaded: 25, total: 100 });
        xhr.respond(204);

        expect(xhr.method).toBe('POST');
        expect(xhr.url).toBe('/api/lecture/lecture-units/7/tumlive-upload/file?ref=reference-a-0000');
        expect(xhr.requestHeaders).toEqual({ 'Content-Type': 'application/octet-stream', 'ngsw-bypass': 'true' });
        expect(xhr.body).toBe(file);
        expect(events).toEqual([{ type: 'progress', percent: 25 }, { type: 'done' }]);
        expect(completed).toBe(true);
    });

    it('reports Artemis errors and network failures', () => {
        const errors: string[] = [];
        const file = new File(['video'], 'lecture.mp4');

        service.transfer('/api/lecture/lecture-units/7/tumlive-upload/file?ref=reference-a-0000', file).subscribe({ error: (error: Error) => errors.push(error.message) });
        FakeXhr.last.respond(400, '{"status":400,"detail":"The video is empty."}');
        service.transfer('/api/lecture/lecture-units/7/tumlive-upload/file?ref=reference-a-0000', file).subscribe({ error: (error: Error) => errors.push(error.message) });
        FakeXhr.last.respond(413, '<html>Request Entity Too Large</html>');
        service.transfer('/api/lecture/lecture-units/7/tumlive-upload/file?ref=reference-a-0000', file).subscribe({ error: (error: Error) => errors.push(error.message) });
        FakeXhr.last.onerror?.();

        expect(errors).toEqual(['The video is empty.', 'HTTP 413', 'network']);
    });

    it('keeps Artemis translation keys separate from display text', () => {
        const error = vi.fn();
        service.transfer('/upload', new File(['video'], 'lecture.mp4')).subscribe({ error });
        FakeXhr.last.respond(409, '{"status":409,"detail":"Another upload is already running.","message":"error.tumLiveUploadInProgress"}');

        expect(error).toHaveBeenCalledWith(expect.any(TumLiveTransferError));
        expect(error).toHaveBeenCalledWith(expect.objectContaining({ status: 409, message: 'HTTP 409', translationKey: 'error.tumLiveUploadInProgress' }));
    });

    it('aborts the transfer when unsubscribed', () => {
        const subscription = service.transfer('/api/lecture/lecture-units/7/tumlive-upload/file?ref=reference-a-0000', new File(['video'], 'lecture.mp4')).subscribe();

        subscription.unsubscribe();

        expect(FakeXhr.last.aborted).toBe(true);
    });
});
