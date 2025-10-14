import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';

import { AgentChatService } from './agent-chat.service';

describe('AgentChatService', () => {
    let service: AgentChatService;
    let httpMock: HttpTestingController;
    let translateService: jest.Mocked<TranslateService>;

    const mockTranslateService = {
        instant: jest.fn(),
    };

    const mockAccountService = {
        userIdentity: { id: 42, login: 'testuser' },
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                AgentChatService,
                {
                    provide: TranslateService,
                    useValue: mockTranslateService,
                },
                {
                    provide: AccountService,
                    useValue: mockAccountService,
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
        const userId = 42;
        const message = 'Test message';
        const expectedUrl = `api/atlas/agent/courses/${courseId}/chat`;
        const expectedRequestBody = {
            message,
            sessionId: `course_${courseId}_user_${userId}`,
        };

        it('should return AgentChatResponse from successful HTTP response', () => {
            // Arrange
            const mockResponse = {
                message: 'Agent response message',
                sessionId: `course_${courseId}_user_${userId}`,
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

        it('should generate sessionId correctly with userId from AccountService', () => {
            // Arrange
            const testCourseId = 999;
            const expectedSessionId = `course_${testCourseId}_user_${userId}`;
            const mockResponse = {
                message: 'Response',
                sessionId: expectedSessionId,
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            };

            // Act
            service.sendMessage(message, testCourseId).subscribe();

            // Assert
            const req = httpMock.expectOne(`api/atlas/agent/courses/${testCourseId}/chat`);
            expect(req.request.body.sessionId).toBe(expectedSessionId);
            req.flush(mockResponse);
        });

        it('should include all required fields in request body', () => {
            // Act
            service.sendMessage(message, courseId).subscribe();

            // Assert
            const req = httpMock.expectOne(expectedUrl);
            expect(req.request.body).toHaveProperty('message');
            expect(req.request.body).toHaveProperty('sessionId');
            expect(req.request.body.message).toBe(message);
            expect(req.request.body.sessionId).toContain('course_');
            expect(req.request.body.sessionId).toContain('user_');
            req.flush({
                message: 'Response',
                sessionId: req.request.body.sessionId,
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            });
        });

        it('should return error response with all required fields on failure', () => {
            // Arrange
            const fallbackMessage = 'Error occurred';
            translateService.instant.mockReturnValue(fallbackMessage);
            let result: any;

            // Act
            service.sendMessage(message, courseId).subscribe((response) => {
                result = response;
            });

            // Assert
            const req = httpMock.expectOne(expectedUrl);
            req.flush('Error', { status: 400, statusText: 'Bad Request' });

            expect(result).toBeDefined();
            expect(result.message).toBe(fallbackMessage);
            expect(result.sessionId).toBe(`course_${courseId}_user_${userId}`);
            expect(result.timestamp).toBeDefined();
            expect(result.success).toBeFalse();
            expect(result.competenciesModified).toBeFalse();
        });

        it('should handle response with competenciesModified true', () => {
            // Arrange
            const mockResponse = {
                message: 'Competency created',
                sessionId: `course_${courseId}_user_${userId}`,
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: true,
            };
            let result: any;

            // Act
            service.sendMessage(message, courseId).subscribe((response) => {
                result = response;
            });

            // Assert
            const req = httpMock.expectOne(expectedUrl);
            req.flush(mockResponse);

            expect(result.competenciesModified).toBeTrue();
            expect(result.success).toBeTrue();
        });

        it('should make POST request to correct endpoint URL', () => {
            // Arrange
            const testCourseId = 456;

            // Act
            service.sendMessage(message, testCourseId).subscribe();

            // Assert
            const req = httpMock.expectOne(`api/atlas/agent/courses/${testCourseId}/chat`);
            expect(req.request.method).toBe('POST');
            expect(req.request.url).toContain(`courses/${testCourseId}/chat`);
            req.flush({
                message: 'Response',
                sessionId: `course_${testCourseId}_user_${userId}`,
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            });
        });

        it('should handle empty message string', () => {
            // Arrange
            const emptyMessage = '';

            // Act
            service.sendMessage(emptyMessage, courseId).subscribe();

            // Assert
            const req = httpMock.expectOne(expectedUrl);
            expect(req.request.body.message).toBe('');
            req.flush({
                message: 'Response',
                sessionId: `course_${courseId}_user_${userId}`,
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            });
        });
    });
});
