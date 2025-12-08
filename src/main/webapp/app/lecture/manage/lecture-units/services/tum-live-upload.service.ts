import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';

/**
 * Response from TUM Live authentication.
 */
export interface TumLiveAuthResponse {
    success: boolean;
    token?: string;
    expires?: string;
    user?: TumLiveUser;
    courses?: TumLiveCourse[];
    error?: string;
}

/**
 * TUM Live user information.
 */
export interface TumLiveUser {
    id: number;
    name: string;
    email: string;
    role: string;
}

/**
 * TUM Live course information.
 */
export interface TumLiveCourse {
    id: number;
    name: string;
    slug: string;
    year: number;
    term: string;
    uploadUrl?: string;
    description?: string;
}

/**
 * Response from TUM Live video upload.
 */
export interface TumLiveUploadResponse {
    success: boolean;
    message?: string;
    error?: string;
    videoUrl?: string; // Full watch URL
    embedUrl?: string; // Embeddable URL for iframe
    streamId?: number; // TUM Live stream ID
}

/**
 * Service for uploading videos to TUM Live.
 */
@Injectable({
    providedIn: 'root',
})
export class TumLiveUploadService {
    private readonly httpClient = inject(HttpClient);
    private readonly resourceUrl = 'api/tumlive';

    /**
     * Check if TUM Live upload service is configured and available.
     * @returns Observable<boolean> - true if service is available
     */
    checkStatus(): Observable<boolean> {
        return this.httpClient.get(`${this.resourceUrl}/status`, { observe: 'response' }).pipe(
            map((response) => response.status === 200),
            catchError(() => of(false)),
        );
    }

    /**
     * Authenticate with TUM Live using SSO (Single Sign-On).
     * The user must already be authenticated in Artemis.
     * @returns Observable<TumLiveAuthResponse> - authentication response with token and courses
     */
    authenticate(): Observable<TumLiveAuthResponse> {
        return this.httpClient.post<TumLiveAuthResponse>(`${this.resourceUrl}/auth`, {});
    }

    /**
     * Authenticate with TUM Live using manual credentials.
     * For users whose TUM Live credentials differ from their Artemis account.
     * @param username - TUM Live username
     * @param password - TUM Live password
     * @returns Observable<TumLiveAuthResponse> - authentication response with token and courses
     */
    authenticateManual(username: string, password: string): Observable<TumLiveAuthResponse> {
        return this.httpClient.post<TumLiveAuthResponse>(`${this.resourceUrl}/auth/manual`, { username, password });
    }

    /**
     * Upload a video file to TUM Live.
     * @param token - Authentication token from successful login
     * @param courseId - TUM Live course ID to upload to
     * @param video - Video file to upload
     * @param title - Video title
     * @param description - Video description (optional)
     * @returns Observable<TumLiveUploadResponse> - upload response
     */
    uploadVideo(token: string, courseId: number, video: File, title: string, description?: string): Observable<TumLiveUploadResponse> {
        const formData = new FormData();
        formData.append('video', video);

        let url = `${this.resourceUrl}/upload?token=${encodeURIComponent(token)}&courseId=${courseId}&title=${encodeURIComponent(title)}`;
        if (description) {
            url += `&description=${encodeURIComponent(description)}`;
        }

        return this.httpClient.post<TumLiveUploadResponse>(url, formData, {
            headers: { 'ngsw-bypass': 'true' },
        });
    }
}
