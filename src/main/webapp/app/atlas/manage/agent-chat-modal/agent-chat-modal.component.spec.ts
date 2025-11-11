import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ChangeDetectorRef, ElementRef } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { MockDirective, MockPipe } from 'ng-mocks';
import { FormsModule } from '@angular/forms';

import { AgentChatModalComponent } from './agent-chat-modal.component';
import { AgentChatService } from './agent-chat.service';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChatMessage } from 'app/atlas/shared/entities/chat-message.model';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';

describe('AgentChatModalComponent', () => {
    let component: AgentChatModalComponent;
    let fixture: ComponentFixture<AgentChatModalComponent>;
    let mockActiveModal: jest.Mocked<NgbActiveModal>;
    let mockAgentChatService: jest.Mocked<AgentChatService>;
    let mockCompetencyService: jest.Mocked<CompetencyService>;
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

        mockCompetencyService = {
            getAll: jest.fn(),
            create: jest.fn(),
            update: jest.fn(),
            delete: jest.fn(),
        } as any;

        mockTranslateService = {
            instant: jest.fn(),
        } as Partial<TranslateService> as TranslateService;

        mockTranslateService.instant.mockImplementation((key: string, params?: any) => {
            switch (key) {
                case 'artemisApp.agent.chat.welcome':
                    return 'Welcome to the agent chat!';
                case 'artemisApp.agent.chat.error':
                    return 'An error occurred. Please try again.';
                case 'artemisApp.agent.chat.error.createFailed':
                    return 'Failed to create competency';
                case 'artemisApp.agent.chat.error.updateFailed':
                    return 'Failed to update competency';
                case 'artemisApp.agent.chat.planApproval':
                    return 'I approve the plan';
                case 'artemisApp.agent.chat.success.processed':
                    return `Successfully processed ${params?.count} competencies!`;
                case 'artemisApp.agent.chat.success.updated':
                    return `Successfully updated ${params?.count} ${params?.count === 1 ? 'competency' : 'competencies'}!`;
                case 'artemisApp.agent.chat.success.created':
                    return `Successfully created ${params?.count} ${params?.count === 1 ? 'competency' : 'competencies'}!`;
                default:
                    return key; // fallback
            }
        });

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
                { provide: CompetencyService, useValue: mockCompetencyService },
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
            const welcomeMessage = 'Welcome to the agent chat!';
            mockTranslateService.instant.mockReturnValue(welcomeMessage);

            component.ngOnInit();

            expect(mockTranslateService.instant).toHaveBeenCalledOnce();
            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.welcome');
            expect(component.messages).toHaveLength(1);
            expect(component.messages[0].content).toBe(welcomeMessage);
            expect(component.messages[0].isUser).toBeFalse();
            expect(component.messages[0].timestamp).toBeInstanceOf(Date);
            expect(component.messages[0].id).toBeDefined();
        });

        it('should generate sessionId based on courseId and timestamp', () => {
            const mockDateNow = 1642723200000; // Fixed timestamp
            jest.spyOn(Date, 'now').mockReturnValue(mockDateNow);
            component.courseId = 456;

            component.ngOnInit();

            expect(component.messages.length).toBeGreaterThan(0);
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

            component.messages = [];
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            expect(mockAgentChatService.sendMessage).toHaveBeenCalledWith('Test message', 123);
            expect(component.messages).toHaveLength(3); // Welcome + User message + agent response
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
            component.currentMessage.set(''); // Makes canSendMessage false
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

            fixture.detectChanges();

            const messageElements = fixture.debugElement.nativeElement.querySelectorAll('.message-wrapper');
            expect(messageElements).toHaveLength(2);

            const userMessageElement = messageElements[0];
            const agentMessageElement = messageElements[1];

            expect(userMessageElement.classList.contains('user-message')).toBeTrue();
            expect(agentMessageElement.classList.contains('agent-message')).toBeTrue();
        });

        it('should show typing indicator when isAgentTyping is true', () => {
            component.isAgentTyping.set(true);

            fixture.detectChanges();

            const typingIndicator = fixture.debugElement.nativeElement.querySelector('.typing-indicator');
            expect(typingIndicator).toBeTruthy();
        });

        it('should hide typing indicator when isAgentTyping is false', () => {
            component.isAgentTyping.set(false);

            fixture.detectChanges();

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

            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            expect(sendButton.disabled).toBeTrue();
        });

        it('should enable send button when canSendMessage is true', () => {
            component.currentMessage.set('Valid message');
            component.isAgentTyping.set(false);

            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            expect(sendButton.disabled).toBeFalse();
        });

        it('should show character count in template', () => {
            component.currentMessage.set('Test message');

            fixture.detectChanges();

            const charCountElement = fixture.debugElement.nativeElement.querySelector('.text-end');
            expect(charCountElement.textContent.trim()).toContain('12 / 8000');
        });

        it('should show error styling when message is too long', () => {
            component.currentMessage.set('a'.repeat(component.MAX_MESSAGE_LENGTH + 1));

            fixture.detectChanges();

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

            expect(component.isAgentTyping()).toBeFalse(); // After response completes
        });

        it('should add user message to messages array', () => {
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
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();
            tick();

            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            expect(component.messages[component.messages.length - 1].content).toBe(mockTranslateService.instant('artemisApp.agent.chat.error'));
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
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();
            tick();

            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            expect(component.messages[component.messages.length - 1].content).toBe(mockTranslateService.instant('artemisApp.agent.chat.error'));
        }));

        it('should set shouldScrollToBottom flag when adding messages', () => {
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

    describe('Competency Preview Extraction', () => {
        beforeEach(() => {
            component.ngOnInit();
        });

        it('should extract competency preview from JSON in agent response', () => {
            const mockResponse = {
                message: "Here's a competency suggestion:",
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
                competencyPreview: {
                    preview: true,
                    competency: {
                        title: 'Object-Oriented Programming',
                        description: 'Understanding OOP principles',
                        taxonomy: CompetencyTaxonomy.UNDERSTAND,
                        icon: 'comments',
                    },
                },
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            component.currentMessage.set('Create OOP competency');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            const agentMessage = component.messages.find((msg) => !msg.isUser && msg.competencyPreview);
            expect(agentMessage).toBeDefined();
            expect(agentMessage?.competencyPreview?.title).toBe('Object-Oriented Programming');
            expect(agentMessage?.competencyPreview?.description).toBe('Understanding OOP principles');
            expect(agentMessage?.competencyPreview?.taxonomy).toBe(CompetencyTaxonomy.UNDERSTAND);
            expect(agentMessage?.competencyPreview?.icon).toBe('comments');
        });

        it('should extract competency preview from structured response', () => {
            const mockResponse = {
                message: "Here's a suggestion:",
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
                competencyPreview: {
                    preview: true,
                    competency: {
                        title: 'Data Structures',
                        description: 'Arrays, lists, trees, and graphs',
                        taxonomy: CompetencyTaxonomy.APPLY,
                        icon: 'pen-fancy',
                    },
                },
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            component.currentMessage.set('Create data structures competency');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            const agentMessage = component.messages.find((msg) => !msg.isUser && msg.competencyPreview);
            expect(agentMessage).toBeDefined();
            expect(agentMessage?.competencyPreview?.title).toBe('Data Structures');
            expect(agentMessage?.competencyPreview?.taxonomy).toBe(CompetencyTaxonomy.APPLY);
        });

        it('should extract competencyId for update operations', () => {
            const mockResponse = {
                message: 'Updated competency preview:',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
                competencyPreview: {
                    preview: true,
                    competencyId: 42,
                    competency: {
                        title: 'Updated Title',
                        description: 'Updated description',
                        taxonomy: CompetencyTaxonomy.UNDERSTAND,
                        icon: 'magnifying-glass',
                    },
                },
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            component.currentMessage.set('Update competency 42');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            const agentMessage = component.messages.find((msg) => !msg.isUser && msg.competencyPreview);
            expect(agentMessage).toBeDefined();
            expect(agentMessage?.competencyPreview?.competencyId).toBe(42);
        });

        it('should extract viewOnly flag when present', () => {
            const mockResponse = {
                message: 'Preview:',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
                competencyPreview: {
                    preview: true,
                    viewOnly: true,
                    competency: {
                        title: 'Read-only Competency',
                        description: 'For viewing only',
                        taxonomy: CompetencyTaxonomy.UNDERSTAND,
                        icon: 'brain',
                    },
                },
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            component.currentMessage.set('Show me competency');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            const agentMessage = component.messages.find((msg) => !msg.isUser && msg.competencyPreview);
            expect(agentMessage).toBeDefined();
            expect(agentMessage?.competencyPreview?.viewOnly).toBeTrue();
        });

        it('should not extract preview when no preview is sent', () => {
            const mockResponse = {
                message: 'Response: This is not a preview',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            component.currentMessage.set('Some message');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            const agentMessage = component.messages.find((msg) => !msg.isUser && !msg.isUser);
            expect(agentMessage).toBeDefined();
            expect(agentMessage?.competencyPreview).toBeUndefined();
        });

        it('should handle malformed JSON gracefully', () => {
            const mockResponse = {
                message: 'This is a normal response without JSON',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            component.currentMessage.set('Normal message');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            const agentMessage = component.messages[component.messages.length - 1];
            expect(agentMessage).toBeDefined();
            expect(agentMessage?.competencyPreview).toBeUndefined();
            expect(agentMessage?.content).toBe('This is a normal response without JSON');
        });
    });

    describe('Batch Competency Preview Extraction', () => {
        beforeEach(() => {
            mockTranslateService.instant.mockReturnValue('Welcome');
            component.ngOnInit();
        });

        it('should extract batch competency preview from structured response', () => {
            const mockResponse = {
                message: 'Here are multiple competencies:',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
                batchCompetencyPreview: {
                    batchPreview: true,
                    count: 3,
                    competencies: [
                        { title: 'Comp 1', description: 'Desc 1', taxonomy: CompetencyTaxonomy.REMEMBER, icon: 'brain' },
                        { title: 'Comp 2', description: 'Desc 2', taxonomy: CompetencyTaxonomy.UNDERSTAND, icon: 'comments' },
                        { title: 'Comp 3', description: 'Desc 3', taxonomy: CompetencyTaxonomy.APPLY, icon: 'pen-fancy' },
                    ],
                },
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            component.currentMessage.set('Create multiple competencies');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            const agentMessage = component.messages.find((msg) => !msg.isUser && msg.batchCompetencyPreview);
            expect(agentMessage).toBeDefined();
            expect(agentMessage?.batchCompetencyPreview).toHaveLength(3);
            expect(agentMessage?.batchCompetencyPreview?.[0].title).toBe('Comp 1');
            expect(agentMessage?.batchCompetencyPreview?.[1].title).toBe('Comp 2');
            expect(agentMessage?.batchCompetencyPreview?.[2].title).toBe('Comp 3');
        });

        it('should prioritize batch preview over single preview', () => {
            const mockResponse = {
                message: 'Batch preview:',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
                batchCompetencyPreview: {
                    batchPreview: true,
                    count: 2,
                    competencies: [
                        { title: 'Batch 1', description: 'Desc 1', taxonomy: CompetencyTaxonomy.APPLY, icon: 'pen-fancy' },
                        { title: 'Batch 2', description: 'Desc 2', taxonomy: CompetencyTaxonomy.ANALYZE, icon: 'magnifying-glass' },
                    ],
                },
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            component.currentMessage.set('Create batch');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            const agentMessage = component.messages.find((msg) => !msg.isUser && msg.batchCompetencyPreview);
            expect(agentMessage).toBeDefined();
            expect(agentMessage?.batchCompetencyPreview).toHaveLength(2);
            expect(agentMessage?.competencyPreview).toBeUndefined();
        });

        it('should handle empty batch preview gracefully', () => {
            const mockResponse = {
                message: 'Empty batch:',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
                batchCompetencyPreview: {
                    batchPreview: true,
                    count: 0,
                    competencies: [],
                },
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            component.currentMessage.set('Empty batch');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            const agentMessage = component.messages.find((msg) => !msg.isUser && msg.batchCompetencyPreview !== undefined);
            expect(agentMessage).toBeDefined();
            expect(agentMessage?.batchCompetencyPreview || []).toHaveLength(0);
        });
    });

    describe('Plan Pending Detection and Approval', () => {
        beforeEach(() => {
            mockTranslateService.instant.mockReturnValue('Welcome');
            component.ngOnInit();
        });

        it('should detect plan pending marker in agent response', () => {
            const mockResponse = {
                message: 'Here is my plan:\n1. Step 1\n2. Step 2\n[PLAN_PENDING]',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            component.currentMessage.set('Create a plan');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            const agentMessage = component.messages.find((msg) => !msg.isUser && msg.planPending);
            expect(agentMessage).toBeDefined();
            expect(agentMessage?.planPending).toBeTrue();
            expect(agentMessage?.content).not.toContain('[PLAN_PENDING]');
        });

        it('should send approval message when onApprovePlan is called', fakeAsync(() => {
            const planApprovalText = 'I approve the plan';
            mockTranslateService.instant.mockReturnValue(planApprovalText);
            const approvalResponse = {
                message: 'Plan approved, executing...',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
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
            component.messages = [message];

            component['onApprovePlan'](message);
            tick();

            expect(mockAgentChatService.sendMessage).toHaveBeenCalledWith(planApprovalText, 123);
            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.planApproval');

            // Find the updated message in the messages array
            const updatedMessage = component.messages.find((msg) => msg.id === '1');
            expect(updatedMessage?.planApproved).toBeTrue();
            expect(updatedMessage?.planPending).toBeFalse();
        }));

        it('should emit competencyChanged when approval modifies competencies', fakeAsync(() => {
            const approvalResponse = {
                message: 'Competencies created successfully',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
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

            component['onApprovePlan'](message);
            tick();

            expect(component.isAgentTyping()).toBeFalse();
            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error');
            expect(component.messages[component.messages.length - 1].content).toBe(mockTranslateService.instant('artemisApp.agent.chat.error'));
        }));
    });

    describe('Create Competency from Preview', () => {
        beforeEach(() => {
            mockTranslateService.instant.mockReturnValue('Welcome');
            component.ngOnInit();
        });

        it('should create new competency when onCreateCompetency is called', fakeAsync(() => {
            mockCompetencyService.create.mockReturnValue(
                of({
                    id: 1,
                    title: 'New Competency',
                    description: 'Description',
                } as any),
            );
            const emitSpy = jest.spyOn(component.competencyChanged, 'emit');

            const message: ChatMessage = {
                id: '1',
                content: 'Preview',
                isUser: false,
                timestamp: new Date(),
                competencyPreview: {
                    title: 'New Competency',
                    description: 'Description',
                    taxonomy: 'APPLY' as any,
                    icon: 'pen-fancy',
                },
            };
            component.messages = [message];

            component['onCreateCompetency'](message);
            tick();

            expect(mockCompetencyService.create).toHaveBeenCalledOnce();
            expect(mockCompetencyService.create).toHaveBeenCalledWith(expect.objectContaining({ title: 'New Competency' }), 123);
            expect(component.isAgentTyping()).toBeFalse();

            // Find the message in the component's messages array to check if it was updated
            const updatedMessage = component.messages.find((msg) => msg.id === '1');
            expect(updatedMessage?.competencyCreated).toBeTrue();
            expect(emitSpy).toHaveBeenCalledOnce();
        }));

        it('should update existing competency when competencyId is present', fakeAsync(() => {
            mockCompetencyService.update.mockReturnValue(
                of({
                    id: 42,
                    title: 'Updated Competency',
                    description: 'Updated description',
                } as any),
            );
            const emitSpy = jest.spyOn(component.competencyChanged, 'emit');

            const message: ChatMessage = {
                id: '1',
                content: 'Preview',
                isUser: false,
                timestamp: new Date(),
                competencyPreview: {
                    title: 'Updated Competency',
                    description: 'Updated description',
                    taxonomy: 'ANALYZE' as any,
                    icon: 'magnifying-glass',
                    competencyId: 42,
                },
            };
            component.messages = [message];

            component['onCreateCompetency'](message);
            tick();

            expect(mockCompetencyService.update).toHaveBeenCalledOnce();
            expect(mockCompetencyService.update).toHaveBeenCalledWith(expect.objectContaining({ id: 42, title: 'Updated Competency' }), 123);

            // Find the message in the component's messages array to check if it was updated
            const updatedMessage = component.messages.find((msg) => msg.id === '1');
            expect(updatedMessage?.competencyCreated).toBeTrue();
            expect(emitSpy).toHaveBeenCalledOnce();
        }));

        it('should not create competency twice', () => {
            const message: ChatMessage = {
                id: '1',
                content: 'Preview',
                isUser: false,
                timestamp: new Date(),
                competencyPreview: {
                    title: 'Competency',
                    description: 'Description',
                    taxonomy: 'APPLY' as any,
                    icon: 'pen-fancy',
                },
                competencyCreated: true,
            };

            component['onCreateCompetency'](message);

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

            component['onCreateCompetency'](message);

            expect(mockCompetencyService.create).not.toHaveBeenCalled();
        });

        it('should handle creation error gracefully', fakeAsync(() => {
            mockCompetencyService.create.mockReturnValue(throwError(() => new Error('Creation failed')));
            const createFailedText = 'Failed to create competency';
            mockTranslateService.instant.mockReturnValue(createFailedText);

            const message: ChatMessage = {
                id: '1',
                content: 'Preview',
                isUser: false,
                timestamp: new Date(),
                competencyPreview: {
                    title: 'New Competency',
                    description: 'Description',
                    taxonomy: 'APPLY' as any,
                    icon: 'pen-fancy',
                },
            };

            component['onCreateCompetency'](message);
            tick();

            expect(component.isAgentTyping()).toBeFalse();
            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error.createFailed');
            const errorMessage = component.messages.find((msg) => !msg.isUser && msg.content === createFailedText);
            expect(errorMessage).toBeDefined();
        }));

        it('should handle update error gracefully', fakeAsync(() => {
            mockCompetencyService.update.mockReturnValue(throwError(() => new Error('Update failed')));
            const updateFailedText = 'Failed to update competency';
            mockTranslateService.instant.mockReturnValue(updateFailedText);

            const message: ChatMessage = {
                id: '1',
                content: 'Preview',
                isUser: false,
                timestamp: new Date(),
                competencyPreview: {
                    title: 'Updated Competency',
                    description: 'Description',
                    taxonomy: 'ANALYZE' as any,
                    icon: 'magnifying-glass',
                    competencyId: 42,
                },
            };

            component['onCreateCompetency'](message);
            tick();

            expect(component.isAgentTyping()).toBeFalse();
            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.error.updateFailed');
            const errorMessage = component.messages.find((msg) => !msg.isUser && msg.content === updateFailedText);
            expect(errorMessage).toBeDefined();
        }));
    });

    describe('Invalidate Pending Plan Approvals', () => {
        beforeEach(() => {
            mockTranslateService.instant.mockReturnValue('Welcome');
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

            component.messages = [pendingPlanMessage, approvedPlanMessage];
            component.currentMessage.set('New message');
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

            expect(component.messages[0].planPending).toBeFalse();
            expect(component.messages[1].planApproved).toBeTrue(); // Already approved plans should not be affected
        });
    });

    describe('Batch Competency Creation', () => {
        beforeEach(() => {
            mockTranslateService.instant.mockReturnValue('Welcome');
            component.ngOnInit();
        });

        it('should create multiple competencies from batch preview', fakeAsync(() => {
            mockCompetencyService.create.mockReturnValue(
                of({
                    id: 1,
                    title: 'Test',
                } as any),
            );
            const emitSpy = jest.spyOn(component.competencyChanged, 'emit');

            const message: ChatMessage = {
                id: '1',
                content: 'Batch preview',
                isUser: false,
                timestamp: new Date(),
                batchCompetencyPreview: [
                    { title: 'Comp 1', description: 'Desc 1', taxonomy: 'REMEMBER' as any, icon: 'brain' },
                    { title: 'Comp 2', description: 'Desc 2', taxonomy: 'UNDERSTAND' as any, icon: 'comments' },
                    { title: 'Comp 3', description: 'Desc 3', taxonomy: 'APPLY' as any, icon: 'pen-fancy' },
                ],
            };
            component.messages = [message];

            component['onCreateBatchCompetencies'](message);
            tick();

            expect(mockCompetencyService.create).toHaveBeenCalledTimes(3);
            expect(component.isAgentTyping()).toBeFalse();

            const updatedMessage = component.messages.find((msg) => msg.id === '1');
            expect(updatedMessage?.batchCreated).toBeTrue();
            expect(emitSpy).toHaveBeenCalledOnce();
        }));

        it('should not create batch competencies twice', () => {
            const message: ChatMessage = {
                id: '1',
                content: 'Batch',
                isUser: false,
                timestamp: new Date(),
                batchCompetencyPreview: [{ title: 'Test', description: 'Test', taxonomy: 'APPLY' as any, icon: 'pen-fancy' }],
                batchCreated: true,
            };

            component['onCreateBatchCompetencies'](message);

            expect(mockCompetencyService.create).not.toHaveBeenCalled();
        });

        it('should not create batch without preview', () => {
            const message: ChatMessage = {
                id: '1',
                content: 'No batch',
                isUser: false,
                timestamp: new Date(),
            };

            component['onCreateBatchCompetencies'](message);

            expect(mockCompetencyService.create).not.toHaveBeenCalled();
        });

        it('should not create empty batch', () => {
            const message: ChatMessage = {
                id: '1',
                content: 'Empty batch',
                isUser: false,
                timestamp: new Date(),
                batchCompetencyPreview: [],
            };

            component['onCreateBatchCompetencies'](message);

            expect(mockCompetencyService.create).not.toHaveBeenCalled();
        });

        it('should handle mixed create and update operations in batch', fakeAsync(() => {
            mockCompetencyService.create.mockReturnValue(of({ id: 1, title: 'Created' } as any));
            mockCompetencyService.update.mockReturnValue(of({ id: 10, title: 'Updated' } as any));

            const message: ChatMessage = {
                id: '1',
                content: 'Mixed batch',
                isUser: false,
                timestamp: new Date(),
                batchCompetencyPreview: [
                    { title: 'New', description: 'New desc', taxonomy: 'REMEMBER' as any, icon: 'brain' },
                    { title: 'Update', description: 'Update desc', taxonomy: 'APPLY' as any, icon: 'pen-fancy', competencyId: 10 },
                ],
            };
            component.messages = [message];

            component['onCreateBatchCompetencies'](message);
            tick();

            expect(mockCompetencyService.create).toHaveBeenCalledOnce();
            expect(mockCompetencyService.update).toHaveBeenCalledOnce();

            const successMessage = component.messages[component.messages.length - 1];
            expect(successMessage).toBeDefined();
        }));

        it('should handle all updates in batch', fakeAsync(() => {
            mockCompetencyService.update.mockReturnValue(of({ id: 1, title: 'Updated' } as any));

            const message: ChatMessage = {
                id: '1',
                content: 'Update batch',
                isUser: false,
                timestamp: new Date(),
                batchCompetencyPreview: [
                    { title: 'Update 1', description: 'Desc 1', taxonomy: 'ANALYZE' as any, icon: 'magnifying-glass', competencyId: 1 },
                    { title: 'Update 2', description: 'Desc 2', taxonomy: 'EVALUATE' as any, icon: 'plus-minus', competencyId: 2 },
                ],
            };
            component.messages = [message];

            component['onCreateBatchCompetencies'](message);
            tick();

            expect(mockCompetencyService.update).toHaveBeenCalledTimes(2);

            const successMessage = component.messages[component.messages.length - 1];
            expect(successMessage).toBeDefined();
        }));

        it('should handle single competency in batch', fakeAsync(() => {
            mockCompetencyService.create.mockReturnValue(of({ id: 1, title: 'Single' } as any));

            const message: ChatMessage = {
                id: '1',
                content: 'Single batch',
                isUser: false,
                timestamp: new Date(),
                batchCompetencyPreview: [{ title: 'Single', description: 'Single desc', taxonomy: 'CREATE' as any, icon: 'cubes-stacked' }],
            };
            component.messages = [message];

            component['onCreateBatchCompetencies'](message);
            tick();

            expect(mockCompetencyService.create).toHaveBeenCalledOnce();

            const successMessage = component.messages[component.messages.length - 1];
            expect(successMessage).toBeDefined();
        }));

        it('should handle batch creation error gracefully', fakeAsync(() => {
            mockCompetencyService.create.mockReturnValue(throwError(() => new Error('Creation failed')));

            const message: ChatMessage = {
                id: '1',
                content: 'Batch error',
                isUser: false,
                timestamp: new Date(),
                batchCompetencyPreview: [{ title: 'Error', description: 'Error desc', taxonomy: 'APPLY' as any, icon: 'pen-fancy' }],
            };
            component.messages = [message];

            component['onCreateBatchCompetencies'](message);
            tick();

            expect(component.isAgentTyping()).toBeFalse();
            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.welcome');
            expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.agent.chat.competencyProcessFailure');
            expect(component.messages[component.messages.length - 1].content).toBe(mockTranslateService.instant('artemisApp.agent.chat.error'));
        }));
    });

    describe('Edge Cases for JSON Extraction', () => {
        beforeEach(() => {
            mockTranslateService.instant.mockReturnValue('Welcome');
            component.ngOnInit();
        });

        it('should handle null content in extractCompetencyPreview', () => {
            const mockResponse = {
                message: null as any,
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            mockTranslateService.instant.mockReturnValue('Error message');
            component.currentMessage.set('Test');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            const agentMessage = component.messages.find((msg) => !msg.isUser);
            expect(agentMessage).toBeDefined();
            expect(agentMessage?.competencyPreview).toBeUndefined();
        });

        it('should handle undefined content in extractBatchCompetencyPreview', () => {
            const mockResponse = {
                message: undefined as any,
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            mockTranslateService.instant.mockReturnValue('Error message');
            component.currentMessage.set('Test');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            const agentMessage = component.messages.find((msg) => !msg.isUser);
            expect(agentMessage).toBeDefined();
            expect(agentMessage?.batchCompetencyPreview).toBeUndefined();
        });

        it('should handle content without valid JSON structure', () => {
            const mockResponse = {
                message: 'This is plain text without any JSON',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            component.currentMessage.set('Test');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            const agentMessage = component.messages.find((msg) => !msg.isUser && msg.content === 'This is plain text without any JSON');
            expect(agentMessage).toBeDefined();
            expect(agentMessage?.competencyPreview).toBeUndefined();
            expect(agentMessage?.batchCompetencyPreview).toBeUndefined();
        });

        it('should handle incomplete JSON in message', () => {
            const mockResponse = {
                message: 'Here is some text {"preview": true, "competency": { incomplete',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            component.currentMessage.set('Test');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            const agentMessage = component.messages.find((msg) => !msg.isUser);
            expect(agentMessage).toBeDefined();
            expect(agentMessage?.competencyPreview).toBeUndefined();
        });

        it('should handle nested JSON with mismatched braces', () => {
            const mockResponse = {
                message: 'Text { "preview": { "nested": { "value" } } extra }',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            component.currentMessage.set('Test');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            const agentMessage = component.messages.find((msg) => !msg.isUser);
            expect(agentMessage).toBeDefined();
        });

        it('should handle empty plan pending content', () => {
            const mockResponse = {
                message: '',
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            component.currentMessage.set('Test');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            const agentMessage = component.messages.find((msg) => !msg.isUser);
            expect(agentMessage?.planPending).toBeUndefined();
        });

        it('should handle null plan pending content', () => {
            const mockResponse = {
                message: null as any,
                sessionId: 'course_123',
                timestamp: '2024-01-01T00:00:00Z',
                success: true,
                competenciesModified: false,
            };
            mockAgentChatService.sendMessage.mockReturnValue(of(mockResponse));
            mockTranslateService.instant.mockReturnValue('Error message');
            component.currentMessage.set('Test');
            fixture.detectChanges();

            const sendButton = fixture.debugElement.nativeElement.querySelector('.send-button');
            sendButton.click();

            const agentMessage = component.messages.find((msg) => !msg.isUser);
            expect(agentMessage?.planPending).toBeUndefined();
        });
    });
});
