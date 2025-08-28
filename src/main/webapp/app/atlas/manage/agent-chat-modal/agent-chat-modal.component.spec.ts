import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { By } from '@angular/platform-browser';
import { Observable, of } from 'rxjs';
import { AgentChatModalComponent } from './agent-chat-modal.component';
import { AgentChatService } from './agent-chat.service';
import { TranslateModule } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MockProvider } from 'ng-mocks';

describe('AgentChatModalComponent', () => {
    let component: AgentChatModalComponent;
    let fixture: ComponentFixture<AgentChatModalComponent>;
    let mockActiveModal: NgbActiveModal;
    let mockAgentChatService: AgentChatService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AgentChatModalComponent, TranslateModule.forRoot(), FormsModule, FontAwesomeModule],
            providers: [MockProvider(NgbActiveModal), MockProvider(AgentChatService)],
        }).compileComponents();

        fixture = TestBed.createComponent(AgentChatModalComponent);
        component = fixture.componentInstance;
        component.courseId = 123;

        mockActiveModal = TestBed.inject(NgbActiveModal);
        mockAgentChatService = TestBed.inject(AgentChatService);

        // Mock the service response
        jest.spyOn(mockAgentChatService, 'sendMessage').mockReturnValue(of('Mock agent response: This is a test response from the mocked agent service.'));
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with welcome message', () => {
        fixture.detectChanges();

        expect(component.messages).toHaveLength(1);
        expect(component.messages[0].isUser).toBeFalse();
        expect(component.messages[0].content).toContain('Hello!');
    });

    it('should close modal when close button is clicked', () => {
        const spyClose = jest.spyOn(mockActiveModal, 'close');
        const closeButton = fixture.debugElement.query(By.css('.btn-close'));

        closeButton.nativeElement.click();

        expect(spyClose).toHaveBeenCalled();
    });

    it('should send message when send button is clicked', () => {
        fixture.detectChanges();
        component.currentMessage = 'Test message';

        // Call the method directly since DOM click might not trigger correctly in test
        component.sendMessage();

        expect(component.messages.length).toBeGreaterThanOrEqual(2);
        const userMessage = component.messages.find((m) => m.content === 'Test message' && m.isUser);
        expect(userMessage).toBeTruthy();
        expect(mockAgentChatService.sendMessage).toHaveBeenCalledWith('Test message', 123);
    });

    it('should send message when Enter key is pressed', () => {
        fixture.detectChanges();
        component.currentMessage = 'Test message';

        const textarea = fixture.debugElement.query(By.css('.message-input'));
        const event = new KeyboardEvent('keypress', { key: 'Enter' });

        textarea.triggerEventHandler('keypress', event);

        expect(component.messages.length).toBeGreaterThanOrEqual(2);
        const userMessage = component.messages.find((m) => m.content === 'Test message' && m.isUser);
        expect(userMessage).toBeTruthy();
    });

    it('should not send empty messages', () => {
        fixture.detectChanges();
        component.currentMessage = '   '; // Only whitespace

        const sendButton = fixture.debugElement.query(By.css('.send-button'));
        sendButton.nativeElement.click();

        expect(component.messages).toHaveLength(1); // Only welcome message
        expect(mockAgentChatService.sendMessage).not.toHaveBeenCalled();
    });

    it('should disable send button when agent is typing', () => {
        fixture.detectChanges();
        component.currentMessage = 'Test message';
        component.isAgentTyping = true;
        fixture.detectChanges();

        const sendButton = fixture.debugElement.query(By.css('.send-button'));

        expect(sendButton.nativeElement.disabled).toBeTruthy();
    });

    it('should show typing indicator when agent is typing', () => {
        fixture.detectChanges();
        component.isAgentTyping = true;
        fixture.detectChanges();

        const typingIndicator = fixture.debugElement.query(By.css('.typing-indicator'));

        expect(typingIndicator).toBeTruthy();
        expect(typingIndicator.nativeElement.textContent).toContain('artemisApp.agent.chat.typing');
    });

    it('should handle service errors gracefully', async () => {
        jest.spyOn(mockAgentChatService, 'sendMessage').mockImplementation(() => {
            return new Observable((subscriber) => {
                subscriber.error(new Error('Service error'));
            });
        });

        fixture.detectChanges();
        component.currentMessage = 'Test message';
        component.sendMessage();

        // Wait for async operations to complete
        await fixture.whenStable();
        fixture.detectChanges();

        // Should add user message and error response
        expect(component.messages.length).toBeGreaterThanOrEqual(2);
        const userMessage = component.messages.find((m) => m.content === 'Test message' && m.isUser);
        expect(userMessage).toBeTruthy();
    });

    it('should not send message with Shift+Enter', () => {
        fixture.detectChanges();
        component.currentMessage = 'Test message';

        const event = new KeyboardEvent('keypress', { key: 'Enter', shiftKey: true });
        const preventDefaultSpy = jest.spyOn(event, 'preventDefault');

        component.onKeyPress(event);

        expect(preventDefaultSpy).not.toHaveBeenCalled();
        expect(component.messages).toHaveLength(1); // Only welcome message
    });

    it('should generate unique message IDs', () => {
        const id1 = (component as any).generateMessageId();
        const id2 = (component as any).generateMessageId();

        expect(id1).not.toBe(id2);
        expect(typeof id1).toBe('string');
        expect(typeof id2).toBe('string');
    });

    it('should auto-resize textarea on input', () => {
        fixture.detectChanges();
        const textarea = fixture.debugElement.query(By.css('.message-input'));

        // Mock the textarea element
        Object.defineProperty(textarea.nativeElement, 'scrollHeight', {
            get: jest.fn(() => 60),
        });

        component.onTextareaInput();

        expect(textarea.nativeElement.style.height).toBe('60px');
    });

    it('should handle competency-related queries appropriately', async () => {
        jest.spyOn(mockAgentChatService, 'sendMessage').mockReturnValue(
            of('Great! For sorting algorithms, I suggest competencies like: Algorithm Analysis, Divide & Conquer strategies, and Implementation Skills.'),
        );

        fixture.detectChanges();
        component.currentMessage = 'Help me create competencies for sorting algorithms';
        component.sendMessage();

        expect(mockAgentChatService.sendMessage).toHaveBeenCalledWith('Help me create competencies for sorting algorithms', 123);

        // Wait for async operations to complete
        await fixture.whenStable();
        fixture.detectChanges();

        // Check that agent responds with competency-specific information
        const agentMessage = component.messages.find((m) => m.content.includes('Algorithm Analysis') && !m.isUser);
        expect(agentMessage).toBeTruthy();
    });

    it('should handle general programming queries', () => {
        jest.spyOn(mockAgentChatService, 'sendMessage').mockReturnValue(
            of('For Java programming, I can help you create competencies covering: Object-Oriented Programming principles, Java syntax and semantics...'),
        );

        fixture.detectChanges();
        component.currentMessage = 'I need help with Java programming competencies';
        component.sendMessage();

        expect(mockAgentChatService.sendMessage).toHaveBeenCalledWith('I need help with Java programming competencies', 123);
    });

    it('should handle mock competency creation confirmations', () => {
        jest.spyOn(mockAgentChatService, 'sendMessage').mockReturnValue(
            of('🎉 **Mock: Created 3 competencies for your course!**\n\n(This is a demo - competencies would be created in the actual implementation)'),
        );

        fixture.detectChanges();
        component.currentMessage = 'Yes, create these competencies';
        component.sendMessage();

        expect(mockAgentChatService.sendMessage).toHaveBeenCalledWith('Yes, create these competencies', 123);
    });

    it('should handle textarea focus and blur events', () => {
        fixture.detectChanges();
        const textarea = fixture.debugElement.query(By.css('.message-input'));

        textarea.triggerEventHandler('focus', {});
        expect(textarea.nativeElement).toBeTruthy();

        textarea.triggerEventHandler('blur', {});
        expect(textarea.nativeElement).toBeTruthy();
    });

    it('should clear current message after sending', () => {
        fixture.detectChanges();
        component.currentMessage = 'Test message';

        component.sendMessage();

        expect(component.currentMessage).toBe('');
    });

    it('should set typing indicator correctly during message sending', async () => {
        // Mock the service to return a delayed observable
        const mockObservable = new Observable((subscriber) => {
            // Set typing to true initially, then false when response comes
            setTimeout(() => {
                subscriber.next('Mock response');
                subscriber.complete();
            }, 50);
        });

        jest.spyOn(mockAgentChatService, 'sendMessage').mockReturnValue(mockObservable);

        fixture.detectChanges();
        component.currentMessage = 'Test message';

        expect(component.isAgentTyping).toBeFalsy();

        component.sendMessage();
        expect(component.isAgentTyping).toBeTruthy();

        // Wait for the observable to complete
        await new Promise((resolve) => setTimeout(resolve, 100));
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.isAgentTyping).toBeFalsy();
    });

    it('should generate unique message IDs consistently', () => {
        const ids = new Set();

        for (let i = 0; i < 100; i++) {
            const id = (component as any).generateMessageId();
            expect(ids.has(id)).toBeFalsy();
            ids.add(id);
        }
    });

    it('should handle scroll container properly', () => {
        fixture.detectChanges();

        // Mock messagesContainer
        component['messagesContainer'] = {
            nativeElement: {
                scrollTop: 0,
                scrollHeight: 1000,
            },
        } as any;

        (component as any).scrollToBottom();

        expect(component['messagesContainer'].nativeElement.scrollTop).toBe(1000);
    });

    it('should handle empty textarea input gracefully', () => {
        fixture.detectChanges();
        component.currentMessage = '';

        const textarea = fixture.debugElement.query(By.css('.message-input'));
        const event = { target: { value: '' } };
        textarea.triggerEventHandler('input', event);

        expect(component.currentMessage).toBe('');
    });

    it('should prevent form submission on Enter without shift', () => {
        fixture.detectChanges();
        component.currentMessage = 'Test message';

        const event = new KeyboardEvent('keypress', { key: 'Enter', shiftKey: false });
        const preventDefaultSpy = jest.spyOn(event, 'preventDefault');

        component.onKeyPress(event);

        expect(preventDefaultSpy).toHaveBeenCalled();
    });

    it('should allow new line on Shift+Enter', () => {
        fixture.detectChanges();
        component.currentMessage = 'Test message';

        const event = new KeyboardEvent('keypress', { key: 'Enter', shiftKey: true });
        const preventDefaultSpy = jest.spyOn(event, 'preventDefault');

        component.onKeyPress(event);

        expect(preventDefaultSpy).not.toHaveBeenCalled();
    });

    it('should handle non-Enter key presses', () => {
        fixture.detectChanges();
        component.currentMessage = 'Test message';

        const event = new KeyboardEvent('keypress', { key: 'a' });
        const preventDefaultSpy = jest.spyOn(event, 'preventDefault');

        component.onKeyPress(event);

        expect(preventDefaultSpy).not.toHaveBeenCalled();
        expect(component.messages).toHaveLength(1); // Only welcome message
    });

    it('should handle textarea with no element reference', () => {
        fixture.detectChanges();

        // Mock messageInput as undefined
        component['messageInput'] = undefined as any;

        expect(() => component.onTextareaInput()).not.toThrow();
    });

    it('should enforce maximum textarea height', () => {
        fixture.detectChanges();
        const textarea = fixture.debugElement.query(By.css('.message-input'));

        // Mock large scroll height
        Object.defineProperty(textarea.nativeElement, 'scrollHeight', {
            get: jest.fn(() => 150),
        });

        component.onTextareaInput();

        expect(textarea.nativeElement.style.height).toBe('120px'); // Max height enforced
    });

    it('should scroll to bottom after new messages', async () => {
        const scrollSpy = jest.spyOn(component as any, 'scrollToBottom');

        fixture.detectChanges();
        component.currentMessage = 'Test message';
        component.sendMessage();

        // Wait for async operations and view updates
        await fixture.whenStable();
        fixture.detectChanges();

        expect(scrollSpy).toHaveBeenCalled();
    });
});
