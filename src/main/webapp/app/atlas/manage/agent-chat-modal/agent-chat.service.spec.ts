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
        });
    });
});
