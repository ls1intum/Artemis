import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { AgentChatService } from './agent-chat.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';

describe('AgentChatService', () => {
    let service: AgentChatService;
    let httpMock: HttpTestingController;
    let translateService: jest.Mocked<TranslateService>;
    let accountService: AccountService;

    const mockTranslateService = {
        instant: jest.fn(),
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
                    useClass: MockAccountService,
                },
            ],
        });

        service = TestBed.inject(AgentChatService);
        httpMock = TestBed.inject(HttpTestingController);
        translateService = TestBed.inject(TranslateService) as jest.Mocked<TranslateService>;
        accountService = TestBed.inject(AccountService);

        accountService.userIdentity.set({ id: 42, login: 'testuser' } as User);

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
            const mockResponse = {
                message: 'Agent response message',
                sessionId: `course_${courseId}_user_${userId}`,
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            };
            let result: any;

            service.sendMessage(message, courseId).subscribe((response) => {
                result = response;
            });

            const req = httpMock.expectOne(expectedUrl);
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toEqual(expectedRequestBody);

            req.flush(mockResponse);

            expect(result).toEqual(mockResponse);
        });

        it('should return fallback error response on HTTP error', () => {
            const fallbackMessage = 'Connection error';
            translateService.instant.mockReturnValue(fallbackMessage);
            let result: any;

            service.sendMessage(message, courseId).subscribe((response) => {
                result = response;
            });

            const req = httpMock.expectOne(expectedUrl);
            req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });

            expect(translateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            expect(result.message).toBe(fallbackMessage);
            expect(result.success).toBeFalse();
        });

        it('should use catchError operator properly on network failure', () => {
            const fallbackMessage = 'Network failure handled';
            translateService.instant.mockReturnValue(fallbackMessage);
            let result: any;
            let errorOccurred = false;

            service.sendMessage(message, courseId).subscribe({
                next: (response) => {
                    result = response;
                },
                error: () => {
                    errorOccurred = true;
                },
            });

            const req = httpMock.expectOne(expectedUrl);
            req.error(new ProgressEvent('Network error'));

            // Verify catchError worked - no error thrown, fallback response returned
            expect(errorOccurred).toBeFalse();
            expect(result.message).toBe(fallbackMessage);
            expect(result.success).toBeFalse();
            expect(translateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
        });

        describe('sendMessage - timeout handling', () => {
            const courseId = 123;
            const message = 'Test message';

            it('should handle timeout after 30 seconds', fakeAsync(() => {
                const expectedUrl = `api/atlas/agent/courses/${courseId}/chat`;
                let result: any;

                service.sendMessage(message, courseId).subscribe({
                    next: (response) => {
                        result = response;
                    },
                });

                httpMock.expectOne(expectedUrl);

                // Simulate timeout by advancing time past 30 seconds
                tick(30001);

                // Assert - timeout should trigger catchError which returns fallback response
                expect(result).toBeDefined();
                expect(result.success).toBeFalse();
                expect(result.competenciesModified).toBeFalse();
                expect(translateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            }));
        });

        describe('sessionId generation', () => {
            const courseId = 456;
            const message = 'Test';

            it('should generate sessionId with valid userId', () => {
                accountService.userIdentity.set({ id: 42, login: 'testuser' } as User);
                const expectedSessionId = 'course_456_user_42';

                service.sendMessage(message, courseId).subscribe();

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);

                expect(req.request.body.sessionId).toBe(expectedSessionId);
                req.flush({
                    message: 'response',
                    sessionId: expectedSessionId,
                    timestamp: '2024-01-01T00:00:00Z',
                    success: true,
                    competenciesModified: false,
                });
            });

            it('should throw error when userIdentity is undefined', () => {
                accountService.userIdentity.set(undefined);

                expect(() => service.sendMessage(message, courseId)).toThrow('User must be authenticated to use agent chat');
            });

            it('should throw error when userIdentity.id is undefined', () => {
                accountService.userIdentity.set({ id: undefined, login: 'testuser' } as User);

                expect(() => service.sendMessage(message, courseId)).toThrow('User must be authenticated to use agent chat');
            });
        });

        describe('HTTP request details', () => {
            const courseId = 789;
            const message = 'Test message';

            it('should make POST request to correct URL', () => {
                accountService.userIdentity.set({ id: 42, login: 'testuser' } as User);
                const expectedUrl = `api/atlas/agent/courses/${courseId}/chat`;

                service.sendMessage(message, courseId).subscribe();

                const req = httpMock.expectOne(expectedUrl);
                expect(req.request.method).toBe('POST');
                req.flush({
                    message: 'response',
                    sessionId: 'test',
                    timestamp: '2024-01-01T00:00:00Z',
                    success: true,
                    competenciesModified: false,
                });
            });

            it('should send request with correct body structure', () => {
                accountService.userIdentity.set({ id: 99, login: 'user99' } as User);

                service.sendMessage(message, courseId).subscribe();

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);

                expect(req.request.body).toEqual({
                    message: 'Test message',
                    sessionId: 'course_789_user_99',
                });

                req.flush({
                    message: 'response',
                    sessionId: 'course_789_user_99',
                    timestamp: '2024-01-01T00:00:00Z',
                    success: true,
                    competenciesModified: false,
                });
            });
        });

        describe('Error response handling', () => {
            const courseId = 111;
            const message = 'Test';

            it('should return error response object on catchError', () => {
                accountService.userIdentity.set({ id: 55, login: 'testuser' } as User);
                mockTranslateService.instant.mockReturnValue('Translated error message');
                let result: any;

                service.sendMessage(message, courseId).subscribe({
                    next: (response) => {
                        result = response;
                    },
                });

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                req.error(new ProgressEvent('error'));

                expect(result).toBeDefined();
                expect(result.message).toBe('Translated error message');
                expect(result.success).toBeFalse();
                expect(result.competenciesModified).toBeFalse();
                expect(result.sessionId).toBe('course_111_user_55');
                expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            });

            it('should include timestamp in error response', () => {
                const beforeTime = new Date().toISOString();
                let result: any;

                service.sendMessage(message, courseId).subscribe({
                    next: (response) => {
                        result = response;
                    },
                });

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                req.error(new ProgressEvent('error'));

                const afterTime = new Date().toISOString();

                expect(result.timestamp).toBeDefined();
                expect(result.timestamp >= beforeTime).toBeTruthy();
                expect(result.timestamp <= afterTime).toBeTruthy();
            });

            it('should handle HTTP 400 Bad Request error', () => {
                mockTranslateService.instant.mockReturnValue('Bad request error');
                let result: any;

                service.sendMessage(message, courseId).subscribe({
                    next: (response) => {
                        result = response;
                    },
                });

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                req.flush({ error: 'Invalid request' }, { status: 400, statusText: 'Bad Request' });

                expect(result.success).toBeFalse();
                expect(result.message).toBe('Bad request error');
            });

            it('should handle HTTP 403 Forbidden error', () => {
                mockTranslateService.instant.mockReturnValue('Access denied');
                let result: any;

                service.sendMessage(message, courseId).subscribe({
                    next: (response) => {
                        result = response;
                    },
                });

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                req.flush({ error: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

                expect(result.success).toBeFalse();
                expect(result.message).toBe('Access denied');
            });

            it('should handle HTTP 404 Not Found error', () => {
                mockTranslateService.instant.mockReturnValue('Resource not found');
                let result: any;

                service.sendMessage(message, courseId).subscribe({
                    next: (response) => {
                        result = response;
                    },
                });

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                req.flush({ error: 'Not found' }, { status: 404, statusText: 'Not Found' });

                expect(result.success).toBeFalse();
                expect(result.message).toBe('Resource not found');
            });

            it('should handle HTTP 503 Service Unavailable error', () => {
                mockTranslateService.instant.mockReturnValue('Service unavailable');
                let result: any;

                service.sendMessage(message, courseId).subscribe({
                    next: (response) => {
                        result = response;
                    },
                });

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                req.flush({ error: 'Service unavailable' }, { status: 503, statusText: 'Service Unavailable' });

                expect(result.success).toBeFalse();
                expect(result.message).toBe('Service unavailable');
            });
        });

        describe('Successful response handling', () => {
            const courseId = 222;
            const message = 'Success test';

            it('should handle response with competenciesModified as true', () => {
                const mockResponse = {
                    message: 'Competencies updated',
                    sessionId: 'course_222_user_42',
                    timestamp: '2024-01-01T12:00:00Z',
                    success: true,
                    competenciesModified: true,
                };
                let result: any;

                service.sendMessage(message, courseId).subscribe((response) => {
                    result = response;
                });

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                req.flush(mockResponse);

                expect(result).toEqual(mockResponse);
                expect(result.competenciesModified).toBeTrue();
                expect(result.success).toBeTrue();
            });

            it('should handle response with empty message', () => {
                const mockResponse = {
                    message: '',
                    sessionId: 'course_222_user_42',
                    timestamp: '2024-01-01T12:00:00Z',
                    success: true,
                    competenciesModified: false,
                };
                let result: any;

                service.sendMessage(message, courseId).subscribe((response) => {
                    result = response;
                });

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                req.flush(mockResponse);

                expect(result.message).toBe('');
                expect(result.success).toBeTrue();
            });

            it('should handle response with very long message', () => {
                const longMessage = 'a'.repeat(10000);
                const mockResponse = {
                    message: longMessage,
                    sessionId: 'course_222_user_42',
                    timestamp: '2024-01-01T12:00:00Z',
                    success: true,
                    competenciesModified: false,
                };
                let result: any;

                service.sendMessage(message, courseId).subscribe((response) => {
                    result = response;
                });

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                req.flush(mockResponse);

                expect(result.message).toHaveLength(10000);
                expect(result.success).toBeTrue();
            });

            it('should handle response with special characters in message', () => {
                const specialMessage = 'Test <>&"\'\\n\\t';
                const mockResponse = {
                    message: specialMessage,
                    sessionId: 'course_222_user_42',
                    timestamp: '2024-01-01T12:00:00Z',
                    success: true,
                    competenciesModified: false,
                };
                let result: any;

                service.sendMessage(message, courseId).subscribe((response) => {
                    result = response;
                });

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                req.flush(mockResponse);

                expect(result.message).toBe(specialMessage);
            });

            it('should preserve timestamp format from response', () => {
                const customTimestamp = '2024-12-31T23:59:59.999Z';
                const mockResponse = {
                    message: 'Test',
                    sessionId: 'course_222_user_42',
                    timestamp: customTimestamp,
                    success: true,
                    competenciesModified: false,
                };
                let result: any;

                service.sendMessage(message, courseId).subscribe((response) => {
                    result = response;
                });

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                req.flush(mockResponse);

                expect(result.timestamp).toBe(customTimestamp);
            });
        });

        describe('Multiple concurrent requests', () => {
            const courseId1 = 100;
            const courseId2 = 200;
            const message1 = 'Request 1';
            const message2 = 'Request 2';

            it('should handle multiple concurrent requests independently', () => {
                let result1: any;
                let result2: any;

                service.sendMessage(message1, courseId1).subscribe((response) => {
                    result1 = response;
                });

                service.sendMessage(message2, courseId2).subscribe((response) => {
                    result2 = response;
                });

                const req1 = httpMock.expectOne(`api/atlas/agent/courses/${courseId1}/chat`);
                const req2 = httpMock.expectOne(`api/atlas/agent/courses/${courseId2}/chat`);

                req1.flush({
                    message: 'Response 1',
                    sessionId: 'course_100_user_42',
                    timestamp: '2024-01-01T10:00:00Z',
                    success: true,
                    competenciesModified: false,
                });

                req2.flush({
                    message: 'Response 2',
                    sessionId: 'course_200_user_42',
                    timestamp: '2024-01-01T10:01:00Z',
                    success: true,
                    competenciesModified: true,
                });

                expect(result1.message).toBe('Response 1');
                expect(result1.competenciesModified).toBeFalse();
                expect(result2.message).toBe('Response 2');
                expect(result2.competenciesModified).toBeTrue();
            });

            it('should handle one success and one error independently', () => {
                mockTranslateService.instant.mockReturnValue('Error occurred');
                let result1: any;
                let result2: any;

                service.sendMessage(message1, courseId1).subscribe((response) => {
                    result1 = response;
                });

                service.sendMessage(message2, courseId2).subscribe((response) => {
                    result2 = response;
                });

                const req1 = httpMock.expectOne(`api/atlas/agent/courses/${courseId1}/chat`);
                const req2 = httpMock.expectOne(`api/atlas/agent/courses/${courseId2}/chat`);

                req1.flush({
                    message: 'Success',
                    sessionId: 'course_100_user_42',
                    timestamp: '2024-01-01T10:00:00Z',
                    success: true,
                    competenciesModified: false,
                });

                req2.error(new ProgressEvent('Network error'));

                expect(result1.success).toBeTrue();
                expect(result1.message).toBe('Success');
                expect(result2.success).toBeFalse();
                expect(result2.message).toBe('Error occurred');
            });
        });

        describe('Edge cases and boundary conditions', () => {
            it('should handle message with only whitespace', () => {
                const whitespaceMessage = '   \\n\\t   ';
                const courseId = 333;

                service.sendMessage(whitespaceMessage, courseId).subscribe();

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                expect(req.request.body.message).toBe(whitespaceMessage);

                req.flush({
                    message: 'Response',
                    sessionId: 'course_333_user_42',
                    timestamp: '2024-01-01T10:00:00Z',
                    success: true,
                    competenciesModified: false,
                });
            });

            it('should handle courseId as 0', () => {
                const courseId = 0;
                const message = 'Test';

                service.sendMessage(message, courseId).subscribe();

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                expect(req.request.body.sessionId).toBe('course_0_user_42');

                req.flush({
                    message: 'Response',
                    sessionId: 'course_0_user_42',
                    timestamp: '2024-01-01T10:00:00Z',
                    success: true,
                    competenciesModified: false,
                });
            });

            it('should handle very large courseId', () => {
                const courseId = 999999999;
                const message = 'Test';

                service.sendMessage(message, courseId).subscribe();

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                expect(req.request.body.sessionId).toBe('course_999999999_user_42');

                req.flush({
                    message: 'Response',
                    sessionId: 'course_999999999_user_42',
                    timestamp: '2024-01-01T10:00:00Z',
                    success: true,
                    competenciesModified: false,
                });
            });

            it('should handle empty string message', () => {
                const emptyMessage = '';
                const courseId = 555;

                service.sendMessage(emptyMessage, courseId).subscribe();

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                expect(req.request.body.message).toBe('');

                req.flush({
                    message: 'Response',
                    sessionId: 'course_555_user_42',
                    timestamp: '2024-01-01T10:00:00Z',
                    success: true,
                    competenciesModified: false,
                });
            });
        });

        describe('SessionId consistency', () => {
            it('should use same sessionId for same course and user', () => {
                const courseId = 777;
                const message1 = 'First message';
                const message2 = 'Second message';

                service.sendMessage(message1, courseId).subscribe();
                const req1 = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                const sessionId1 = req1.request.body.sessionId;
                req1.flush({
                    message: 'Response 1',
                    sessionId: sessionId1,
                    timestamp: '2024-01-01T10:00:00Z',
                    success: true,
                    competenciesModified: false,
                });

                service.sendMessage(message2, courseId).subscribe();
                const req2 = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                const sessionId2 = req2.request.body.sessionId;
                req2.flush({
                    message: 'Response 2',
                    sessionId: sessionId2,
                    timestamp: '2024-01-01T10:01:00Z',
                    success: true,
                    competenciesModified: false,
                });

                expect(sessionId1).toBe(sessionId2);
            });

            it('should use different sessionId for different courses', () => {
                const courseId1 = 888;
                const courseId2 = 999;
                const message = 'Test';

                service.sendMessage(message, courseId1).subscribe();
                const req1 = httpMock.expectOne(`api/atlas/agent/courses/${courseId1}/chat`);
                const sessionId1 = req1.request.body.sessionId;
                req1.flush({
                    message: 'Response 1',
                    sessionId: sessionId1,
                    timestamp: '2024-01-01T10:00:00Z',
                    success: true,
                    competenciesModified: false,
                });

                service.sendMessage(message, courseId2).subscribe();
                const req2 = httpMock.expectOne(`api/atlas/agent/courses/${courseId2}/chat`);
                const sessionId2 = req2.request.body.sessionId;
                req2.flush({
                    message: 'Response 2',
                    sessionId: sessionId2,
                    timestamp: '2024-01-01T10:01:00Z',
                    success: true,
                    competenciesModified: false,
                });

                expect(sessionId1).not.toBe(sessionId2);
                expect(sessionId1).toContain('course_888');
                expect(sessionId2).toContain('course_999');
            });

            it('should use different sessionId for different users on same course', () => {
                const courseId = 1000;
                const message = 'Test';

                accountService.userIdentity.set({ id: 10, login: 'user10' } as User);
                service.sendMessage(message, courseId).subscribe();
                const req1 = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                const sessionId1 = req1.request.body.sessionId;
                req1.flush({
                    message: 'Response 1',
                    sessionId: sessionId1,
                    timestamp: '2024-01-01T10:00:00Z',
                    success: true,
                    competenciesModified: false,
                });

                accountService.userIdentity.set({ id: 20, login: 'user20' } as User);
                service.sendMessage(message, courseId).subscribe();
                const req2 = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                const sessionId2 = req2.request.body.sessionId;
                req2.flush({
                    message: 'Response 2',
                    sessionId: sessionId2,
                    timestamp: '2024-01-01T10:01:00Z',
                    success: true,
                    competenciesModified: false,
                });

                expect(sessionId1).not.toBe(sessionId2);
                expect(sessionId1).toContain('user_10');
                expect(sessionId2).toContain('user_20');
            });
        });

        describe('TranslateService integration', () => {
            it('should call translateService.instant with correct key on error', () => {
                const message = 'Test';
                const courseId = 123;
                mockTranslateService.instant.mockReturnValue('Translated error');

                service.sendMessage(message, courseId).subscribe();

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);
                req.error(new ProgressEvent('Error'));

                expect(mockTranslateService.instant).toHaveBeenCalledOnce();
                expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            });

            it('should use translated message in error response', () => {
                const translatedError = 'Connexion échouée';
                mockTranslateService.instant.mockReturnValue(translatedError);
                let result: any;

                service.sendMessage('Test', 123).subscribe((response) => {
                    result = response;
                });

                const req = httpMock.expectOne('api/atlas/agent/courses/123/chat');
                req.error(new ProgressEvent('Error'));

                expect(result.message).toBe(translatedError);
            });
        });
    });
});
