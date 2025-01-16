import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ArtemisTestModule } from '../test.module';

import { RephraseService } from 'app/shared/monaco-editor/rephrase.service';
import { BehaviorSubject } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import RephrasingVariant from '../../../../main/webapp/app/shared/monaco-editor/model/rephrasing-variant';

describe('Rephrase Service', () => {
    let httpMock: HttpTestingController;
    let service: RephraseService;

    beforeEach(() => {
        const mockWebsocketService = {
            subscribe: jest.fn(),
            unsubscribe: jest.fn(),
            receive: jest.fn().mockReturnValue(new BehaviorSubject({ result: 'Rephrased Text' })),
        };

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: JhiWebsocketService, useValue: mockWebsocketService }],
        });

        httpMock = TestBed.inject(HttpTestingController);
        service = TestBed.inject(RephraseService);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        it('should trigger rephrasing pipeline and return rephrased text', () => {
            const toBeRephrased = 'OriginalText';
            const rephrasingVariant = RephrasingVariant.FAQ;
            const courseId = 1;

            service.rephraseMarkdown(toBeRephrased, rephrasingVariant, courseId).subscribe((rephrasedText) => {
                expect(rephrasedText).toBe('Rephrased Text');
            });

            const req = httpMock.expectOne({
                url: `api/courses/${courseId}/rephrase-text?toBeRephrased=${toBeRephrased}&variant=${rephrasingVariant}`,
                method: 'POST',
            });

            req.flush(null);
        });

        it('should handle HTTP error correctly', () => {
            const toBeRephrased = 'OriginalText';
            const rephrasingVariant = RephrasingVariant.FAQ;
            const courseId = 1;

            service.rephraseMarkdown(toBeRephrased, rephrasingVariant, courseId).subscribe({
                next: () => {
                    throw new Error('Expected error, but got success response');
                },
                error: (err) => {
                    expect(err).toBe('HTTP Request Error:');
                },
            });

            const req = httpMock.expectOne({
                url: `api/courses/${courseId}/rephrase-text?toBeRephrased=${toBeRephrased}&variant=${rephrasingVariant}`,
                method: 'POST',
            });

            req.flush(null, { status: 400, statusText: 'Bad Request' });
        });
    });
});
