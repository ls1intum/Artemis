import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';

import { AgentChatService } from './agent-chat.service';

describe('AgentChatService', () => {
    let service: AgentChatService;
    let httpMock: HttpTestingController;
    let translateService: jest.Mocked<TranslateService>;

    const mockTranslateService = {
        instant: jest.fn(),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                AgentChatService,
                {
                    provide: TranslateService,
                    useValue: mockTranslateService,
                },
            ],
        });

        service = TestBed.inject(AgentChatService);
        httpMock = TestBed.inject(HttpTestingController);
        translateService = TestBed.inject(TranslateService) as jest.Mocked<TranslateService>;

        // Reset mocks
        jest.clearAllMocks();
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('sendMessage', () => {
        const courseId = 123;
        const message = 'Test message';
        const expectedUrl = `api/atlas/agent/courses/${courseId}/chat`;
        const expectedRequestBody = {
            message,
            sessionId: `course_${courseId}`,
        };

        it('should return AgentChatResponse from successful HTTP response', () => {
            // Arrange
            const mockResponse = {
                message: 'Agent response message',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            };
            let result: any;

            // Act
            service.sendMessage(message, courseId).subscribe((response) => {
                result = response;
            });

            // Assert
            const req = httpMock.expectOne(expectedUrl);
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toEqual(expectedRequestBody);

            req.flush(mockResponse);

            expect(result).toEqual(mockResponse);
        });

        it('should return fallback error response on HTTP error', () => {
            // Arrange
            const fallbackMessage = 'Connection error';
            translateService.instant.mockReturnValue(fallbackMessage);
            let result: any;

            // Act
            service.sendMessage(message, courseId).subscribe((response) => {
                result = response;
            });

            // Assert
            const req = httpMock.expectOne(expectedUrl);
            req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });

            expect(translateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            expect(result.message).toBe(fallbackMessage);
            expect(result.success).toBeFalse();
        });

        it('should use catchError operator properly on network failure', () => {
            // Arrange
            const fallbackMessage = 'Network failure handled';
            translateService.instant.mockReturnValue(fallbackMessage);
            let result: any;
            let errorOccurred = false;

            // Act
            service.sendMessage(message, courseId).subscribe({
                next: (response) => {
                    result = response;
                },
                error: () => {
                    errorOccurred = true;
                },
            });

            // Assert
            const req = httpMock.expectOne(expectedUrl);
            req.error(new ProgressEvent('Network error'));

            // Verify catchError worked - no error thrown, fallback response returned
            expect(errorOccurred).toBeFalse();
            expect(result.message).toBe(fallbackMessage);
            expect(result.success).toBeFalse();
            expect(translateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
        });
    });

    describe('getHistory', () => {
        const courseId = 456;
        const expectedUrl = `api/atlas/agent/courses/${courseId}/history`;

        it('should return chat history from successful HTTP response', () => {
            // Arrange
            const mockHistory = [
                {
                    role: 'user',
                    content: 'Course ID: 456\n\nWhat competencies should I create?',
                },
                {
                    role: 'assistant',
                    content: 'Here are some suggested competencies...',
                },
            ];
            let result: any;

            // Act
            service.getHistory(courseId).subscribe((history) => {
                result = history;
            });

            // Assert
            const req = httpMock.expectOne(expectedUrl);
            expect(req.request.method).toBe('GET');

            req.flush(mockHistory);

            expect(result).toEqual(mockHistory);
            expect(result).toHaveLength(2);
        });

        it('should return empty array on HTTP error', () => {
            // Arrange
            let result: any;

            // Act
            service.getHistory(courseId).subscribe((history) => {
                result = history;
            });

            // Assert
            const req = httpMock.expectOne(expectedUrl);
            req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });

            expect(result).toEqual([]);
        });

        it('should return empty array on network failure', () => {
            // Arrange
            let result: any;
            let errorOccurred = false;

            // Act
            service.getHistory(courseId).subscribe({
                next: (history) => {
                    result = history;
                },
                error: () => {
                    errorOccurred = true;
                },
            });

            // Assert
            const req = httpMock.expectOne(expectedUrl);
            req.error(new ProgressEvent('Network error'));

            // Verify catchError worked - no error thrown, empty array returned
            expect(errorOccurred).toBeFalse();
            expect(result).toEqual([]);
        });

        it('should handle empty history response', () => {
            // Arrange
            let result: any;

            // Act
            service.getHistory(courseId).subscribe((history) => {
                result = history;
            });

            // Assert
            const req = httpMock.expectOne(expectedUrl);
            req.flush([]);

            expect(result).toEqual([]);
        });
    });
});
