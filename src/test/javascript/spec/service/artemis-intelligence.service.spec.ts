import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ArtemisTestModule } from '../test.module';

import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { BehaviorSubject } from 'rxjs';
import { WebsocketService } from 'app/core/websocket/websocket.service';
import RewritingVariant from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-variant';

describe('ArtemisIntelligenceService', () => {
    let httpMock: HttpTestingController;
    let service: ArtemisIntelligenceService;

    beforeEach(() => {
        const mockWebsocketService = {
            subscribe: jest.fn(),
            unsubscribe: jest.fn(),
            receive: jest.fn().mockReturnValue(new BehaviorSubject({ result: 'Rewritten Text' })),
        };

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: WebsocketService, useValue: mockWebsocketService }],
        });

        httpMock = TestBed.inject(HttpTestingController);
        service = TestBed.inject(ArtemisIntelligenceService);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        it('should trigger rewriting pipeline and return rewritten text', () => {
            const toBeRewritten = 'OriginalText';
            const rewritingVariant = RewritingVariant.FAQ;
            const courseId = 1;

            service.rewrite(toBeRewritten, rewritingVariant, courseId).subscribe((rewrittenText) => {
                expect(rewrittenText).toBe('Rewritten Text');
            });

            const req = httpMock.expectOne({
                url: `api/courses/${courseId}/rewrite-text`,
                method: 'POST',
            });

            req.flush(null);
        });

        it('should handle HTTP error correctly', () => {
            const toBeRewritten = 'OriginalText';
            const rewritingVariant = RewritingVariant.FAQ;
            const courseId = 1;

            service.rewrite(toBeRewritten, rewritingVariant, courseId).subscribe({
                next: () => {
                    throw new Error('Expected error, but got success response');
                },
                error: (err) => {
                    expect(err).toBe('HTTP Request Error:');
                },
            });

            const req = httpMock.expectOne({
                url: `api/courses/${courseId}/rewrite-text`,
                method: 'POST',
            });

            req.flush(null, { status: 400, statusText: 'Bad Request' });
        });
    });
});
