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
        const sessionId = 'test-session-123';
        const expectedUrl = `api/atlas/agent/courses/${courseId}/chat`;
        const expectedRequestBody = {
            message,
            sessionId,
        };

        it('should return message field from successful HTTP response', () => {
            // Arrange
            const mockResponse = {
                message: 'Agent response message',
                sessionId: 'test-session-123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
            };
            let result: string | undefined;

            // Act
            service.sendMessage(message, courseId, sessionId).subscribe((response) => {
                result = response;
            });

            // Assert
            const req = httpMock.expectOne(expectedUrl);
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toEqual(expectedRequestBody);

            req.flush(mockResponse);

            expect(result).toBe('Agent response message');
        });

        it('should return fallback error message when response has empty message', () => {
            // Arrange
            const mockResponse = {
                message: '',
                sessionId: 'test-session-123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
            };
            const fallbackMessage = 'Error occurred';
            translateService.instant.mockReturnValue(fallbackMessage);
            let result: string | undefined;

            // Act
            service.sendMessage(message, courseId, sessionId).subscribe((response) => {
                result = response;
            });

            // Assert
            const req = httpMock.expectOne(expectedUrl);
            req.flush(mockResponse);

            expect(translateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            expect(result).toBe(fallbackMessage);
        });

        it('should return fallback error message when response has null message', () => {
            // Arrange
            const mockResponse = {
                message: null,
                sessionId: 'test-session-123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
            };
            const fallbackMessage = 'Error occurred';
            translateService.instant.mockReturnValue(fallbackMessage);
            let result: string | undefined;

            // Act
            service.sendMessage(message, courseId, sessionId).subscribe((response) => {
                result = response;
            });

            // Assert
            const req = httpMock.expectOne(expectedUrl);
            req.flush(mockResponse);

            expect(translateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            expect(result).toBe(fallbackMessage);
        });

        it('should return fallback error message when response has undefined message', () => {
            // Arrange
            const mockResponse = {
                sessionId: 'test-session-123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                // message is undefined
            };
            const fallbackMessage = 'Error occurred';
            translateService.instant.mockReturnValue(fallbackMessage);
            let result: string | undefined;

            // Act
            service.sendMessage(message, courseId, sessionId).subscribe((response) => {
                result = response;
            });

            // Assert
            const req = httpMock.expectOne(expectedUrl);
            req.flush(mockResponse);

            expect(translateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            expect(result).toBe(fallbackMessage);
        });

        it('should return fallback error message on HTTP error', () => {
            // Arrange
            const fallbackMessage = 'Connection error';
            translateService.instant.mockReturnValue(fallbackMessage);
            let result: string | undefined;

            // Act
            service.sendMessage(message, courseId, sessionId).subscribe((response) => {
                result = response;
            });

            // Assert
            const req = httpMock.expectOne(expectedUrl);
            req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });

            expect(translateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            expect(result).toBe(fallbackMessage);
        });

        it('should return fallback error message on timeout', () => {
            // Arrange
            jest.useFakeTimers();
            const fallbackMessage = 'Timeout error';
            translateService.instant.mockReturnValue(fallbackMessage);
            let result: string | undefined;
            let completed = false;

            // Act
            service.sendMessage(message, courseId, sessionId).subscribe((response) => {
                result = response;
                completed = true;
            });

            // Verify HTTP request is made
            const req = httpMock.expectOne(expectedUrl);
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toEqual(expectedRequestBody);

            // Don't flush the request, instead advance time to trigger timeout
            jest.advanceTimersByTime(30000);

            // Assert
            expect(completed).toBeTrue();
            expect(translateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            expect(result).toBe(fallbackMessage);

            jest.useRealTimers();
        });

        it('should handle request without sessionId', () => {
            // Arrange
            const mockResponse = {
                message: 'Response without session',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
            };
            const expectedRequestBodyWithoutSession = {
                message,
                sessionId: undefined,
            };
            let result: string | undefined;

            // Act
            service.sendMessage(message, courseId).subscribe((response) => {
                result = response;
            });

            // Assert
            const req = httpMock.expectOne(expectedUrl);
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toEqual(expectedRequestBodyWithoutSession);

            req.flush(mockResponse);

            expect(result).toBe('Response without session');
        });

        it('should use map operator to extract message field correctly', () => {
            // Arrange
            const mockResponse = {
                message: 'Mapped message content',
                sessionId: 'test-session-123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                extraField: 'should be ignored',
            };
            let result: string | undefined;

            // Act
            service.sendMessage(message, courseId, sessionId).subscribe((response) => {
                result = response;
            });

            // Assert
            const req = httpMock.expectOne(expectedUrl);
            req.flush(mockResponse);

            // Verify only the message field is returned, other fields are ignored
            expect(result).toBe('Mapped message content');
            expect(result).not.toContain('extraField');
        });

        it('should use catchError operator properly on network failure', () => {
            // Arrange
            const fallbackMessage = 'Network failure handled';
            translateService.instant.mockReturnValue(fallbackMessage);
            let result: string | undefined;
            let errorOccurred = false;

            // Act
            service.sendMessage(message, courseId, sessionId).subscribe({
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

            // Verify catchError worked - no error thrown, fallback message returned
            expect(errorOccurred).toBeFalse();
            expect(result).toBe(fallbackMessage);
            expect(translateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
        });
    });
});
