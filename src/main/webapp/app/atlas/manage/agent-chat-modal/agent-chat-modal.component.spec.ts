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

        it('should show welcome message after init', () => {
            // Arrange
            const welcomeMessage = 'Welcome to the agent chat!';
            mockTranslateService.instant.mockReturnValue(welcomeMessage);

            // Act
            component.ngOnInit();

            // Assert
            expect(mockTranslateService.instant).toHaveBeenCalledOnce();
            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.welcome');
            expect(component.messages).toHaveLength(1);
            expect(component.messages[0].content).toBe(welcomeMessage);
            expect(component.messages[0].isUser).toBeFalse();
            expect(component.messages[0].timestamp).toBeInstanceOf(Date);
            expect(component.messages[0].id).toBeDefined();
        });

        it('should generate sessionId based on courseId and timestamp', () => {
            // Arrange
            const mockDateNow = 1642723200000; // Fixed timestamp
            jest.spyOn(Date, 'now').mockReturnValue(mockDateNow);
            component.courseId = 456;

            // Act
            component.ngOnInit();

            // Assert
            expect(component['sessionId']).toBe(`course_456_session_${mockDateNow}`);
        });
    });

    describe('canSendMessage computed signal', () => {
        beforeEach(() => {
            component.isAgentTyping.set(false);
        });

        it('should return false for empty input', () => {
            // Arrange
            component.currentMessage.set('');

            // Act & Assert
            expect(component.canSendMessage()).toBeFalse();
        });

        it('should return false for whitespace only input', () => {
            // Arrange
            component.currentMessage.set('   \n\t  ');

            // Act & Assert
            expect(component.canSendMessage()).toBeFalse();
        });

        it('should return false for too long input', () => {
            // Arrange
            component.currentMessage.set('a'.repeat(component.MAX_MESSAGE_LENGTH + 1));

            // Act & Assert
            expect(component.canSendMessage()).toBeFalse();
        });

        it('should return false when agent is typing', () => {
            // Arrange
            component.currentMessage.set('Valid message');
            component.isAgentTyping.set(true);

            // Act & Assert
            expect(component.canSendMessage()).toBeFalse();
        });

        it('should return true for valid input', () => {
            // Arrange
            component.currentMessage.set('Valid message');
            component.isAgentTyping.set(false);

            // Act & Assert
            expect(component.canSendMessage()).toBeTrue();
        });

        it('should return true for input at max length limit', () => {
            // Arrange
            component.currentMessage.set('a'.repeat(component.MAX_MESSAGE_LENGTH));
            component.isAgentTyping.set(false);

            // Act & Assert
            expect(component.canSendMessage()).toBeTrue();
        });
    });

    describe('onKeyPress', () => {
        let sendMessageSpy: jest.SpyInstance;

        beforeEach(() => {
            sendMessageSpy = jest.spyOn(component as any, 'sendMessage');
        });

        it('should call sendMessage when Enter key is pressed without Shift', () => {
            // Arrange
            const mockEvent = {
                key: 'Enter',
                shiftKey: false,
                preventDefault: jest.fn(),
            } as any;

            // Act
            component.onKeyPress(mockEvent);

            // Assert
            expect(mockEvent.preventDefault).toHaveBeenCalled();
            expect(sendMessageSpy).toHaveBeenCalled();
        });

        it('should not call sendMessage when Enter key is pressed with Shift', () => {
            // Arrange
            const mockEvent = {
                key: 'Enter',
                shiftKey: true,
                preventDefault: jest.fn(),
            } as any;

            // Act
            component.onKeyPress(mockEvent);

            // Assert
            expect(mockEvent.preventDefault).not.toHaveBeenCalled();
            expect(sendMessageSpy).not.toHaveBeenCalled();
        });

        it('should not call sendMessage for other keys', () => {
            // Arrange
            const mockEvent = {
                key: 'Space',
                shiftKey: false,
                preventDefault: jest.fn(),
            } as any;

            // Act
            component.onKeyPress(mockEvent);

            // Assert
            expect(mockEvent.preventDefault).not.toHaveBeenCalled();
            expect(sendMessageSpy).not.toHaveBeenCalled();
        });
    });

    describe('sendMessage', () => {
        beforeEach(() => {
            // Setup component for successful message sending
            component.currentMessage.set('Test message');
            component.isAgentTyping.set(false);
            component.courseId = 123;
            component['sessionId'] = 'test-session-123';
        });

        it('should send message when send button is clicked', () => {
            // Arrange
            component.currentMessage.set('Test message');
            component.isAgentTyping.set(false);
            mockAgentChatService.sendMessage.mockReturnValue(of('Agent response'));

            // Clear any existing messages to start fresh
            component.messages = [];
            fixture.detectChanges();

            // Act - Test through user interaction instead of calling private method
            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            // Assert
            expect(mockAgentChatService.sendMessage).toHaveBeenCalledWith('Test message', 123, component['sessionId']);
            expect(component.messages).toHaveLength(3); // Welcome + User message + agent response
        });

        it('should send message when Enter key is pressed', () => {
            // Arrange
            component.currentMessage.set('Test message');
            component.isAgentTyping.set(false);
            mockAgentChatService.sendMessage.mockReturnValue(of('Agent response'));
            fixture.detectChanges();

            // Act - Test through keyboard interaction
            const textarea = fixture.debugElement.nativeElement.querySelector('textarea');
            const enterEvent = new KeyboardEvent('keypress', {
                key: 'Enter',
                shiftKey: false,
            });

            textarea.dispatchEvent(enterEvent);

            // Assert
            expect(mockAgentChatService.sendMessage).toHaveBeenCalledWith('Test message', 123, component['sessionId']);
        });

        it('should handle service error gracefully', fakeAsync(() => {
            // Arrange
            component.currentMessage.set('Test message');
            const errorMessage = 'Connection failed';
            mockAgentChatService.sendMessage.mockReturnValue(throwError(() => new Error('Service error')));
            mockTranslateService.instant.mockReturnValue(errorMessage);
            fixture.detectChanges();

            // Clear previous calls from beforeEach
            jest.clearAllMocks();

            // Act - Through user interaction
            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();
            tick();

            // Assert
            expect(component.isAgentTyping()).toBeFalse();
            expect(mockTranslateService.instant).toHaveBeenCalledOnce();
            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            expect(component.messages).toHaveLength(3); // Welcome + user message + error message
            expect(component.messages[2].content).toBe(errorMessage);
            expect(component.messages[2].isUser).toBeFalse();
        }));

        it('should not send message if canSendMessage is false', () => {
            // Arrange
            component.currentMessage.set(''); // Makes canSendMessage false
            fixture.detectChanges();

            // Act - Try to click disabled button
            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            // Assert
            expect(mockAgentChatService.sendMessage).not.toHaveBeenCalled();
        });
    });

    describe('Focus behavior', () => {
        it('should focus input after view init', fakeAsync(() => {
            // Act
            component.ngAfterViewInit();
            tick(10);

            // Assert
            expect(mockTextarea.focus).toHaveBeenCalled();
        }));

        it('should scroll to bottom when shouldScrollToBottom is true', () => {
            // Arrange
            component['shouldScrollToBottom'] = true;

            // Act
            component.ngAfterViewChecked();

            // Assert
            expect(mockMessagesContainer.nativeElement.scrollTop).toBe(500); // scrollHeight value
            expect(component['shouldScrollToBottom']).toBeFalse();
        });

        it('should not scroll when shouldScrollToBottom is false', () => {
            // Arrange
            component['shouldScrollToBottom'] = false;
            const originalScrollTop = mockMessagesContainer.nativeElement.scrollTop;

            // Act
            component.ngAfterViewChecked();

            // Assert
            expect(mockMessagesContainer.nativeElement.scrollTop).toBe(originalScrollTop);
        });
    });

    describe('Template integration', () => {
        beforeEach(() => {
            mockTranslateService.instant.mockReturnValue('Welcome message');
            component.ngOnInit();
            fixture.detectChanges();
        });

        it('should display messages in the template', () => {
            // Arrange
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

            // Act
            fixture.detectChanges();

            // Assert
            const messageElements = fixture.debugElement.nativeElement.querySelectorAll('.message-wrapper');
            expect(messageElements).toHaveLength(2);

            const userMessageElement = messageElements[0];
            const agentMessageElement = messageElements[1];

            expect(userMessageElement.classList.contains('user-message')).toBeTrue();
            expect(agentMessageElement.classList.contains('agent-message')).toBeTrue();
        });

        it('should show typing indicator when isAgentTyping is true', () => {
            // Arrange
            component.isAgentTyping.set(true);

            // Act
            fixture.detectChanges();

            // Assert
            const typingIndicator = fixture.debugElement.nativeElement.querySelector('.typing-indicator');
            expect(typingIndicator).toBeTruthy();
        });

        it('should hide typing indicator when isAgentTyping is false', () => {
            // Arrange
            component.isAgentTyping.set(false);

            // Act
            fixture.detectChanges();

            // Assert
            const typingIndicator = fixture.debugElement.nativeElement.querySelector('.typing-indicator');
            expect(typingIndicator).toBeFalsy();
        });

        it('should prevent message sending when agent is typing', () => {
            // Arrange
            component.currentMessage.set('Valid message');
            component.isAgentTyping.set(true);

            // Act & Assert
            expect(component.canSendMessage()).toBeFalse();
            expect(component.isAgentTyping()).toBeTrue();
        });

        it('should disable send button when canSendMessage is false', () => {
            // Arrange
            component.currentMessage.set('');

            // Act
            fixture.detectChanges();

            // Assert
            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            expect(sendButton.disabled).toBeTrue();
        });

        it('should enable send button when canSendMessage is true', () => {
            // Arrange
            component.currentMessage.set('Valid message');
            component.isAgentTyping.set(false);

            // Act
            fixture.detectChanges();

            // Assert
            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            expect(sendButton.disabled).toBeFalse();
        });

        it('should show character count in template', () => {
            // Arrange
            component.currentMessage.set('Test message');

            // Act
            fixture.detectChanges();

            // Assert
            const charCountElement = fixture.debugElement.nativeElement.querySelector('.text-end');
            expect(charCountElement.textContent.trim()).toContain('12 / 8000');
        });

        it('should show error styling when message is too long', () => {
            // Arrange
            component.currentMessage.set('a'.repeat(component.MAX_MESSAGE_LENGTH + 1));

            // Act
            fixture.detectChanges();

            // Assert
            const charCountElement = fixture.debugElement.nativeElement.querySelector('.text-danger');
            expect(charCountElement).toBeTruthy();

            const errorMessage = fixture.debugElement.nativeElement.querySelector('small.text-danger.mt-1');
            expect(errorMessage).toBeTruthy();
        });
    });

    describe('Modal interaction', () => {
        it('should close modal when closeModal is called', () => {
            // Act
            (component as any).closeModal();

            // Assert
            expect(mockActiveModal.close).toHaveBeenCalled();
        });

        it('should call closeModal when close button is clicked', () => {
            // Arrange
            const closeModalSpy = jest.spyOn(component as any, 'closeModal');
            fixture.detectChanges();

            // Act
            const closeButton = fixture.debugElement.nativeElement.querySelector('.btn-close');
            closeButton.click();

            // Assert
            expect(closeModalSpy).toHaveBeenCalled();
        });
    });

    describe('Textarea auto-resize behavior', () => {
        it('should auto-resize textarea on input when content exceeds max height', () => {
            // Arrange
            Object.defineProperty(mockTextarea, 'scrollHeight', {
                value: 150, // Greater than max height of 120px
                writable: true,
                configurable: true,
            });

            // Act
            component.onTextareaInput();

            // Assert
            // Height should be set to max height (120px) when scrollHeight exceeds it
            expect(mockTextarea.style.height).toBe('120px');
        });

        it('should auto-resize textarea on input when content is within max height', () => {
            // Arrange
            Object.defineProperty(mockTextarea, 'scrollHeight', {
                value: 80, // Less than max height of 120px
                writable: true,
                configurable: true,
            });

            // Act
            component.onTextareaInput();

            // Assert
            // Height should be set to scrollHeight when it's less than max height
            expect(mockTextarea.style.height).toBe('80px');
        });

        it('should handle case when textarea element is not available', () => {
            // Arrange
            jest.spyOn(component as any, 'messageInput').mockReturnValue(null);

            // Act & Assert - Should not throw error
            expect(() => component.onTextareaInput()).not.toThrow();
        });
    });
});
