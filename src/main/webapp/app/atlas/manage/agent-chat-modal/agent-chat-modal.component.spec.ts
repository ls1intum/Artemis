import { MockDirective, MockPipe } from 'ng-mocks';
import { FormsModule } from '@angular/forms';
import { HttpResponse } from '@angular/common/http';

import { AgentChatModalComponent } from './agent-chat-modal.component';
import { AgentChatResponse, AgentChatService, AgentHistoryMessage } from '../services/agent-chat.service';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChatMessage } from 'app/atlas/shared/entities/chat-message.model';
import { Competency, CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { throwError } from 'rxjs';
import { ElementRef } from '@angular/core';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbActiveModalService } from 'test/helpers/mocks/service/mock-ngb-active-modal.service';

describe('AgentChatModalComponent', () => {
    let fixture: ComponentFixture<AgentChatModalComponent>;
    let component: AgentChatModalComponent;

    let mockAgentChatService: jest.Mocked<AgentChatService>;
    let mockCompetencyService: jest.Mocked<CompetencyService>;
    let mockTranslateService: TranslateService;

    let mockMessagesContainer: ElementRef;
    let mockMessageInput: ElementRef<HTMLTextAreaElement>;
    let mockTextarea: Partial<HTMLTextAreaElement>;
    let message: ChatMessage;

    beforeEach(async () => {
        mockAgentChatService = {
            sendMessage: jest.fn().mockReturnValue(
                of({
                    message: 'Default mocked response',
                    sessionId: 'session',
                    timestamp: new Date().toISOString(),
                    success: true,
                    competenciesModified: false,
                }),
            ),
            getConversationHistory: jest.fn().mockReturnValue(of([])),
        } as unknown as jest.Mocked<AgentChatService>;

        mockCompetencyService = {
            getAll: jest.fn(),
            create: jest.fn(),
            update: jest.fn(),
            delete: jest.fn(),
        } as unknown as jest.Mocked<CompetencyService>;

        const mockStyle = { height: '' } as unknown as CSSStyleDeclaration;
        mockTextarea = {
            focus: jest.fn(),
            style: mockStyle,
            scrollHeight: 100,
        };

        mockMessageInput = {
            nativeElement: mockTextarea as HTMLTextAreaElement,
        } as ElementRef<HTMLTextAreaElement>;

        mockMessagesContainer = {
            nativeElement: {
                scrollTop: 0,
                scrollHeight: 500,
            },
        } as ElementRef;

        await TestBed.configureTestingModule({
            imports: [AgentChatModalComponent, FormsModule],
            declarations: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe, (value: string) => `translated:${value}`)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbActiveModal, useClass: MockNgbActiveModalService },
                { provide: AgentChatService, useValue: mockAgentChatService },
                { provide: CompetencyService, useValue: mockCompetencyService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(AgentChatModalComponent);
        component = fixture.componentInstance;

        mockTranslateService = TestBed.inject(TranslateService);

        component.courseId.set(123);

        const messageInputSignal = () => mockMessageInput;
        const messagesContainerSignal = () => mockMessagesContainer;

        Object.defineProperty(component, 'messageInput', {
            get: () => messageInputSignal,
            configurable: true,
        });
        Object.defineProperty(component, 'messagesContainer', {
            get: () => messagesContainerSignal,
            configurable: true,
        });
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
        jest.clearAllTimers();
        component.messages.set([]);
    });

    describe('Component Initialization', () => {
        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should show welcome message after init when history is empty', () => {
            const welcomeMessage = 'Welcome to the agent chat!';
            jest.spyOn(mockTranslateService, 'instant').mockReturnValue(welcomeMessage);
            component.ngOnInit();

            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.welcome');
            const messages = component.messages();
            expect(messages.length).toBeGreaterThanOrEqual(1);
            expect(messages[0].isUser).toBeFalse();
        });

        it('should load conversation history when available', () => {
            const mockHistory: AgentHistoryMessage[] = [
                { content: 'Previous user message', isUser: true },
                { content: 'Previous agent response', isUser: false },
                { content: 'Another user message', isUser: true },
            ];
            (mockAgentChatService.getConversationHistory as jest.Mock).mockReturnValue(of(mockHistory));
            component.ngOnInit();
            expect(mockAgentChatService.getConversationHistory).toHaveBeenCalledWith(123);
            const messages = component.messages();
            expect(messages).toHaveLength(4);
            expect(messages[1].content).toBe('Previous user message');
            expect(messages[1].isUser).toBeTrue();
            expect(messages[2].content).toBe('Previous agent response');
            expect(messages[2].isUser).toBeFalse();
            expect(messages[3].content).toBe('Another user message');
            expect(messages[3].isUser).toBeTrue();
        });

        it('should show welcome message on history fetch error', () => {
            const welcomeMessage = 'Welcome message on error';
            jest.spyOn(mockTranslateService, 'instant').mockReturnValue(welcomeMessage);
            (mockAgentChatService.getConversationHistory as jest.Mock).mockReturnValue(throwError(() => new Error('Network error')));

            component.ngOnInit();

            expect(mockAgentChatService.getConversationHistory).toHaveBeenCalledWith(123);
            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.welcome');
            const messages = component.messages();
            expect(component.messages().length).toBeGreaterThanOrEqual(2);
            expect(messages[1].content).toBe(welcomeMessage);
            expect(messages[1].isUser).toBeFalse();
        });

        it('should generate unique message IDs for history messages', () => {
            const mockHistory: AgentHistoryMessage[] = [
                { content: 'Message 1', isUser: true },
                { content: 'Message 2', isUser: false },
            ];
            (mockAgentChatService.getConversationHistory as jest.Mock).mockReturnValue(of(mockHistory));

            component.ngOnInit();

            const messageIds = component.messages().map((m) => m.id);
            const uniqueIds = new Set(messageIds);
            expect(uniqueIds.size).toBe(messageIds.length);
        });

        it('should set correct timestamps for history messages', () => {
            const beforeTime = new Date();
            const mockHistory: AgentHistoryMessage[] = [{ content: 'Test message', isUser: true }];
            (mockAgentChatService.getConversationHistory as jest.Mock).mockReturnValue(of(mockHistory));

            component.ngOnInit();
            const afterTime = new Date();

            const messages = component.messages();
            expect(messages.length).toBeGreaterThanOrEqual(2); // welcome + history
            expect(messages[1].timestamp).toBeInstanceOf(Date);
            expect(messages[1].timestamp.getTime()).toBeGreaterThanOrEqual(beforeTime.getTime());
            expect(messages[1].timestamp.getTime()).toBeLessThanOrEqual(afterTime.getTime());
        });
    });

    describe('competency operations', () => {
        it('should handle competency creation', fakeAsync(() => {
            const mockCompetency = new Competency();
            mockCompetency.title = 'Test Competency';
            mockCompetency.description = 'Test Description';
            mockCompetency.taxonomy = CompetencyTaxonomy.ANALYZE;

            const httpResponse = new HttpResponse({ body: mockCompetency, status: 200 });
            (mockCompetencyService.create as jest.Mock).mockReturnValue(of(httpResponse));

            const message: ChatMessage = {
                id: '1',
                content: 'Test',
                isUser: false,
                timestamp: new Date(),
                competencyPreviews: [
                    {
                        title: 'Test Competency',
                        description: 'Test Description',
                        taxonomy: CompetencyTaxonomy.ANALYZE,
                    },
                ],
            };

            component.onCreateCompetencies(message);
            tick();

            expect(mockCompetencyService.create).toHaveBeenCalled();
            expect(mockCompetencyService.create).toHaveBeenCalledOnce();
        }));

        it('should handle competency update', fakeAsync(() => {
            const mockCompetency = new Competency();
            mockCompetency.id = 1;
            mockCompetency.title = 'Updated Competency';
            mockCompetency.description = 'Updated Description';
            mockCompetency.taxonomy = CompetencyTaxonomy.ANALYZE;

            const httpResponse = new HttpResponse({ body: mockCompetency, status: 200 });
            (mockCompetencyService.update as jest.Mock).mockReturnValue(of(httpResponse));

            const message: ChatMessage = {
                id: '2',
                content: 'Test',
                isUser: false,
                timestamp: new Date(),
                competencyPreviews: [
                    {
                        title: 'Updated Competency',
                        description: 'Updated Description',
                        taxonomy: CompetencyTaxonomy.ANALYZE,
                        competencyId: 1,
                    },
                ],
            };

            component.onCreateCompetencies(message);
            tick();

            expect(mockCompetencyService.update).toHaveBeenCalled();
            expect(mockCompetencyService.update).toHaveBeenCalledOnce();
        }));
    });

    describe('agent response handling', () => {
        it('should handle response with message', fakeAsync(() => {
            const response = {
                message: 'Agent response',
                sessionId: 'test-session',
                timestamp: new Date().toISOString(),
                success: true,
                competenciesModified: false,
            };
            (mockAgentChatService.sendMessage as jest.Mock).mockReturnValue(of(response));
            component.currentMessage.set('Test message');
            component['sendMessage']();
            tick();

            expect(component.messages().length).toBeGreaterThanOrEqual(2);
        }));

        it('should handle response with null message by using fallback', fakeAsync(() => {
            const response = {
                message: null as unknown as string,
                sessionId: 'test-session',
                timestamp: new Date().toISOString(),
                success: true,
                competenciesModified: false,
            };
            // The component expects a string fallback from translateService; we mock service to return fallback string when invoked
            jest.spyOn(mockTranslateService, 'instant').mockReturnValue('Fallback error message');
            (mockAgentChatService.sendMessage as jest.Mock).mockReturnValue(of(response));

            component.currentMessage.set('Test message');
            component['sendMessage']();
            tick();

            expect(component.messages().length).toBeGreaterThanOrEqual(2);
        }));

        it('should handle response with undefined message by using fallback', fakeAsync(() => {
            const response = {
                message: undefined as unknown as string,
                sessionId: 'test-session',
                timestamp: new Date().toISOString(),
                success: true,
                competenciesModified: false,
            };
            jest.spyOn(mockTranslateService, 'instant').mockReturnValue('Fallback error message');
            (mockAgentChatService.sendMessage as jest.Mock).mockReturnValue(of(response));

            component.currentMessage.set('Test message');
            component['sendMessage']();
            tick();

            expect(component.messages().length).toBeGreaterThanOrEqual(2);
        }));
    });

    describe('translate service mocking', () => {
        it('should mock translate service instant method correctly', () => {
            const spy = jest.spyOn(mockTranslateService, 'instant').mockReturnValue('translated text');

            const result = mockTranslateService.instant('test.key');
            expect(result).toBe('translated text');
            expect(spy).toHaveBeenCalledWith('test.key');
        });
    });
    describe('batch operations', () => {
        it('should handle batch response correctly', fakeAsync(() => {
            const mockBatchResponse = {
                message: 'Batch completed',
                sessionId: 'test-session',
                timestamp: new Date().toISOString(),
                success: true,
                competenciesModified: true,
            };

            mockAgentChatService.sendMessage.mockReturnValue(of(mockBatchResponse));

            component.currentMessage.set('Create batch competencies');

            component['sendMessage']();
            tick();

            const messages = component.messages();
            expect(messages).toHaveLength(3);

            expect(messages[2].content).toBe('Batch completed');
        }));
    });

    describe('canSendMessage computed signal', () => {
        beforeEach(() => {
            component.isAgentTyping.set(false);
        });

        it('should return false for empty input', () => {
            component.currentMessage.set('');

            expect(component.canSendMessage()).toBeFalse();
        });

        it('should return false for whitespace only input', () => {
            component.currentMessage.set('   \n\t  ');

            expect(component.canSendMessage()).toBeFalse();
        });

        it('should return false for too long input', () => {
            component.currentMessage.set('a'.repeat(component.MAX_MESSAGE_LENGTH + 1));

            expect(component.canSendMessage()).toBeFalse();
        });

        it('should return false when agent is typing', () => {
            component.currentMessage.set('Valid message');
            component.isAgentTyping.set(true);

            expect(component.canSendMessage()).toBeFalse();
        });

        it('should return true for valid input', () => {
            component.currentMessage.set('Valid message');
            component.isAgentTyping.set(false);

            expect(component.canSendMessage()).toBeTrue();
        });

        it('should return true for input at max length limit', () => {
            component.currentMessage.set('a'.repeat(component.MAX_MESSAGE_LENGTH));
            component.isAgentTyping.set(false);

            expect(component.canSendMessage()).toBeTrue();
        });
    });

    describe('sendMessage', () => {
        beforeEach(() => {
            component.currentMessage.set('Test message');
            component.isAgentTyping.set(false);
            component.courseId.set(123);
        });

        it('should send message when Enter key is pressed', () => {
            component.currentMessage.set('Test message');
            component.isAgentTyping.set(false);
            const mockResponse = {
                message: 'Agent response',
                sessionId: 'course_123',
                timestamp: new Date().toISOString(),
                success: true,
                competenciesModified: false,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse as unknown as AgentChatResponse));
            fixture.detectChanges();

            const textarea = fixture.debugElement.nativeElement.querySelector('textarea');
            const enterEvent = new KeyboardEvent('keydown', {
                key: 'Enter',
                shiftKey: false,
            });

            textarea.dispatchEvent(enterEvent);

            expect(mockAgentChatService.sendMessage).toHaveBeenCalledWith('Test message', 123);
        });

        it('should handle service error gracefully', fakeAsync(() => {
            component.currentMessage.set('Test message');
            const errorMessage = 'Connection failed';
            mockAgentChatService.sendMessage.mockReturnValue(throwError(() => new Error('Service error')));
            const translateSpy = jest.spyOn(mockTranslateService, 'instant').mockReturnValue(errorMessage);
            fixture.detectChanges();

            jest.clearAllMocks();
            translateSpy.mockReturnValue(errorMessage);

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();
            tick();
            const messages = component.messages();
            expect(component.isAgentTyping()).toBeFalse();
            expect(translateSpy).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            expect(messages).toHaveLength(3); // Welcome + user message + error message
            expect(messages[2].content).toBe(errorMessage);
            expect(messages[2].isUser).toBeFalse();
        }));

        it('should not send message if canSendMessage is false', () => {
            component.currentMessage.set('');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            expect(mockAgentChatService.sendMessage).not.toHaveBeenCalled();
        });
    });

    describe('Focus behavior', () => {
        it('should focus input after view init', fakeAsync(() => {
            component.ngAfterViewInit();
            tick(10);

            expect(mockTextarea.focus).toHaveBeenCalled();
        }));

        it('should scroll to bottom when shouldScrollToBottom is true', () => {
            component.shouldScrollToBottom.set(true);

            component.ngAfterViewChecked();

            expect(mockMessagesContainer.nativeElement.scrollTop).toBe(500); // scrollHeight value
            expect(component.shouldScrollToBottom()).toBeFalse();
        });

        it('should not scroll when shouldScrollToBottom is false', () => {
            component.shouldScrollToBottom.set(false);
            const originalScrollTop = mockMessagesContainer.nativeElement.scrollTop;

            component.ngAfterViewChecked();

            expect(mockMessagesContainer.nativeElement.scrollTop).toBe(originalScrollTop);
        });
    });

    describe('Template integration', () => {
        beforeEach(() => {
            jest.spyOn(mockTranslateService, 'instant').mockReturnValue('Welcome message');
            mockAgentChatService.getConversationHistory.mockReturnValue(of([]));
            component.ngOnInit();
            fixture.detectChanges();
        });

        it('should display messages in the template', () => {
            const userMessage: ChatMessage = {
                id: '1',
                content: 'User message',
                isUser: true,
                timestamp: new Date(),
            };
            const agentMessage: ChatMessage = {
                id: '2',
                content: 'Agent response',
                isUser: false,
                timestamp: new Date(),
            };
            component.messages.set([userMessage, agentMessage]);

            fixture.changeDetectorRef.detectChanges();

            const messageElements = fixture.debugElement.nativeElement.querySelectorAll('.message-wrapper');
            expect(messageElements).toHaveLength(2);

            const userMessageElement = messageElements[0];
            const agentMessageElement = messageElements[1];

            expect(userMessageElement.classList.contains('user-message')).toBeTrue();
            expect(agentMessageElement.classList.contains('agent-message')).toBeTrue();
        });

        it('should show typing indicator when isAgentTyping is true', () => {
            component.isAgentTyping.set(true);

            fixture.changeDetectorRef.detectChanges();

            const typingIndicator = fixture.debugElement.nativeElement.querySelector('.typing-indicator');
            expect(typingIndicator).toBeTruthy();
        });

        it('should hide typing indicator when isAgentTyping is false', () => {
            component.isAgentTyping.set(false);

            fixture.changeDetectorRef.detectChanges();

            const typingIndicator = fixture.debugElement.nativeElement.querySelector('.typing-indicator');
            expect(typingIndicator).toBeFalsy();
        });

        it('should prevent message sending when agent is typing', () => {
            component.currentMessage.set('Valid message');
            component.isAgentTyping.set(true);

            expect(component.canSendMessage()).toBeFalse();
            expect(component.isAgentTyping()).toBeTrue();
        });

        it('should disable send button when canSendMessage is false', () => {
            component.currentMessage.set('');

            fixture.changeDetectorRef.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            expect(sendButton.disabled).toBeTrue();
        });

        it('should enable send button when canSendMessage is true', () => {
            component.currentMessage.set('Valid message');
            component.isAgentTyping.set(false);

            fixture.changeDetectorRef.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            expect(sendButton.disabled).toBeFalse();
        });

        it('should show character count in template', () => {
            component.currentMessage.set('Test message');

            fixture.changeDetectorRef.detectChanges();

            const charCountElement = fixture.debugElement.nativeElement.querySelector('.text-end');
            expect(charCountElement.textContent.trim()).toContain('12 / 8000');
        });

        it('should show error styling when message is too long', () => {
            component.currentMessage.set('a'.repeat(component.MAX_MESSAGE_LENGTH + 1));

            fixture.changeDetectorRef.detectChanges();

            const charCountElement = fixture.debugElement.nativeElement.querySelector('.text-danger');
            expect(charCountElement).toBeTruthy();

            const errorMessage = fixture.debugElement.nativeElement.querySelector('small.text-danger.mt-1');
            expect(errorMessage).toBeTruthy();
        });
    });

    describe('Modal interaction', () => {
        it('should close modal when closeModal is called', () => {
            const activeModalService = TestBed.inject(NgbActiveModal);
            const closeSpy = jest.spyOn(activeModalService, 'close');
            component['closeModal']();

            expect(closeSpy).toHaveBeenCalled();
        });

        it('should call closeModal when close button is clicked', () => {
            const closeModalSpy = jest.spyOn(component as unknown as Record<string, () => void>, 'closeModal');
            fixture.detectChanges();

            const closeButton = fixture.debugElement.nativeElement.querySelector('.btn-close');
            closeButton.click();

            expect(closeModalSpy).toHaveBeenCalled();
        });
    });

    describe('Competency modification events', () => {
        beforeEach(() => {
            jest.spyOn(mockTranslateService, 'instant').mockReturnValue('welcome');
            mockAgentChatService.getConversationHistory.mockReturnValue(of([]));
            component.ngOnInit();
        });

        it('should emit competencyChanged event when competenciesModified is true', () => {
            component.currentMessage.set('Create a competency for OOP');
            component.isAgentTyping.set(false);
            const mockResponse = {
                message: 'Competency created successfully',
                sessionId: 'course_123',
                timestamp: new Date().toISOString(),
                success: true,
                competenciesModified: true,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse as unknown as AgentChatResponse));
            const emitSpy = jest.spyOn(component.competencyChanged, 'emit');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            expect(emitSpy).toHaveBeenCalledOnce();
        });

        it('should not emit competencyChanged event when competenciesModified is false', () => {
            component.currentMessage.set('What competencies exist?');
            component.isAgentTyping.set(false);
            const mockResponse = {
                message: 'Here are the existing competencies...',
                sessionId: 'course_123',
                timestamp: new Date().toISOString(),
                success: true,
                competenciesModified: false,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse as unknown as AgentChatResponse));
            const emitSpy = jest.spyOn(component.competencyChanged, 'emit');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            expect(emitSpy).not.toHaveBeenCalled();
        });
    });

    describe('Textarea auto-resize behavior', () => {
        it('should auto-resize textarea on input when content exceeds max height', () => {
            Object.defineProperty(mockTextarea, 'scrollHeight', {
                value: 150, // Greater than max height of 120px
                writable: true,
                configurable: true,
            });

            component.onTextareaInput();

            // Height should be set to max height (120px) when scrollHeight exceeds it
            expect(mockTextarea.style!.height).toBe('120px');
        });

        it('should auto-resize textarea on input when content is within max height', () => {
            Object.defineProperty(mockTextarea, 'scrollHeight', {
                value: 80, // Less than max height of 120px
                writable: true,
                configurable: true,
            });

            component.onTextareaInput();

            // Height should be set to scrollHeight when it's less than max height
            expect(mockTextarea.style!.height).toBe('80px');
        });

        it('should handle case when textarea element is not available', () => {
            Object.defineProperty(component, 'messageInput', {
                get: () => () => null,
                configurable: true,
            });
            expect(() => component.onTextareaInput()).not.toThrow();
        });
    });

    describe('Computed signals', () => {
        it('should calculate currentMessageLength correctly', () => {
            component.currentMessage.set('Hello');

            expect(component.currentMessageLength()).toBe(5);
        });

        it('should correctly identify message as too long', () => {
            component.currentMessage.set('a'.repeat(component.MAX_MESSAGE_LENGTH + 1));

            expect(component.isMessageTooLong()).toBeTrue();
        });

        it('should correctly identify message as not too long', () => {
            component.currentMessage.set('a'.repeat(component.MAX_MESSAGE_LENGTH));

            expect(component.isMessageTooLong()).toBeFalse();
        });

        it('should correctly identify empty message as not too long', () => {
            component.currentMessage.set('');

            expect(component.isMessageTooLong()).toBeFalse();
        });
    });

    describe('Message state management', () => {
        it('should clear currentMessage after sending', () => {
            jest.spyOn(mockTranslateService, 'instant').mockReturnValue('welcome');
            mockAgentChatService.getConversationHistory.mockReturnValue(of([]));
            component.ngOnInit();
            component.currentMessage.set('Test message to send');
            component.isAgentTyping.set(false);

            const mockResponse = {
                message: 'Agent response',
                sessionId: 'course_123',
                timestamp: new Date().toISOString(),
                success: true,
                competenciesModified: false,
            };

            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            expect(component.currentMessage()).toBe('');
        });

        it('should set isAgentTyping to true when sending message', () => {
            jest.spyOn(mockTranslateService, 'instant').mockReturnValue('welcome');
            mockAgentChatService.getConversationHistory.mockReturnValue(of([]));
            component.ngOnInit();
            component.currentMessage.set('Test message');
            component.isAgentTyping.set(false);

            mockAgentChatService.sendMessage.mockReturnValue(
                of({
                    message: 'Response',
                    sessionId: 'course_123',
                    timestamp: new Date().toISOString(),
                    success: true,
                    competenciesModified: false,
                }),
            );

            fixture.detectChanges();
            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            expect(component.isAgentTyping()).toBeFalse(); // After response completes
        });

        it('should add user message to messages array', () => {
            jest.spyOn(mockTranslateService, 'instant').mockReturnValue('welcome');
            mockAgentChatService.getConversationHistory.mockReturnValue(of([]));
            component.ngOnInit();
            const initialMessageCount = component.messages().length;
            component.currentMessage.set('User test message');
            component.isAgentTyping.set(false);

            mockAgentChatService.sendMessage.mockReturnValue(
                of({
                    message: 'Agent response',
                    sessionId: 'course_123',
                    timestamp: new Date().toISOString(),
                    success: true,
                    competenciesModified: false,
                }),
            );

            fixture.detectChanges();
            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            expect(component.messages().length).toBeGreaterThan(initialMessageCount);
            const userMessage = component.messages().find((msg) => msg.isUser && msg.content === 'User test message');
            expect(userMessage).toBeDefined();
        });

        describe('Scroll behavior edge cases', () => {
            it('should handle scrollToBottom when messagesContainer is null', () => {
                Object.defineProperty(component, 'messagesContainer', {
                    get: () => () => null,
                    configurable: true,
                });
                component.shouldScrollToBottom.set(true);
                expect(() => component.ngAfterViewChecked()).not.toThrow();
            });

            it('should handle empty response message from service', fakeAsync(() => {
                component.currentMessage.set('Test message');
                component.isAgentTyping.set(false);
                const errorText = 'Error text';
                const translateSpy = jest.spyOn(mockTranslateService, 'instant').mockReturnValue(errorText);
                const mockResponse = {
                    message: '',
                    sessionId: 'course_123',
                    timestamp: new Date().toISOString(),
                    success: true,
                    competenciesModified: false,
                };
                mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse as unknown as AgentChatResponse));
                fixture.detectChanges();

                const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
                sendButton.click();
                tick();
                expect(translateSpy).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            }));

            it('should handle null response message from service', fakeAsync(() => {
                component.currentMessage.set('Test message');
                component.isAgentTyping.set(false);
                const errorText = 'Error text';
                const translateSpy = jest.spyOn(mockTranslateService, 'instant').mockReturnValue(errorText);
                const mockResponse = {
                    message: null,
                    sessionId: 'course_123',
                    timestamp: new Date().toISOString(),
                    success: true,
                    competenciesModified: false,
                };
                mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse as unknown as AgentChatResponse));
                fixture.detectChanges();

                const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
                sendButton.click();
                tick();

                expect(translateSpy).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            }));
        });

        describe('Competency Preview Extraction', () => {
            beforeEach(() => {
                component.ngOnInit();
            });

            it('should extract competency preview from JSON in agent response', () => {
                const mockResponse = {
                    message: "Here's a competency suggestion:",
                    sessionId: 'course_123',
                    timestamp: new Date().toISOString(),
                    success: true,
                    competenciesModified: false,
                    competencyPreviews: [
                        {
                            title: 'Object-Oriented Programming',
                            description: 'Understanding OOP principles',
                            taxonomy: CompetencyTaxonomy.UNDERSTAND,
                            icon: 'comments',
                        },
                    ],
                };
                mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse as unknown as AgentChatResponse));
                component.currentMessage.set('Create OOP competency');
                fixture.detectChanges();

                const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
                sendButton.click();

                const agentMessage = component.messages().find((msg) => !msg.isUser && msg.competencyPreviews && msg.competencyPreviews.length > 0);
                expect(agentMessage).toBeDefined();
                expect(agentMessage?.competencyPreviews?.[0].title).toBe('Object-Oriented Programming');
                expect(agentMessage?.competencyPreviews?.[0].description).toBe('Understanding OOP principles');
                expect(agentMessage?.competencyPreviews?.[0].taxonomy).toBe(CompetencyTaxonomy.UNDERSTAND);
                expect(agentMessage?.competencyPreviews?.[0].icon).toBe('comments');
            });

            it('should extract competency preview from structured response', () => {
                const mockResponse = {
                    message: "Here's a suggestion:",
                    sessionId: 'course_123',
                    timestamp: new Date().toISOString(),
                    success: true,
                    competenciesModified: false,
                    competencyPreviews: [
                        {
                            title: 'Data Structures',
                            description: 'Arrays, lists, trees, and graphs',
                            taxonomy: CompetencyTaxonomy.APPLY,
                            icon: 'pen-fancy',
                        },
                    ],
                };
                mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse as unknown as AgentChatResponse));
                component.currentMessage.set('Create data structures competency');
                fixture.detectChanges();

                const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
                sendButton.click();

                const agentMessage = component.messages().find((msg) => !msg.isUser && msg.competencyPreviews && msg.competencyPreviews.length > 0);
                expect(agentMessage).toBeDefined();
                expect(agentMessage?.competencyPreviews?.[0].title).toBe('Data Structures');
                expect(agentMessage?.competencyPreviews?.[0].taxonomy).toBe(CompetencyTaxonomy.APPLY);
            });

            it('should extract competencyId for update operations', () => {
                const mockResponse = {
                    message: 'Updated competency preview:',
                    sessionId: 'course_123',
                    timestamp: new Date().toISOString(),
                    success: true,
                    competenciesModified: false,
                    competencyPreviews: [
                        {
                            competency: {
                                title: 'Updated Title',
                                description: 'Updated description',
                                taxonomy: CompetencyTaxonomy.UNDERSTAND,
                                icon: 'magnifying-glass',
                            },
                            competencyId: 42,
                        },
                    ],
                };
                mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse as unknown as AgentChatResponse));
                component.currentMessage.set('Update competency 42');
                fixture.detectChanges();

                const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
                sendButton.click();

                const agentMessage = component.messages().find((msg) => !msg.isUser && msg.competencyPreviews && msg.competencyPreviews.length > 0);
                expect(agentMessage).toBeDefined();
                expect(agentMessage?.competencyPreviews?.[0].competencyId).toBe(42);
            });

            it('should extract viewOnly flag when present', () => {
                const mockResponse = {
                    message: 'Preview:',
                    sessionId: 'course_123',
                    timestamp: new Date().toISOString(),
                    success: true,
                    competenciesModified: false,
                    competencyPreviews: [
                        {
                            viewOnly: true,
                            competency: {
                                title: 'Read-only Competency',
                                description: 'For viewing only',
                                taxonomy: CompetencyTaxonomy.UNDERSTAND,
                                icon: 'brain',
                            },
                        },
                    ],
                };
                mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse as unknown as AgentChatResponse));
                component.currentMessage.set('Show me competency');
                fixture.detectChanges();

                const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
                sendButton.click();

                const agentMessage = component.messages().find((msg) => !msg.isUser && msg.competencyPreviews && msg.competencyPreviews.length > 0);
                expect(agentMessage).toBeDefined();
                expect(agentMessage?.competencyPreviews?.[0].viewOnly).toBeTrue();
            });

            it('should not extract preview when no preview is sent', () => {
                const mockResponse = {
                    message: 'Response: This is not a preview',
                    sessionId: 'course_123',
                    timestamp: new Date().toISOString(),
                    success: true,
                    competenciesModified: false,
                };
                mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse as unknown as AgentChatResponse));
                component.currentMessage.set('Some message');
                fixture.detectChanges();

                const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
                sendButton.click();

                const agentMessage = component.messages().find((msg) => !msg.isUser);
                expect(agentMessage).toBeDefined();
                expect(agentMessage?.competencyPreviews).toBeUndefined();
            });

            it('should handle malformed JSON gracefully', () => {
                const mockResponse = {
                    message: 'This is a normal response without JSON',
                    sessionId: 'course_123',
                    timestamp: new Date().toISOString(),
                    success: true,
                    competenciesModified: false,
                };
                mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse as unknown as AgentChatResponse));
                component.currentMessage.set('Normal message');
                fixture.detectChanges();

                const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
                sendButton.click();
                const messages = component.messages();
                const agentMessage = messages[component.messages().length - 1];
                expect(agentMessage).toBeDefined();
                expect(agentMessage?.competencyPreviews).toBeUndefined();
                expect(agentMessage?.content).toBe('This is a normal response without JSON');
            });
        });

        describe('Batch Competency Preview Extraction', () => {
            beforeEach(() => {
                jest.spyOn(mockTranslateService, 'instant').mockReturnValue('Welcome');
                component.ngOnInit();
            });

            it('should extract batch competency preview from structured response', () => {
                const mockResponse = {
                    message: 'Here are multiple competencies:',
                    sessionId: 'course_123',
                    timestamp: new Date().toISOString(),
                    success: true,
                    competenciesModified: false,
                    competencyPreviews: [
                        {
                            title: 'Comp 1',
                            description: 'Desc 1',
                            taxonomy: CompetencyTaxonomy.REMEMBER,
                            icon: 'brain',
                        },
                        {
                            title: 'Comp 2',
                            description: 'Desc 2',
                            taxonomy: CompetencyTaxonomy.UNDERSTAND,
                            icon: 'comments',
                        },
                        {
                            title: 'Comp 3',
                            description: 'Desc 3',
                            taxonomy: CompetencyTaxonomy.APPLY,
                            icon: 'pen-fancy',
                        },
                    ],
                };
                mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse as unknown as AgentChatResponse));
                component.currentMessage.set('Create multiple competencies');
                fixture.detectChanges();

                const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
                sendButton.click();

                const agentMessage = component.messages().find((msg) => !msg.isUser && msg.competencyPreviews);
                expect(agentMessage).toBeDefined();
                expect(agentMessage?.competencyPreviews).toHaveLength(3);
                expect(agentMessage?.competencyPreviews?.[0].title).toBe('Comp 1');
                expect(agentMessage?.competencyPreviews?.[1].title).toBe('Comp 2');
                expect(agentMessage?.competencyPreviews?.[2].title).toBe('Comp 3');
            });

            it('should prioritize batch preview over single preview', () => {
                const mockResponse = {
                    message: 'Batch preview:',
                    sessionId: 'course_123',
                    timestamp: new Date().toISOString(),
                    success: true,
                    competenciesModified: false,
                    competencyPreviews: [
                        {
                            competency: {
                                title: 'Batch 1',
                                description: 'Desc 1',
                                taxonomy: CompetencyTaxonomy.APPLY,
                                icon: 'pen-fancy',
                            },
                        },
                        {
                            competency: {
                                title: 'Batch 2',
                                description: 'Desc 2',
                                taxonomy: CompetencyTaxonomy.ANALYZE,
                                icon: 'magnifying-glass',
                            },
                        },
                    ],
                };
                mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse as unknown as AgentChatResponse));
                component.currentMessage.set('Create batch');
                fixture.detectChanges();

                const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
                sendButton.click();

                const agentMessage = component.messages().find((msg) => !msg.isUser && msg.competencyPreviews);
                expect(agentMessage).toBeDefined();
                expect(agentMessage?.competencyPreviews).toHaveLength(2);
            });

            it('should handle empty batch preview gracefully', () => {
                const mockResponse = {
                    message: 'Response with empty batch',
                    sessionId: 'session',
                    timestamp: new Date().toISOString(),
                    success: true,
                    competenciesModified: false,
                    competencyPreviews: [],
                };
                mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
                component.currentMessage.set('Empty batch');
                fixture.detectChanges();

                const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
                sendButton.click();

                const agentMessage = component.messages().find((msg) => !msg.isUser && msg.competencyPreviews !== undefined);
                expect(agentMessage?.competencyPreviews || []).toHaveLength(0);
            });

            describe('Plan Pending Detection and Approval', () => {
                beforeEach(() => {
                    jest.spyOn(mockTranslateService, 'instant').mockReturnValue('Welcome');
                    component.ngOnInit();
                });

                it('should detect plan pending marker in agent response', () => {
                    const mockResponse = {
                        message: 'Here is my plan:\n1. Step 1\n2. Step 2\n[PLAN_PENDING]',
                        sessionId: 'course_123',
                        timestamp: new Date().toISOString(),
                        success: true,
                        competenciesModified: false,
                    };
                    mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse as unknown as AgentChatResponse));
                    component.currentMessage.set('Create a plan');
                    fixture.detectChanges();

                    const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
                    sendButton.click();

                    const agentMessage = component.messages().find((msg) => !msg.isUser && msg.planPending);
                    expect(agentMessage).toBeDefined();
                    expect(agentMessage?.planPending).toBeTrue();
                    expect(agentMessage?.content).not.toContain('[PLAN_PENDING]');
                });

                it('should send approval message when onApprovePlan is called', fakeAsync(() => {
                    const planApprovalText = 'I approve the plan';
                    const translateSpy = jest.spyOn(mockTranslateService, 'instant').mockReturnValue(planApprovalText);
                    const approvalResponse = {
                        message: 'Plan approved, executing...',
                        sessionId: 'course_123',
                        timestamp: new Date().toISOString(),
                        success: true,
                        competenciesModified: false,
                    };
                    mockAgentChatService.sendMessage.mockReturnValue(of(approvalResponse));

                    const message: ChatMessage = {
                        id: '1',
                        content: 'Plan pending',
                        isUser: false,
                        timestamp: new Date(),
                        planPending: true,
                    };
                    component.messages.set([message]);

                    component['onApprovePlan'](message);
                    tick();

                    expect(mockAgentChatService.sendMessage).toHaveBeenCalledWith(planApprovalText, 123);
                    expect(translateSpy).toHaveBeenCalledWith('artemisApp.agent.chat.planApproval');

                    // Find the updated message in the messages array
                    const updatedMessage = component.messages().find((msg) => msg.id === '1');
                    expect(updatedMessage?.planApproved).toBeTrue();
                    expect(updatedMessage?.planPending).toBeFalse();
                }));

                it('should emit competencyChanged when approval modifies competencies', fakeAsync(() => {
                    const approvalResponse = {
                        message: 'Competencies created successfully',
                        sessionId: 'course_123',
                        timestamp: new Date().toISOString(),
                        success: true,
                        competenciesModified: true,
                    };
                    mockAgentChatService.sendMessage.mockReturnValue(of(approvalResponse));
                    const emitSpy = jest.spyOn(component.competencyChanged, 'emit');

                    const message: ChatMessage = {
                        id: '1',
                        content: 'Plan',
                        isUser: false,
                        timestamp: new Date(),
                        planPending: true,
                    };

                    component['onApprovePlan'](message);
                    tick();

                    expect(emitSpy).toHaveBeenCalledOnce();
                }));

                it('should not approve plan twice', () => {
                    const message: ChatMessage = {
                        id: '1',
                        content: 'Plan',
                        isUser: false,
                        timestamp: new Date(),
                        planPending: false,
                        planApproved: true,
                    };

                    component['onApprovePlan'](message);

                    expect(mockAgentChatService.sendMessage).not.toHaveBeenCalled();
                });

                it('should handle approval error gracefully', fakeAsync(() => {
                    mockAgentChatService.sendMessage.mockReturnValue(throwError(() => new Error('Approval failed')));

                    const message: ChatMessage = {
                        id: '1',
                        content: 'Plan',
                        isUser: false,
                        timestamp: new Date(),
                        planPending: true,
                    };

                    const translateSpy = jest.spyOn(mockTranslateService, 'instant');

                    component['onApprovePlan'](message);
                    tick();

                    expect(component.isAgentTyping()).toBeFalse();
                    expect(translateSpy).toHaveBeenCalledWith('artemisApp.agent.chat.error');
                }));
            });

            describe('Create Competency from Preview', () => {
                beforeEach(() => {
                    jest.spyOn(mockTranslateService, 'instant').mockReturnValue('Welcome');
                    component.ngOnInit();

                    message = {
                        id: '1',
                        content: 'Create competency',
                        isUser: false,
                        timestamp: new Date(),
                        competencyPreviews: [
                            {
                                title: 'New Competency',
                                description: 'Test description',
                                taxonomy: CompetencyTaxonomy.UNDERSTAND,
                                icon: 'comments',
                            },
                        ],
                    };
                    component.messages.set([message]);
                });

                it('should create new competency when onCreateCompetencies is called', fakeAsync(() => {
                    mockCompetencyService.create.mockReturnValue(
                        of(
                            new HttpResponse({
                                body: { id: 1, title: 'Created' } as Competency,
                                status: 200,
                            }),
                        ),
                    );
                    const emitSpy = jest.spyOn(component.competencyChanged, 'emit');
                    component.onCreateCompetencies(message);
                    tick();

                    expect(mockCompetencyService.create).toHaveBeenCalledOnce();
                    expect(mockCompetencyService.create).toHaveBeenCalledWith(expect.objectContaining({ title: 'New Competency' }), 123);
                    expect(component.isAgentTyping()).toBeFalse();
                    expect(emitSpy).toHaveBeenCalled();
                }));

                it('should update existing competency when competencyId is present', fakeAsync(() => {
                    message.competencyPreviews = [
                        {
                            title: 'Updated Competency',
                            description: 'Updated description',
                            taxonomy: CompetencyTaxonomy.ANALYZE,
                            icon: 'magnifying-glass',
                            competencyId: 42,
                        },
                    ];
                    component.messages.set([message]);
                    mockCompetencyService.update.mockReturnValue(
                        of(
                            new HttpResponse({
                                body: { id: 42, title: 'Updated Competency' } as Competency,
                            }),
                        ),
                    );
                    const emitSpy = jest.spyOn(component.competencyChanged, 'emit');

                    component.onCreateCompetencies(message);
                    tick();

                    expect(mockCompetencyService.update).toHaveBeenCalledOnce();
                    expect(mockCompetencyService.update).toHaveBeenCalledWith(
                        expect.objectContaining({
                            id: 42,
                            title: 'Updated Competency',
                        }),
                        123,
                    );
                    const updatedMessage = component.messages().find((msg) => msg.id === '1');
                    expect(updatedMessage?.competencyCreated).toBeTrue();
                    expect(emitSpy).toHaveBeenCalledOnce();
                }));

                it('should not create competency twice', () => {
                    message.competencyCreated = true;
                    component.messages.set([message]);

                    component.onCreateCompetencies(message);

                    expect(mockCompetencyService.create).not.toHaveBeenCalled();
                    expect(mockCompetencyService.update).not.toHaveBeenCalled();
                });

                it('should not create competency without preview', () => {
                    const message: ChatMessage = {
                        id: '1',
                        content: 'No preview',
                        isUser: false,
                        timestamp: new Date(),
                    };

                    component.onCreateCompetencies(message);

                    expect(mockCompetencyService.create).not.toHaveBeenCalled();
                });

                it('should handle creation error gracefully', fakeAsync(() => {
                    mockCompetencyService.create.mockReturnValue(throwError(() => new Error('Creation failed')));
                    const createFailedText = 'Failed to create competency';
                    jest.spyOn(mockTranslateService, 'instant').mockReturnValue(createFailedText);

                    component.onCreateCompetencies(message);
                    tick();

                    expect(component.isAgentTyping()).toBeFalse();
                    expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.competencyProcessFailure');
                    const errorMessage = component.messages().find((msg) => !msg.isUser && msg.content === createFailedText);
                    expect(errorMessage).toBeDefined();
                }));

                it('should handle update error gracefully', fakeAsync(() => {
                    mockCompetencyService.update.mockReturnValue(throwError(() => new Error('Update failed')));
                    const updateFailedText = 'Failed to update competency';
                    const translateSpy = jest.spyOn(mockTranslateService, 'instant').mockReturnValue(updateFailedText);

                    const message: ChatMessage = {
                        id: '1',
                        content: 'Preview',
                        isUser: false,
                        timestamp: new Date(),
                        competencyPreviews: [
                            {
                                title: 'Updated Competency',
                                description: 'Description',
                                taxonomy: CompetencyTaxonomy.ANALYZE,
                                icon: 'magnifying-glass',
                                competencyId: 42,
                            },
                        ],
                    };

                    component.onCreateCompetencies(message);
                    tick();

                    expect(component.isAgentTyping()).toBeFalse();
                    expect(translateSpy).toHaveBeenCalledWith('artemisApp.agent.chat.competencyProcessFailure');
                    const errorMessage = component.messages().find((msg) => !msg.isUser && msg.content === updateFailedText);
                    expect(errorMessage).toBeDefined();
                }));
            });

            describe('Invalidate Pending Plan Approvals', () => {
                beforeEach(() => {
                    jest.spyOn(mockTranslateService, 'instant').mockReturnValue('Welcome');
                    component.ngOnInit();
                });

                it('should invalidate pending plans when user sends new message', () => {
                    const pendingPlanMessage: ChatMessage = {
                        id: '1',
                        content: 'Plan 1',
                        isUser: false,
                        timestamp: new Date(),
                        planPending: true,
                    };
                    const approvedPlanMessage: ChatMessage = {
                        id: '2',
                        content: 'Plan 2',
                        isUser: false,
                        timestamp: new Date(),
                        planPending: false,
                        planApproved: true,
                    };

                    component.messages.set([pendingPlanMessage, approvedPlanMessage]);
                    component.currentMessage.set('New message');
                    mockAgentChatService.sendMessage.mockReturnValue(
                        of({
                            message: 'Response',
                            sessionId: 'course_123',
                            timestamp: new Date().toISOString(),
                            success: true,
                            competenciesModified: false,
                        }),
                    );
                    fixture.detectChanges();

                    const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
                    sendButton.click();
                    const messages = component.messages();
                    expect(messages[0].planPending).toBeFalse();
                    expect(messages[1].planApproved).toBeTrue(); // Already approved plans should not be affected
                });
            });

            describe('Batch Competency Creation', () => {
                beforeEach(() => {
                    jest.spyOn(mockTranslateService, 'instant').mockReturnValue('Welcome');
                    component.ngOnInit();
                });

                it('should create multiple competencies from batch preview', fakeAsync(() => {
                    const mockCompetency: Partial<Competency> = {
                        id: 1,
                        title: 'Test',
                    };
                    mockCompetencyService.create.mockReturnValue(of(new HttpResponse({ body: mockCompetency as Competency })));
                    const emitSpy = jest.spyOn(component.competencyChanged, 'emit');

                    const message: ChatMessage = {
                        id: '1',
                        content: 'Batch preview',
                        isUser: false,
                        timestamp: new Date(),
                        competencyPreviews: [
                            {
                                title: 'Comp 1',
                                description: 'Desc 1',
                                taxonomy: CompetencyTaxonomy.REMEMBER,
                                icon: 'brain',
                            },
                            {
                                title: 'Comp 2',
                                description: 'Desc 2',
                                taxonomy: CompetencyTaxonomy.UNDERSTAND,
                                icon: 'comments',
                            },
                            {
                                title: 'Comp 3',
                                description: 'Desc 3',
                                taxonomy: CompetencyTaxonomy.APPLY,
                                icon: 'pen-fancy',
                            },
                        ],
                    };
                    component.messages.set([message]);

                    component.onCreateCompetencies(message);
                    tick();

                    expect(mockCompetencyService.create).toHaveBeenCalledTimes(3);
                    expect(component.isAgentTyping()).toBeFalse();

                    const updatedMessage = component.messages().find((msg) => msg.id === '1');
                    expect(updatedMessage?.competencyCreated).toBeTrue();
                    expect(emitSpy).toHaveBeenCalledOnce();
                }));

                it('should not create batch competencies twice', () => {
                    const message: ChatMessage = {
                        id: '1',
                        content: 'Batch',
                        isUser: false,
                        timestamp: new Date(),
                        competencyPreviews: [
                            {
                                title: 'Test',
                                description: 'Test',
                                taxonomy: CompetencyTaxonomy.APPLY,
                                icon: 'pen-fancy',
                            },
                        ],
                        competencyCreated: true,
                    };

                    component.onCreateCompetencies(message);

                    expect(mockCompetencyService.create).not.toHaveBeenCalled();
                });

                it('should not create batch without preview', () => {
                    const message: ChatMessage = {
                        id: '1',
                        content: 'No batch',
                        isUser: false,
                        timestamp: new Date(),
                    };

                    component.onCreateCompetencies(message);

                    expect(mockCompetencyService.create).not.toHaveBeenCalled();
                });

                it('should not create empty batch', () => {
                    const message: ChatMessage = {
                        id: '1',
                        content: 'Empty batch',
                        isUser: false,
                        timestamp: new Date(),
                        competencyPreviews: [],
                    };

                    component.onCreateCompetencies(message);

                    expect(mockCompetencyService.create).not.toHaveBeenCalled();
                });

                it('should handle mixed create and update operations in batch', fakeAsync(() => {
                    const competency = { id: 1, title: 'Created' };
                    const updatedCompetency: Competency = { id: 10, title: 'Updated' };
                    mockCompetencyService.create.mockReturnValue(
                        of(
                            new HttpResponse({
                                body: competency as Competency,
                                status: 200,
                            }),
                        ),
                    );
                    mockCompetencyService.update.mockReturnValue(
                        of(
                            new HttpResponse({
                                body: updatedCompetency,
                                status: 200,
                            }),
                        ),
                    );

                    const message: ChatMessage = {
                        id: '1',
                        content: 'Mixed batch',
                        isUser: false,
                        timestamp: new Date(),
                        competencyPreviews: [
                            {
                                title: 'New',
                                description: 'New desc',
                                taxonomy: CompetencyTaxonomy.REMEMBER,
                                icon: 'brain',
                            },
                            {
                                title: 'Update',
                                description: 'Update desc',
                                taxonomy: CompetencyTaxonomy.APPLY,
                                icon: 'pen-fancy',
                                competencyId: 10,
                            },
                        ],
                    };
                    component.messages.set([message]);

                    component.onCreateCompetencies(message);
                    tick();

                    expect(mockCompetencyService.create).toHaveBeenCalledOnce();
                    expect(mockCompetencyService.update).toHaveBeenCalledOnce();
                    const messages = component.messages();
                    const successMessage = messages[component.messages().length - 1];
                    expect(successMessage).toBeDefined();
                }));

                it('should handle all updates in batch', fakeAsync(() => {
                    const updatedCompetency: Partial<Competency> = { id: 1, title: 'Updated' };
                    mockCompetencyService.update.mockReturnValue(of(new HttpResponse({ body: updatedCompetency as Competency })));

                    const message: ChatMessage = {
                        id: '1',
                        content: 'Update batch',
                        isUser: false,
                        timestamp: new Date(),
                        competencyPreviews: [
                            {
                                title: 'Update 1',
                                description: 'Desc 1',
                                taxonomy: CompetencyTaxonomy.ANALYZE,
                                icon: 'magnifying-glass',
                                competencyId: 1,
                            },
                            {
                                title: 'Update 2',
                                description: 'Desc 2',
                                taxonomy: CompetencyTaxonomy.EVALUATE,
                                icon: 'plus-minus',
                                competencyId: 2,
                            },
                        ],
                    };
                    component.messages.set([message]);

                    component.onCreateCompetencies(message);
                    tick();

                    expect(mockCompetencyService.update).toHaveBeenCalledTimes(2);
                    const messages = component.messages();
                    const successMessage = messages[component.messages().length - 1];
                    expect(successMessage).toBeDefined();
                }));

                it('should handle single competency in batch', fakeAsync(() => {
                    const singleCompetency: Partial<Competency> = { id: 1, title: 'Single' };
                    mockCompetencyService.create.mockReturnValue(of(new HttpResponse({ body: singleCompetency as Competency })));

                    const message: ChatMessage = {
                        id: '1',
                        content: 'Single batch',
                        isUser: false,
                        timestamp: new Date(),
                        competencyPreviews: [
                            {
                                title: 'Single',
                                description: 'Single desc',
                                taxonomy: CompetencyTaxonomy.CREATE,
                                icon: 'cubes-stacked',
                            },
                        ],
                    };
                    component.messages.set([message]);

                    component.onCreateCompetencies(message);
                    tick();

                    expect(mockCompetencyService.create).toHaveBeenCalledOnce();
                    const messages = component.messages();
                    const successMessage = messages[component.messages().length - 1];
                    expect(successMessage).toBeDefined();
                }));

                it('should handle batch creation error gracefully', fakeAsync(() => {
                    mockCompetencyService.create.mockReturnValue(throwError(() => new Error('Creation failed')));
                    const createFailedText = 'Failed to create competency';
                    const translateSpy = jest.spyOn(mockTranslateService, 'instant').mockReturnValue(createFailedText);

                    const message: ChatMessage = {
                        id: '1',
                        content: 'Batch error',
                        isUser: false,
                        timestamp: new Date(),
                        competencyPreviews: [
                            {
                                title: 'Error',
                                description: 'Error desc',
                                taxonomy: CompetencyTaxonomy.APPLY,
                                icon: 'pen-fancy',
                            },
                        ],
                    };
                    component.messages.set([message]);

                    component.onCreateCompetencies(message);
                    tick();

                    expect(component.isAgentTyping()).toBeFalse();
                    expect(translateSpy).toHaveBeenCalledWith('artemisApp.agent.chat.welcome');
                    expect(translateSpy).toHaveBeenCalledWith('artemisApp.agent.chat.competencyProcessFailure');
                }));
            });
        });

        describe('Edge Cases for JSON Extraction', () => {
            beforeEach(() => {
                jest.spyOn(mockTranslateService, 'instant').mockReturnValue('Welcome');
                component.ngOnInit();
            });

            it('should handle null content in extractCompetencyPreview', () => {
                const mockResponse = {
                    message: null,
                    sessionId: 'course_123',
                    timestamp: new Date().toISOString(),
                    success: true,
                    competenciesModified: false,
                };
                mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse as unknown as AgentChatResponse));
                jest.spyOn(mockTranslateService, 'instant').mockReturnValue('Error message');
                component.currentMessage.set('Test');
                fixture.detectChanges();

                const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
                sendButton.click();

                const agentMessage = component.messages().find((msg) => !msg.isUser);
                expect(agentMessage).toBeDefined();
                expect(agentMessage?.competencyPreviews).toBeUndefined();
            });

            it('should handle undefined content in extractcompetencyPreviews', () => {
                const mockResponse = {
                    message: undefined,
                    sessionId: 'course_123',
                    timestamp: new Date().toISOString(),
                    success: true,
                    competenciesModified: false,
                };
                mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse as unknown as AgentChatResponse));
                jest.spyOn(mockTranslateService, 'instant').mockReturnValue('Error message');
                component.currentMessage.set('Test');
                fixture.detectChanges();

                const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
                sendButton.click();

                const agentMessage = component.messages().find((msg) => !msg.isUser);
                expect(agentMessage).toBeDefined();
                expect(agentMessage?.competencyPreviews).toBeUndefined();
            });

            it('should handle incomplete JSON in message', () => {
                const mockResponse = {
                    message: 'Here is some text {"preview": true, "competency": { incomplete',
                    sessionId: 'course_123',
                    timestamp: new Date().toISOString(),
                    success: true,
                    competenciesModified: false,
                };
                mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse as unknown as AgentChatResponse));
                component.currentMessage.set('Test');
                fixture.detectChanges();

                const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
                sendButton.click();

                const agentMessage = component.messages().find((msg) => !msg.isUser);
                expect(agentMessage).toBeDefined();
                expect(agentMessage?.competencyPreviews).toBeUndefined();
            });
        });
    });
});
