import { HttpClient, HttpParams } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
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

/** Where to send the recording of a unit: a one-use URL on TUM.Live. */
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

/**
 * Uploads the video of an attachment video unit to the course's TUM.Live course. The browser sends the file straight to TUM.Live under a one-use URL,
 * so Artemis never receives it; Artemis attaches the converted recording in the background.
 */
@Service()
export class TumLiveUploadService {
    private readonly httpClient = inject(HttpClient);

    getState(lectureUnitId: number): Observable<TumLiveUploadState> {
        return this.httpClient.get<TumLiveUploadState>(`api/lecture/lecture-units/${lectureUnitId}/tumlive-upload`);
    }

    start(lectureUnitId: number): Observable<TumLiveUploadStart> {
        return this.httpClient.post<TumLiveUploadStart>(`api/lecture/lecture-units/${lectureUnitId}/tumlive-upload`, undefined);
    }

    discard(lectureUnitId: number, ref: string): Observable<void> {
        return this.httpClient.delete<void>(`api/lecture/lecture-units/${lectureUnitId}/tumlive-upload`, { params: new HttpParams().set('ref', ref) });
    }

    getUploadedUnits(courseId: number): Observable<TumLiveUploadedUnit[]> {
        return this.httpClient.get<TumLiveUploadedUnit[]>(`api/lecture/courses/${courseId}/tumlive-uploads`);
    }

    /**
     * Sends a file to TUM.Live. A plain XMLHttpRequest rather than Artemis' HTTP client: the request goes to another origin, must not carry Artemis' headers or
     * cookies, and reports its upload progress. Unsubscribing cancels the transfer.
     *
     * @param uploadUrl the one-use upload URL from {@link start}
     * @param file      the video
     * @returns the transfer's progress, completing when TUM.Live took the file
     */
    transfer(uploadUrl: string, file: File): Observable<TumLiveTransferEvent> {
        return new Observable<TumLiveTransferEvent>((subscriber) => {
            const request = new XMLHttpRequest();
            request.open('POST', uploadUrl);
            request.withCredentials = false;
            request.upload.onprogress = (event) => {
                if (event.lengthComputable) {
                    subscriber.next({ type: 'progress', percent: Math.round((event.loaded / event.total) * 100) });
                }
            };
            request.onload = () => {
                if (request.status >= 200 && request.status < 300) {
                    subscriber.next({ type: 'done' });
                    subscriber.complete();
                } else {
                    subscriber.error(new Error(transferError(request)));
                }
            };
            request.onerror = () => subscriber.error(new Error('network'));
            const body = new FormData();
            body.append('file', file, file.name);
            request.send(body);
            return () => {
                if (request.readyState !== XMLHttpRequest.DONE) {
                    request.abort();
                }
            };
        });
    }
}

/** TUM.Live answers errors in the gateway's shape, { code, message }. */
function transferError(request: XMLHttpRequest): string {
    try {
        const message = parseJson<{ message?: unknown } | null>(request.responseText)?.message;
        if (typeof message === 'string' && message) {
            return message;
        }
    } catch {
        // not JSON, for example a proxy's error page
    }
    return `HTTP ${request.status}`;
}
