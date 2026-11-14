import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { LectureTranscriptionApi } from 'app/openapi/api/lecture-transcription-api';
import type { LectureTranscription } from 'app/openapi/model/lecture-transcription';

describe('LectureTranscriptionService', () => {
    let service: LectureTranscriptionService;
    let api: { getTranscript: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        api = { getTranscript: vi.fn() };
        TestBed.configureTestingModule({ providers: [{ provide: LectureTranscriptionApi, useValue: api }] });
        service = TestBed.inject(LectureTranscriptionService);
    });

    it('should get the transcription of a lecture unit', () => {
        const transcription: LectureTranscription = { lectureUnitId: 1, language: 'en', segments: [] };
        api.getTranscript.mockReturnValue(of(transcription));
        let result: LectureTranscription | undefined;
        service.getTranscription(1).subscribe((r) => (result = r));
        expect(api.getTranscript).toHaveBeenCalledWith(1);
        expect(result).toEqual(transcription);
    });

    it('should return undefined on get transcription error', () => {
        api.getTranscript.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
        let result: LectureTranscription | undefined = { lectureUnitId: 1 };
        service.getTranscription(1).subscribe((r) => (result = r));
        expect(result).toBeUndefined();
    });
});
