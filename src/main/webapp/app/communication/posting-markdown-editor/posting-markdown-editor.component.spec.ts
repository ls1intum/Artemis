import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MockComponent, MockPipe } from 'ng-mocks';
import { PostingMarkdownEditorComponent } from './posting-markdown-editor.component';
import { MessageEditorComponent } from 'app/communication/message-editor/message-editor.component';
import { RedirectToIrisButtonComponent } from 'app/communication/shared/redirect-to-iris-button/redirect-to-iris-button.component';
import { CommunicationService } from 'app/communication/service/communication.service';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { CourseConversationsService } from 'app/communication/service/course-conversations.service';
import { BehaviorSubject } from 'rxjs';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { MockCommunicationService } from 'test/helpers/mocks/service/mock-communication.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { PostingEditType } from 'app/communication/communication.util';

describe('Posting message editor integration', () => {
    let fixture: ComponentFixture<PostingMarkdownEditorComponent>;
    let component: PostingMarkdownEditorComponent;
    const shownConversation = new BehaviorSubject<ConversationDTO | undefined>(undefined);

    beforeEach(() => {
        shownConversation.next(undefined);
        TestBed.configureTestingModule({
            imports: [PostingMarkdownEditorComponent],
            providers: [
                { provide: CommunicationService, useClass: MockCommunicationService },
                { provide: ConversationService, useValue: { getConversationName: (conversation?: { name?: string }) => conversation?.name ?? '' } },
                { provide: CourseConversationsService, useValue: { activeConversation$: shownConversation.asObservable() } },
            ],
        }).overrideComponent(PostingMarkdownEditorComponent, {
            remove: { imports: [MessageEditorComponent, RedirectToIrisButtonComponent, ArtemisTranslatePipe] },
            add: { imports: [MockComponent(MessageEditorComponent), MockComponent(RedirectToIrisButtonComponent), MockPipe(ArtemisTranslatePipe)] },
        });
        fixture = TestBed.createComponent(PostingMarkdownEditorComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('course', { id: 123 });
    });

    afterEach(() => vi.restoreAllMocks());

    it('mounts only the rich composer without a hidden Markdown editor or preview', () => {
        component.writeValue('Hello **world**');
        fixture.detectChanges();
        const editor = fixture.debugElement.query(By.directive(MessageEditorComponent)).componentInstance as MessageEditorComponent;
        expect(editor.content()).toBe('Hello **world**');
        expect(editor.courseId()).toBe(123);
        expect(fixture.nativeElement.querySelector('jhi-markdown-editor-monaco')).toBeNull();
        expect(fixture.nativeElement.querySelector('jhi-posting-content')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="toggle-formatted-message-editor"]')).toBeNull();
    });

    it('immediately updates the form and value-change listeners from rich edits', () => {
        const changed = vi.fn();
        const emitted = vi.spyOn(component.valueChange, 'emit');
        component.registerOnChange(changed);
        component.writeValue('Before');
        expect(changed).not.toHaveBeenCalled();
        fixture.detectChanges();
        const editor = fixture.debugElement.query(By.directive(MessageEditorComponent)).componentInstance as MessageEditorComponent;
        editor.contentChange.emit('Latest **text**');
        expect(component.content()).toBe('Latest **text**');
        expect(changed).toHaveBeenCalledExactlyOnceWith('Latest **text**');
        expect(emitted).toHaveBeenCalledOnce();
    });

    it.each(['$$x^2$$', '> [!WARNING]\n> Body', '| A | B |\n|---|---|\n| 1 | 2 |', '<div>Legacy HTML</div>'])(
        'prevents destructive editing of unsupported legacy content: %s',
        (source) => {
            component.writeValue(source);
            fixture.detectChanges();
            expect(component.content()).toBe(source);
            expect(component.validate()).toEqual({ unsupportedMessageFormatting: true });
            expect(fixture.debugElement.query(By.directive(MessageEditorComponent))).toBeNull();
            expect(fixture.nativeElement.querySelector('jhi-markdown-editor-monaco')).toBeNull();
            expect(fixture.nativeElement.querySelector('[data-testid="unsupported-message-formatting"]')).not.toBeNull();
        },
    );

    it('revalidates and resumes rich editing when the next message is supported', () => {
        const changed = vi.fn();
        component.registerOnValidatorChange(changed);
        component.writeValue('$$x^2$$');
        expect(component.validate()).toEqual({ unsupportedMessageFormatting: true });
        component.writeValue('Hello');
        fixture.detectChanges();
        expect(component.validate()).toBeNull();
        expect(fixture.debugElement.query(By.directive(MessageEditorComponent))).not.toBeNull();
        expect(changed).toHaveBeenCalledTimes(2);
    });

    it('keeps escaped dollar text editable without interpreting it as a formula', () => {
        component.writeValue('Literal \\$x^2\\$');
        fixture.detectChanges();
        expect(component.validate()).toBeNull();
        expect(fixture.debugElement.query(By.directive(MessageEditorComponent))).not.toBeNull();
    });

    it.each(['Costs $5 and $10', '$5 for `a` and $10 for b'])('keeps existing currency messages editable: %s', (source) => {
        component.writeValue(source);
        fixture.detectChanges();
        expect(component.validate()).toBeNull();
        const editor = fixture.debugElement.query(By.directive(MessageEditorComponent)).componentInstance as MessageEditorComponent;
        expect(editor.content()).toBe(source);
        expect(fixture.nativeElement.querySelector('[data-testid="unsupported-message-formatting"]')).toBeNull();
    });

    it('lets the user discard only an unsupported unsent draft and resume composing', () => {
        component.writeValue('$$legacy draft$$');
        const changed = vi.fn();
        component.registerOnChange(changed);
        fixture.detectChanges();
        expect(changed).not.toHaveBeenCalled();
        const discard = fixture.nativeElement.querySelector('[data-testid="discard-unsupported-message-draft"]') as HTMLButtonElement;
        expect(discard).not.toBeNull();
        discard.click();
        fixture.detectChanges();
        expect(component.content()).toBe('');
        expect(changed).toHaveBeenCalledExactlyOnceWith('');
        expect(component.validate()).toBeNull();
        expect(fixture.debugElement.query(By.directive(MessageEditorComponent))).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="discard-unsupported-message-draft"]')).toBeNull();
    });

    it('does not offer draft deletion when an unsupported sent message is being edited', () => {
        fixture.componentRef.setInput('editType', PostingEditType.UPDATE);
        component.writeValue('$$original message$$');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="discard-unsupported-message-draft"]')).toBeNull();
        expect(component.content()).toBe('$$original message$$');
        expect(component.validate()).toEqual({ unsupportedMessageFormatting: true });
    });

    it('normalizes an empty form value without emitting an edit', () => {
        const changed = vi.fn();
        component.registerOnChange(changed);
        component.registerOnTouched(() => {});
        component.writeValue(undefined);
        expect(component.content()).toBe('');
        expect(changed).not.toHaveBeenCalled();
    });

    it('invalidates during upload and revalidates when upload finishes', () => {
        const changed = vi.fn();
        component.registerOnValidatorChange(changed);
        component.formattedUploadStateChanged(true);
        expect(component.validate()).toEqual({ attachmentUploading: true });
        component.formattedUploadStateChanged(false);
        expect(component.validate()).toBeNull();
        expect(changed).toHaveBeenCalledTimes(2);
    });

    it('keeps upload validation on same-value writes and clears it for a different message', () => {
        component.writeValue('Hello');
        component.formattedUploadStateChanged(true);
        const changed = vi.fn();
        component.registerOnValidatorChange(changed);
        component.writeValue('Hello');
        expect(component.validate()).toEqual({ attachmentUploading: true });
        expect(changed).not.toHaveBeenCalled();
        component.writeValue('Other message');
        expect(component.validate()).toBeNull();
        expect(changed).toHaveBeenCalledOnce();
    });

    it('passes stable draft identity, conversation and edit mode independently of text', () => {
        fixture.componentRef.setInput('activeConversation', { id: 42 });
        fixture.componentRef.setInput('editType', PostingEditType.UPDATE);
        fixture.componentRef.setInput('suppressNewlineOnEnter', false);
        fixture.componentRef.setInput('draftIdentity', 'reply:11:new');
        component.writeValue('');
        fixture.detectChanges();
        const editor = fixture.debugElement.query(By.directive(MessageEditorComponent)).componentInstance as MessageEditorComponent;
        expect(editor.conversation()?.id).toBe(42);
        expect(editor.editing()).toBe(true);
        expect(editor.suppressNewlineOnEnter()).toBe(false);
        expect(editor.draftIdentity()).toBe('reply:11:new');
        fixture.componentRef.setInput('draftIdentity', 'reply:12:new');
        fixture.detectChanges();
        expect(editor.draftIdentity()).toBe('reply:12:new');
    });

    it('names the conversation for the hint of the empty editor and words it for a reply', () => {
        fixture.componentRef.setInput('activeConversation', { id: 42, name: 'announcement' });
        component.writeValue('');
        fixture.detectChanges();
        const editor = fixture.debugElement.query(By.directive(MessageEditorComponent)).componentInstance as MessageEditorComponent;
        expect(editor.conversationName()).toBe('announcement');
        expect(editor.reply()).toBe(false);

        fixture.componentRef.setInput('isReply', true);
        fixture.componentRef.setInput('activeConversation', { id: 43, name: 'organisation' });
        fixture.detectChanges();
        expect(editor.conversationName()).toBe('organisation');
        expect(editor.reply()).toBe(true);
    });

    it('takes the name from the conversation that the course view shows when the new post names only its id', () => {
        fixture.componentRef.setInput('activeConversation', { id: 42 });
        component.writeValue('');
        fixture.detectChanges();
        const editor = fixture.debugElement.query(By.directive(MessageEditorComponent)).componentInstance as MessageEditorComponent;
        expect(editor.conversationName()).toBe('');

        shownConversation.next({ id: 42, name: 'random' } as ConversationDTO);
        fixture.detectChanges();
        expect(editor.conversationName()).toBe('random');

        // The name of another conversation does not label this composer.
        fixture.componentRef.setInput('activeConversation', { id: 43 });
        fixture.detectChanges();
        expect(editor.conversationName()).toBe('');
    });

    it('disables Send while an upload is pending even when the parent form was valid', () => {
        fixture.componentRef.setInput('isFormGroupValid', true);
        component.writeValue('Hello');
        component.formattedUploadStateChanged(true);
        fixture.detectChanges();
        expect((fixture.nativeElement.querySelector('[data-testid="send-formatted-message"]') as HTMLButtonElement).disabled).toBe(true);
        component.formattedUploadStateChanged(false);
        fixture.detectChanges();
        expect((fixture.nativeElement.querySelector('[data-testid="send-formatted-message"]') as HTMLButtonElement).disabled).toBe(false);
    });

    it('does not submit through the parent Enter handler when Cancel has keyboard focus', () => {
        fixture.componentRef.setInput('showCloseButton', true);
        component.writeValue('A valid unsent reply');
        fixture.detectChanges();
        const parentConfirm = vi.fn();
        fixture.nativeElement.addEventListener('keydown', parentConfirm);
        const button = fixture.nativeElement.querySelector('[data-testid="close-message-editor"]') as HTMLButtonElement;
        const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
        button.dispatchEvent(enter);
        expect(parentConfirm).not.toHaveBeenCalled();
        expect(enter.defaultPrevented).toBe(false);
        expect(component.content()).toBe('A valid unsent reply');
    });

    it('keeps native editor Enter events available to the parent send handler', () => {
        component.writeValue('Ready to send');
        fixture.detectChanges();
        const parentConfirm = vi.fn();
        fixture.nativeElement.addEventListener('keydown', parentConfirm);
        fixture.debugElement.query(By.directive(MessageEditorComponent)).nativeElement.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
        expect(parentConfirm).toHaveBeenCalledOnce();
    });

    it('emits Cancel and leaves the current value unchanged', () => {
        fixture.componentRef.setInput('showCloseButton', true);
        component.writeValue('Hello');
        const close = vi.spyOn(component.closeEditor, 'emit');
        fixture.detectChanges();
        (fixture.nativeElement.querySelector('[data-testid="close-message-editor"]') as HTMLButtonElement).click();
        expect(close).toHaveBeenCalledOnce();
        expect(component.content()).toBe('Hello');
    });

    it('keeps unsupported source unchanged when Cancel closes the editor', () => {
        const source = '$$x^2$$';
        const changed = vi.fn();
        const close = vi.spyOn(component.closeEditor, 'emit');
        component.registerOnChange(changed);
        fixture.componentRef.setInput('showCloseButton', true);
        component.writeValue(source);
        fixture.detectChanges();

        (fixture.nativeElement.querySelector('[data-testid="close-message-editor"]') as HTMLButtonElement).click();
        expect(close).toHaveBeenCalledOnce();
        expect(component.content()).toBe(source);
        expect(component.validate()).toEqual({ unsupportedMessageFormatting: true });
        expect(changed).not.toHaveBeenCalled();
        expect(fixture.nativeElement.querySelector('jhi-posting-content')).toBeNull();
    });

    it('blocks Send and Iris for unsupported source even when the parent form was valid', () => {
        fixture.componentRef.setInput('isFormGroupValid', true);
        component.writeValue('$$x^2$$');
        fixture.detectChanges();

        expect((fixture.nativeElement.querySelector('[data-testid="send-formatted-message"]') as HTMLButtonElement).disabled).toBe(true);
        const iris = fixture.debugElement.query(By.directive(RedirectToIrisButtonComponent)).componentInstance as RedirectToIrisButtonComponent;
        expect(iris.disabled()).toBe(true);
        expect(component.content()).toBe('$$x^2$$');
    });

    it('marks the control touched when focus leaves the composer', () => {
        const touched = vi.fn();
        component.registerOnTouched(touched);
        fixture.detectChanges();
        fixture.nativeElement.dispatchEvent(new FocusEvent('focusout'));
        expect(touched).toHaveBeenCalledOnce();
    });

    it('keeps an empty touched required control neutral while preserving nonempty validation styling', () => {
        fixture.nativeElement.classList.add('ng-invalid', 'ng-touched');
        component.writeValue('');
        fixture.detectChanges();
        expect(fixture.nativeElement.style.borderLeftWidth).toBe('0px');
        component.writeValue('Nonempty');
        fixture.detectChanges();
        expect(fixture.nativeElement.style.borderLeftWidth).toBe('');
    });

    it('shows the length counter at the limit only when requested or over the limit', () => {
        fixture.componentRef.setInput('maxContentLength', 5);
        fixture.componentRef.setInput('isInputLengthDisplayed', false);
        component.writeValue('Hello');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="message-content-length"]')).toBeNull();
        component.writeValue('Hello!');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="message-content-length"]').textContent).toContain('6 / 5');
    });
});
