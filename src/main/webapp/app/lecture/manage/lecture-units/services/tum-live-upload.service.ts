import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { TumLiveUploadApi } from 'app/openapi/api/tum-live-upload-api';
import { parseJson } from 'app/foundation/util/json.util';

/** The TUM.Live upload state of an attachment video unit. */
export interface TumLiveUploadState {
    connected?: boolean;
    pendingRef?: string;
    pendingSince?: string;
    converting?: boolean;
    progress?: number;
    /** The reference of the upload whose video the unit shows, if Artemis uploaded it. */
    attachedRef?: string;
    lastError?: string;
}

/** Where to send the recording of a unit in Artemis. */
export interface TumLiveUploadStart {
    ref: string;
    uploadUrl: string;
}

/** A unit that shows a video Artemis uploaded to TUM.Live. */
export interface TumLiveUploadedUnit {
    lectureId: number;
    lectureUnitId: number;
}

/** Progress of a transfer to TUM.Live: the share sent so far, then done. */
export type TumLiveTransferEvent = { type: 'progress'; percent: number } | { type: 'done' };

export class TumLiveTransferError extends Error {
    constructor(
        message: string,
        readonly status: number,
        readonly translationKey?: string,
    ) {
        super(message);
    }
}

/** Sends videos through Artemis to TUM.Live and reads their conversion status. */
@Service()
export class TumLiveUploadService {
    private readonly httpClient = inject(HttpClient);

    private readonly api = inject(TumLiveUploadApi);

    isAvailable(courseId: number): Observable<boolean> {
        return this.api.getTumLiveUploadAvailability(courseId, { context: new HttpContext().set(SKIP_HTTP_ERROR_ALERT, true) }).pipe(map((result) => result.available === true));
    }

    getState(lectureUnitId: number): Observable<TumLiveUploadState> {
        return this.httpClient.get<TumLiveUploadState>(`api/lecture/lecture-units/${lectureUnitId}/tumlive-upload`);
    }

    start(lectureUnitId: number): Observable<TumLiveUploadStart> {
        return this.httpClient.post<TumLiveUploadStart>(`api/lecture/lecture-units/${lectureUnitId}/tumlive-upload`, undefined, {
            context: new HttpContext().set(SKIP_HTTP_ERROR_ALERT, true),
        });
    }

    discard(lectureUnitId: number, ref: string): Observable<void> {
        return this.httpClient.delete<void>(`api/lecture/lecture-units/${lectureUnitId}/tumlive-upload`, { params: new HttpParams().set('ref', ref) });
    }

    getUploadedUnits(courseId: number): Observable<TumLiveUploadedUnit[]> {
        return this.httpClient.get<TumLiveUploadedUnit[]>(`api/lecture/courses/${courseId}/tumlive-uploads`);
    }

    /**
     * Sends the raw file to Artemis so it can stream it to TUM.Live. XMLHttpRequest provides upload progress and sends the Artemis session cookie on this
     * same-origin request. Unsubscribing cancels the transfer.
     *
     * @param uploadUrl the Artemis upload URL from {@link start}
     * @param file      the video
     * @returns the transfer's progress, completing when TUM.Live took the file
     */
    transfer(uploadUrl: string, file: File): Observable<TumLiveTransferEvent> {
        return new Observable<TumLiveTransferEvent>((subscriber) => {
            const request = new XMLHttpRequest();
            request.open('POST', uploadUrl);
            request.setRequestHeader('Content-Type', 'application/octet-stream');
            request.upload.onprogress = (event) => {
                if (event.lengthComputable) {
                    subscriber.next({ type: 'progress', percent: Math.floor((event.loaded / event.total) * 100) });
                }
            };
            request.onload = () => {
                if (request.status >= 200 && request.status < 300) {
                    subscriber.next({ type: 'done' });
                    subscriber.complete();
                } else {
                    subscriber.error(transferError(request));
                }
            };
            request.onerror = () => subscriber.error(new TumLiveTransferError('network', 0));
            request.send(file);
            return () => {
                if (request.readyState !== XMLHttpRequest.DONE) {
                    request.abort();
                }
            };
        });
    }
}

/** Artemis reports errors as problem details. */
function transferError(request: XMLHttpRequest): TumLiveTransferError {
    const fallback = `HTTP ${request.status}`;
    try {
        const problem = parseJson<{ detail?: unknown; message?: unknown } | null>(request.responseText);
        if (typeof problem?.message === 'string' && problem.message.startsWith('error.')) {
            return new TumLiveTransferError(fallback, request.status, problem.message);
        }
        const message = problem?.detail ?? problem?.message;
        if (typeof message === 'string' && message) {
            return message.startsWith('error.') ? new TumLiveTransferError(fallback, request.status, message) : new TumLiveTransferError(message, request.status);
        }
    } catch {
        // not JSON, for example a proxy's error page
    }
    return new TumLiveTransferError(fallback, request.status);
}
