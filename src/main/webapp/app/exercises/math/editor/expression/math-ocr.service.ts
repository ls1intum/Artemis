import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, from } from 'rxjs';
import { switchMap } from 'rxjs/operators';

export interface MathOcrProcessResult {
    text: string;
    latex: string;
    confidence: number;
    error: string;
}

interface MathOcrProcessData {
    image?: string;
    strokes?: any;
}

interface MathOcrTokenResult {
    token: string;
}

interface Strokes {
    x: number[][];
    y: number[][];
}

@Injectable()
export class MathOcrService {
    constructor(private http: HttpClient) {}

    /**
     * Get the expression from the image data
     *
     * @param exerciseId the id of the exercise
     * @param file
     */
    processImage(exerciseId: number, file: File) {
        return from(fileToBase64(file)).pipe(switchMap((image) => this.process(exerciseId, { image })));
    }

    /**
     * Get the expression from the strokes data
     *
     * @param exerciseId the id of the exercise
     * @param strokes
     */
    processStrokes(exerciseId: number, strokes: Strokes) {
        return this.process(exerciseId, { strokes });
    }

    /**
     * Get a short-lived client token for the Mathpix OCR API.
     * Using this token, the client can directly call the API,
     * reducing RTT and server load.
     *
     * @param exerciseId the id of the exercise
     * @private
     */
    private getClientToken(exerciseId: number): Observable<HttpResponse<MathOcrTokenResult>> {
        return this.http.post<MathOcrTokenResult>(`${this.getRequestUrl(exerciseId)}/token`, {}, { observe: 'response' });
    }

    /**
     * Get the expression from the image or strokes data
     *
     * @param exerciseId the id of the exercise
     * @param data the image or strokes data
     * @private
     */
    private process(exerciseId: number, data: MathOcrProcessData): Observable<HttpResponse<MathOcrProcessResult>> {
        return this.http.post<MathOcrProcessResult>(`${this.getRequestUrl(exerciseId)}/process`, data, { observe: 'response' });
    }

    /**
     * Get the request url for the given exercise
     *
     * @param exerciseId
     * @private
     */
    private getRequestUrl(exerciseId: number): string {
        return `/api/math-exercises/${exerciseId}/editor/ocr`;
    }
}

/**
 * Convert a file to a base64 string
 * @param file
 */
const fileToBase64 = async (file: File): Promise<string> =>
    new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result as string);
        reader.onerror = (error) => reject(error);
        reader.readAsDataURL(file);
    });
