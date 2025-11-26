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
        const message = 'Test message';
        const expectedUrl = `api/atlas/agent/courses/${courseId}/chat`;
        const expectedRequestBody = {
            message,
        };

        it('should return AgentChatResponse from successful HTTP response', () => {
            const mockResponse = {
                message: 'Agent response message',
                timestamp: '2024-01-01T00:00:00Z',
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
                expect(result.competenciesModified).toBeFalse();
                expect(translateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            }));
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
                    timestamp: '2024-01-01T00:00:00Z',
                    competenciesModified: false,
                });
            });

            it('should send request with correct body structure', () => {
                accountService.userIdentity.set({ id: 99, login: 'user99' } as User);

                service.sendMessage(message, courseId).subscribe();

                const req = httpMock.expectOne(`api/atlas/agent/courses/${courseId}/chat`);

                expect(req.request.body).toEqual({
                    message: 'Test message',
                });

                req.flush({
                    message: 'response',
                    timestamp: '2024-01-01T00:00:00Z',
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
                expect(result.competenciesModified).toBeFalse();
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

                expect(result.message).toBe('Service unavailable');
            });
        });

        describe('Successful response handling', () => {
            const courseId = 222;
            const message = 'Success test';

            it('should handle response with competenciesModified as true', () => {
                const mockResponse = {
                    message: 'Competencies updated',
                    timestamp: '2024-01-01T12:00:00Z',
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

    describe('getConversationHistory', () => {
        const courseId = 123;
        const expectedUrl = `api/atlas/agent/courses/${courseId}/chat/history`;

        it('should fetch conversation history successfully', () => {
            const mockHistory = [
                { content: 'Hello', isUser: true },
                { content: 'Hi there!', isUser: false },
                { content: 'How are you?', isUser: true },
            ];
            let result: any;

            service.getConversationHistory(courseId).subscribe((history) => {
                result = history;
            });

            const req = httpMock.expectOne(expectedUrl);
            expect(req.request.method).toBe('GET');

            req.flush(mockHistory);

            expect(result).toEqual(mockHistory);
        });

        it('should return empty array on HTTP error', () => {
            let result: any;

            service.getConversationHistory(courseId).subscribe((history) => {
                result = history;
            });

            const req = httpMock.expectOne(expectedUrl);
            req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });

            expect(result).toEqual([]);
        });

        it('should return empty array on network failure', () => {
            let result: any;
            let errorOccurred = false;

            service.getConversationHistory(courseId).subscribe({
                next: (history) => {
                    result = history;
                },
                error: () => {
                    errorOccurred = true;
                },
            });

            const req = httpMock.expectOne(expectedUrl);
            req.error(new ProgressEvent('Network error'));

            // Verify catchError worked - no error thrown, empty array returned
            expect(errorOccurred).toBeFalse();
            expect(result).toEqual([]);
        });

        it('should handle empty history response', () => {
            let result: any;

            service.getConversationHistory(courseId).subscribe((history) => {
                result = history;
            });

            const req = httpMock.expectOne(expectedUrl);
            req.flush([]);

            expect(result).toEqual([]);
        });

        it('should make GET request to correct URL with different courseId', () => {
            const differentCourseId = 456;
            const differentUrl = `api/atlas/agent/courses/${differentCourseId}/chat/history`;

            service.getConversationHistory(differentCourseId).subscribe();

            const req = httpMock.expectOne(differentUrl);
            expect(req.request.method).toBe('GET');
            req.flush([]);
        });

        it('should fetch conversation history with single competency preview', () => {
            const mockHistoryWithPreview = [
                { content: 'Create a competency for OOP', isUser: true },
                {
                    content: "Here's your competency preview",
                    isUser: false,
                    competencyPreview: {
                        preview: true,
                        competency: {
                            title: 'Object-Oriented Programming',
                            description: 'OOP fundamentals',
                            taxonomy: 'UNDERSTAND',
                            icon: 'comments',
                        },
                    },
                },
            ];
            let result: any;

            service.getConversationHistory(courseId).subscribe((history) => {
                result = history;
            });

            const req = httpMock.expectOne(expectedUrl);
            req.flush(mockHistoryWithPreview);

            expect(result).toHaveLength(2);
            expect(result[0].content).toBe('Create a competency for OOP');
            expect(result[0].isUser).toBeTrue();
            expect(result[0].competencyPreview).toBeUndefined();

            expect(result[1].content).toBe("Here's your competency preview");
            expect(result[1].isUser).toBeFalse();
            expect(result[1].competencyPreview).toBeDefined();
            expect(result[1].competencyPreview.preview).toBeTrue();
            expect(result[1].competencyPreview.competency.title).toBe('Object-Oriented Programming');
        });

        it('should fetch conversation history with batch competency preview', () => {
            const mockHistoryWithBatchPreview = [
                { content: 'Create multiple competencies', isUser: true },
                {
                    content: 'Here are multiple competencies',
                    isUser: false,
                    batchCompetencyPreview: {
                        batchPreview: true,
                        count: 2,
                        competencies: [
                            { title: 'Comp 1', description: 'Desc 1', taxonomy: 'REMEMBER', icon: 'brain' },
                            { title: 'Comp 2', description: 'Desc 2', taxonomy: 'APPLY', icon: 'pen-fancy' },
                        ],
                    },
                },
            ];
            let result: any;

            service.getConversationHistory(courseId).subscribe((history) => {
                result = history;
            });

            const req = httpMock.expectOne(expectedUrl);
            req.flush(mockHistoryWithBatchPreview);

            expect(result).toHaveLength(2);
            expect(result[1].batchCompetencyPreview).toBeDefined();
            expect(result[1].batchCompetencyPreview.batchPreview).toBeTrue();
            expect(result[1].batchCompetencyPreview.count).toBe(2);
            expect(result[1].batchCompetencyPreview.competencies).toHaveLength(2);
            expect(result[1].batchCompetencyPreview.competencies[0].title).toBe('Comp 1');
        });

        it('should fetch conversation history with mixed messages', () => {
            const mockMixedHistory = [
                { content: 'User message 1', isUser: true },
                {
                    content: 'Preview response',
                    isUser: false,
                    competencyPreview: {
                        preview: true,
                        competency: {
                            title: 'Test',
                            description: 'Test desc',
                            taxonomy: 'APPLY',
                            icon: 'pen-fancy',
                        },
                    },
                },
                { content: 'User message 2', isUser: true },
                { content: 'Normal response without preview', isUser: false },
                { content: 'User message 3', isUser: true },
                {
                    content: 'Batch response',
                    isUser: false,
                    batchCompetencyPreview: {
                        batchPreview: true,
                        count: 1,
                        competencies: [
                            {
                                title: 'Batch Comp',
                                description: 'Batch desc',
                                taxonomy: 'ANALYZE',
                                icon: 'magnifying-glass',
                            },
                        ],
                    },
                },
            ];
            let result: any;

            service.getConversationHistory(courseId).subscribe((history) => {
                result = history;
            });

            const req = httpMock.expectOne(expectedUrl);
            req.flush(mockMixedHistory);

            expect(result).toHaveLength(6);
            expect(result[0].isUser).toBeTrue();
            expect(result[1].competencyPreview).toBeDefined();
            expect(result[3].competencyPreview).toBeUndefined();
            expect(result[3].batchCompetencyPreview).toBeUndefined();
            expect(result[5].batchCompetencyPreview).toBeDefined();
        });

        it('should handle history with competencyId for update operations', () => {
            const mockHistoryWithUpdate = [
                { content: 'Update competency 42', isUser: true },
                {
                    content: 'Updated competency preview',
                    isUser: false,
                    competencyPreview: {
                        preview: true,
                        competencyId: 42,
                        competency: {
                            title: 'Updated Title',
                            description: 'Updated description',
                            taxonomy: 'ANALYZE',
                            icon: 'magnifying-glass',
                        },
                    },
                },
            ];
            let result: any;

            service.getConversationHistory(courseId).subscribe((history) => {
                result = history;
            });

            const req = httpMock.expectOne(expectedUrl);
            req.flush(mockHistoryWithUpdate);

            expect(result[1].competencyPreview.competencyId).toBe(42);
            expect(result[1].competencyPreview.competency.title).toBe('Updated Title');
        });

        it('should handle history with viewOnly flag', () => {
            const mockHistoryWithViewOnly = [
                { content: 'Show me competency', isUser: true },
                {
                    content: 'Preview (view only)',
                    isUser: false,
                    competencyPreview: {
                        preview: true,
                        viewOnly: true,
                        competency: {
                            title: 'Read-only Competency',
                            description: 'For viewing only',
                            taxonomy: 'UNDERSTAND',
                            icon: 'brain',
                        },
                    },
                },
            ];
            let result: any;

            service.getConversationHistory(courseId).subscribe((history) => {
                result = history;
            });

            const req = httpMock.expectOne(expectedUrl);
            req.flush(mockHistoryWithViewOnly);

            expect(result[1].competencyPreview.viewOnly).toBeTrue();
        });

        it('should handle history with both preview types in batch', () => {
            const mockHistoryWithBatchViewOnly = [
                { content: 'Show competencies', isUser: true },
                {
                    content: 'Batch preview',
                    isUser: false,
                    batchCompetencyPreview: {
                        batchPreview: true,
                        count: 2,
                        viewOnly: true,
                        competencies: [
                            { title: 'Preview 1', description: 'Desc 1', taxonomy: 'REMEMBER', icon: 'brain' },
                            { title: 'Preview 2', description: 'Desc 2', taxonomy: 'UNDERSTAND', icon: 'comments' },
                        ],
                    },
                },
            ];
            let result: any;

            service.getConversationHistory(courseId).subscribe((history) => {
                result = history;
            });

            const req = httpMock.expectOne(expectedUrl);
            req.flush(mockHistoryWithBatchViewOnly);

            expect(result[1].batchCompetencyPreview.viewOnly).toBeTrue();
            expect(result[1].batchCompetencyPreview.competencies).toHaveLength(2);
        });

        it('should handle history with empty competencies array in batch', () => {
            const mockHistoryWithEmptyBatch = [
                { content: 'Create nothing', isUser: true },
                {
                    content: 'Empty batch',
                    isUser: false,
                    batchCompetencyPreview: {
                        batchPreview: true,
                        count: 0,
                        competencies: [],
                    },
                },
            ];
            let result: any;

            service.getConversationHistory(courseId).subscribe((history) => {
                result = history;
            });

            const req = httpMock.expectOne(expectedUrl);
            req.flush(mockHistoryWithEmptyBatch);

            expect(result[1].batchCompetencyPreview.count).toBe(0);
            expect(result[1].batchCompetencyPreview.competencies).toHaveLength(0);
        });
    });
});
