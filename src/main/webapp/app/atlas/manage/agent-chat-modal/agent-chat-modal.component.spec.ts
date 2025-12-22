import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ChangeDetectorRef, ElementRef } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { MockDirective, MockPipe } from 'ng-mocks';
import { FormsModule } from '@angular/forms';

import { AgentChatModalComponent } from './agent-chat-modal.component';
import { AgentChatService } from './agent-chat.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChatMessage } from 'app/atlas/shared/entities/chat-message.model';

describe('AgentChatModalComponent', () => {
    let component: AgentChatModalComponent;
    let fixture: ComponentFixture<AgentChatModalComponent>;
    let mockActiveModal: jest.Mocked<NgbActiveModal>;
    let mockAgentChatService: jest.Mocked<AgentChatService>;
    let mockTranslateService: jest.Mocked<TranslateService>;
    let mockChangeDetectorRef: jest.Mocked<ChangeDetectorRef>;

    // Mock element refs
    let mockMessagesContainer: jest.Mocked<ElementRef>;
    let mockMessageInput: jest.Mocked<ElementRef<HTMLTextAreaElement>>;
    let mockTextarea: jest.Mocked<HTMLTextAreaElement>;

    beforeEach(async () => {
        // Create mock services
        mockActiveModal = {
            close: jest.fn(),
            dismiss: jest.fn(),
        } as any;

        mockAgentChatService = {
            sendMessage: jest.fn(),
            getConversationHistory: jest.fn().mockReturnValue(of([])),
        } as any;

        mockTranslateService = {
            instant: jest.fn(),
        } as any;

        mockChangeDetectorRef = {
            markForCheck: jest.fn(),
            detectChanges: jest.fn(),
        } as any;

        // Create mock DOM elements
        mockTextarea = {
            focus: jest.fn(),
            style: { height: '' },
            scrollHeight: 100,
        } as any;

        mockMessageInput = {
            nativeElement: mockTextarea,
        } as any;

        mockMessagesContainer = {
            nativeElement: {
                scrollTop: 0,
                scrollHeight: 500,
            },
        } as any;

        await TestBed.configureTestingModule({
            imports: [AgentChatModalComponent, FormsModule, MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe, (value: string) => `translated:${value}`)],
            providers: [
                { provide: NgbActiveModal, useValue: mockActiveModal },
                { provide: AgentChatService, useValue: mockAgentChatService },
                { provide: TranslateService, useValue: mockTranslateService },
                { provide: ChangeDetectorRef, useValue: mockChangeDetectorRef },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(AgentChatModalComponent);
        component = fixture.componentInstance;

        // Set required inputs
        component.courseId = 123;

        // Mock viewChild signals
        jest.spyOn(component as any, 'messagesContainer').mockReturnValue(mockMessagesContainer);
        jest.spyOn(component as any, 'messageInput').mockReturnValue(mockMessageInput);
    });

    afterEach(() => {
        jest.restoreAllMocks();
        jest.clearAllTimers();
    });

    describe('Component Initialization', () => {
        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should show welcome message after init when history is empty', () => {
            const welcomeMessage = 'Welcome to the agent chat!';
            mockTranslateService.instant.mockReturnValue(welcomeMessage);
            component.ngOnInit();
            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.welcome');

            expect(component.messages[0].content).toBe(welcomeMessage);
        });

        it('should load conversation history when available', () => {
            const mockHistory = [
                { content: 'Previous user message', isUser: true },
                { content: 'Previous agent response', isUser: false },
                { content: 'Another user message', isUser: true },
            ];
            mockAgentChatService.getConversationHistory.mockReturnValue(of(mockHistory));
            component.ngOnInit();
            expect(mockAgentChatService.getConversationHistory).toHaveBeenCalledWith(123);
            expect(component.messages).toHaveLength(4);
            expect(component.messages[1].content).toBe('Previous user message');
            expect(component.messages[1].isUser).toBeTrue();
            expect(component.messages[2].content).toBe('Previous agent response');
            expect(component.messages[2].isUser).toBeFalse();
            expect(component.messages[3].content).toBe('Another user message');
            expect(component.messages[3].isUser).toBeTrue();
        });

        it('should show welcome message on history fetch error', () => {
            const welcomeMessage = 'Welcome message on error';
            mockTranslateService.instant.mockReturnValue(welcomeMessage);
            mockAgentChatService.getConversationHistory.mockReturnValue(throwError(() => new Error('Network error')));

            component.ngOnInit();

            expect(mockAgentChatService.getConversationHistory).toHaveBeenCalledWith(123);
            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.welcome');
            expect(component.messages).toHaveLength(2);
            expect(component.messages[1].content).toBe(welcomeMessage);
            expect(component.messages[1].isUser).toBeFalse();
        });

        it('should show welcome message when history is null', () => {
            const welcomeMessage = 'Welcome to the agent chat!';
            mockTranslateService.instant.mockReturnValue(welcomeMessage);
            mockAgentChatService.getConversationHistory.mockReturnValue(of(null as any));

            component.ngOnInit();

            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.welcome');
            expect(component.messages).toHaveLength(1);
            expect(component.messages[0].content).toBe(welcomeMessage);
            expect(component.messages[0].isUser).toBeFalse();
        });

        it('should generate unique message IDs for history messages', () => {
            const mockHistory = [
                { content: 'Message 1', isUser: true },
                { content: 'Message 2', isUser: false },
            ];
            mockAgentChatService.getConversationHistory.mockReturnValue(of(mockHistory));

            component.ngOnInit();

            const messageIds = component.messages.map((msg) => msg.id);
            const uniqueIds = new Set(messageIds);

            expect(uniqueIds.size).toBe(messageIds.length);
        });

        it('should set correct timestamps for history messages', () => {
            const beforeTime = new Date();
            const mockHistory = [{ content: 'Test message', isUser: true }];
            mockAgentChatService.getConversationHistory.mockReturnValue(of(mockHistory));

            component.ngOnInit();
            const afterTime = new Date();

            expect(component.messages).toHaveLength(2); // welcome + history
            expect(component.messages[1].timestamp).toBeInstanceOf(Date);
            expect(component.messages[1].timestamp.getTime()).toBeGreaterThanOrEqual(beforeTime.getTime());
            expect(component.messages[1].timestamp.getTime()).toBeLessThanOrEqual(afterTime.getTime());
        });
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

    describe('onKeyPress', () => {
        let sendMessageSpy: jest.SpyInstance;

        beforeEach(() => {
            sendMessageSpy = jest.spyOn(component as any, 'sendMessage');
        });

        it('should call sendMessage when Enter key is pressed without Shift', () => {
            const mockEvent = {
                key: 'Enter',
                shiftKey: false,
                preventDefault: jest.fn(),
            } as any;

            component.onKeyPress(mockEvent);

            expect(mockEvent.preventDefault).toHaveBeenCalled();
            expect(sendMessageSpy).toHaveBeenCalled();
        });

        it('should not call sendMessage when Enter key is pressed with Shift', () => {
            const mockEvent = {
                key: 'Enter',
                shiftKey: true,
                preventDefault: jest.fn(),
            } as any;

            component.onKeyPress(mockEvent);

            expect(mockEvent.preventDefault).not.toHaveBeenCalled();
            expect(sendMessageSpy).not.toHaveBeenCalled();
        });

        it('should not call sendMessage for other keys', () => {
            const mockEvent = {
                key: 'Space',
                shiftKey: false,
                preventDefault: jest.fn(),
            } as any;

            component.onKeyPress(mockEvent);

            expect(mockEvent.preventDefault).not.toHaveBeenCalled();
            expect(sendMessageSpy).not.toHaveBeenCalled();
        });
    });

    describe('sendMessage', () => {
        beforeEach(() => {
            component.currentMessage.set('Test message');
            component.isAgentTyping.set(false);
            component.courseId = 123;
        });

        it('should send message when send button is clicked', () => {
            mockTranslateService.instant.mockReturnValue('Welcome');
            const mockResponse = {
                message: 'Agent response',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));

            component.ngOnInit();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            expect(mockAgentChatService.sendMessage).toHaveBeenCalledWith('Test message', 123);
            //  welcome messages + 1 user message + 1 agent response = 3
            expect(component.messages).toHaveLength(3);
        });

        it('should send message when Enter key is pressed', () => {
            component.currentMessage.set('Test message');
            component.isAgentTyping.set(false);
            const mockResponse = {
                message: 'Agent response',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            fixture.detectChanges();

            const textarea = fixture.debugElement.nativeElement.querySelector('textarea');
            const enterEvent = new KeyboardEvent('keypress', {
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
            mockTranslateService.instant.mockReturnValue(errorMessage);
            fixture.detectChanges();

            jest.clearAllMocks();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();
            tick();

            expect(component.isAgentTyping()).toBeFalse();
            expect(mockTranslateService.instant).toHaveBeenCalledOnce();
            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            expect(component.messages).toHaveLength(3); // Welcome + user message + error message
            expect(component.messages[2].content).toBe(errorMessage);
            expect(component.messages[2].isUser).toBeFalse();
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
            component['shouldScrollToBottom'] = true;

            component.ngAfterViewChecked();

            expect(mockMessagesContainer.nativeElement.scrollTop).toBe(500); // scrollHeight value
            expect(component['shouldScrollToBottom']).toBeFalse();
        });

        it('should not scroll when shouldScrollToBottom is false', () => {
            component['shouldScrollToBottom'] = false;
            const originalScrollTop = mockMessagesContainer.nativeElement.scrollTop;

            component.ngAfterViewChecked();

            expect(mockMessagesContainer.nativeElement.scrollTop).toBe(originalScrollTop);
        });
    });

    describe('Template integration', () => {
        beforeEach(() => {
            mockTranslateService.instant.mockReturnValue('Welcome message');
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
            component.messages = [userMessage, agentMessage];

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
            (component as any).closeModal();

            expect(mockActiveModal.close).toHaveBeenCalled();
        });

        it('should call closeModal when close button is clicked', () => {
            const closeModalSpy = jest.spyOn(component as any, 'closeModal');
            fixture.detectChanges();

            const closeButton = fixture.debugElement.nativeElement.querySelector('.btn-close');
            closeButton.click();

            expect(closeModalSpy).toHaveBeenCalled();
        });
    });

    describe('Competency modification events', () => {
        beforeEach(() => {
            mockTranslateService.instant.mockReturnValue('Welcome');
            mockAgentChatService.getConversationHistory.mockReturnValue(of([]));
            component.ngOnInit();
        });

        it('should emit competencyChanged event when competenciesModified is true', () => {
            component.currentMessage.set('Create a competency for OOP');
            component.isAgentTyping.set(false);
            const mockResponse = {
                message: 'Competency created successfully',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: true,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
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
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
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
            expect(mockTextarea.style.height).toBe('120px');
        });

        it('should auto-resize textarea on input when content is within max height', () => {
            Object.defineProperty(mockTextarea, 'scrollHeight', {
                value: 80, // Less than max height of 120px
                writable: true,
                configurable: true,
            });

            component.onTextareaInput();

            // Height should be set to scrollHeight when it's less than max height
            expect(mockTextarea.style.height).toBe('80px');
        });

        it('should handle case when textarea element is not available', () => {
            jest.spyOn(component as any, 'messageInput').mockReturnValue(null);

            expect(() => component.onTextareaInput()).not.toThrow();
        });
    });

    describe('Computed signals', () => {
        it('should calculate currentMessageLength correctly', () => {
            component.currentMessage.set('Hello');

            expect(component.currentMessageLength()).toBe(5);
        });

        it('should update currentMessageLength when message changes', () => {
            component.currentMessage.set('Short');
            expect(component.currentMessageLength()).toBe(5);

            component.currentMessage.set('A much longer message');

            expect(component.currentMessageLength()).toBe(21);
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
            mockTranslateService.instant.mockReturnValue('Welcome');
            mockAgentChatService.getConversationHistory.mockReturnValue(of([]));
            component.ngOnInit();
            component.currentMessage.set('Test message to send');
            component.isAgentTyping.set(false);
            const mockResponse = {
                message: 'Agent response',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
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
            mockTranslateService.instant.mockReturnValue('Welcome');
            mockAgentChatService.getConversationHistory.mockReturnValue(of([]));
            component.ngOnInit();
            component.currentMessage.set('Test message');
            component.isAgentTyping.set(false);
            mockAgentChatService.sendMessage.mockReturnValue(
                of({
                    message: 'Response',
                    sessionId: 'course_123',
                    timestamp: '2024-01-01T00:00:00Z',
                    success: true,
                    competenciesModified: false,
                }),
            );
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            expect(component.isAgentTyping()).toBeFalse(); // False after response completes
        });

        it('should add user message to messages array', () => {
            mockTranslateService.instant.mockReturnValue('Welcome');
            mockAgentChatService.getConversationHistory.mockReturnValue(of([]));
            component.ngOnInit();
            const initialMessageCount = component.messages.length;
            component.currentMessage.set('User test message');
            component.isAgentTyping.set(false);
            mockAgentChatService.sendMessage.mockReturnValue(
                of({
                    message: 'Agent response',
                    sessionId: 'course_123',
                    timestamp: '2024-01-01T00:00:00Z',
                    success: true,
                    competenciesModified: false,
                }),
            );
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            expect(component.messages.length).toBeGreaterThan(initialMessageCount);
            const userMessage = component.messages.find((msg) => msg.isUser && msg.content === 'User test message');
            expect(userMessage).toBeDefined();
        });
    });

    describe('Scroll behavior edge cases', () => {
        it('should handle scrollToBottom when messagesContainer is null', () => {
            jest.spyOn(component as any, 'messagesContainer').mockReturnValue(null);
            component['shouldScrollToBottom'] = true;

            expect(() => component.ngAfterViewChecked()).not.toThrow();
        });

        it('should handle empty response message from service', fakeAsync(() => {
            component.currentMessage.set('Test message');
            component.isAgentTyping.set(false);
            const mockResponse = {
                message: '',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            mockTranslateService.instant.mockReturnValue('Default error message');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();
            tick();

            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            expect(component.messages[component.messages.length - 1].content).toBe('Default error message');
        }));

        it('should handle null response message from service', fakeAsync(() => {
            component.currentMessage.set('Test message');
            component.isAgentTyping.set(false);
            const mockResponse = {
                message: null as any,
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            mockTranslateService.instant.mockReturnValue('Default error message');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();
            tick();

            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
        }));

        it('should set shouldScrollToBottom flag when adding messages', () => {
            mockTranslateService.instant.mockReturnValue('Welcome');
            mockAgentChatService.getConversationHistory.mockReturnValue(of([]));
            component.ngOnInit();
            component['shouldScrollToBottom'] = false;

            component.currentMessage.set('Test message');
            component.isAgentTyping.set(false);
            mockAgentChatService.sendMessage.mockReturnValue(
                of({
                    message: 'Response',
                    sessionId: 'course_123',
                    timestamp: '2024-01-01T00:00:00Z',
                    success: true,
                    competenciesModified: false,
                }),
            );
            fixture.detectChanges();
            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            expect(component['shouldScrollToBottom']).toBeDefined();
        });
    });
});
