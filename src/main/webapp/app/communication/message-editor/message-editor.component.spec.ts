import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MessageEditorComponent } from './message-editor.component';
import { MessageEditorSuggestions } from './message-editor-suggestions';
import { MessageEditorToolsComponent } from './message-editor-tools.component';
import { MessageEditorResources } from './message-editor-resources';
import { FileUploaderService } from 'app/foundation/service/file-uploader.service';
import { By } from '@angular/platform-browser';

describe('Formatted message composer', () => {
    let fixture: ComponentFixture<MessageEditorComponent>;
    let component: MessageEditorComponent;
    let editable: HTMLElement;
    const search = vi.fn();

    function caretAtEnd() {
        const range = document.createRange();
        range.selectNodeContents(editable.querySelector('p')!);
        range.collapse(false);
        // Completion is scoped to a text node, just like browser text input.
        const text = editable.querySelector('p')!.lastChild!;
        if (text.nodeType === Node.TEXT_NODE) {
            range.setStart(text, text.textContent!.length);
            range.collapse(true);
        }
        const selection = window.getSelection()!;
        selection.removeAllRanges();
        selection.addRange(range);
    }

    beforeEach(async () => {
        search.mockReset().mockResolvedValue([{ label: '@Anna', kind: 'user', markdown: '[user]Anna(anna)[/user]' }]);
        await TestBed.configureTestingModule({
            imports: [MessageEditorComponent],
            providers: [MockProvider(TranslateService), MockProvider(FileUploaderService, { acceptedMarkdownFileExtensions: [] })],
        })
            .overrideComponent(MessageEditorComponent, { set: { providers: [{ provide: MessageEditorSuggestions, useValue: { search } }] } })
            .overrideComponent(MessageEditorToolsComponent, { set: { providers: [MockProvider(MessageEditorResources)] } })
            .compileComponents();
        fixture = TestBed.createComponent(MessageEditorComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('content', 'Hello');
        fixture.detectChanges();
        await fixture.whenStable();
        editable = fixture.nativeElement.querySelector('[data-testid="formatted-message-input"]');
    });

    afterEach(() => {
        vi.useRealTimers();
        fixture.destroy();
    });

    it('immediately emits the current message without debounce', () => {
        const emit = vi.spyOn(component.contentChange, 'emit');
        editable.innerHTML = '<p>Hello <strong>world</strong></p>';
        editable.dispatchEvent(new InputEvent('input', { bubbles: true }));
        expect(emit).toHaveBeenLastCalledWith('Hello **world**');
    });

    it('inserts a picked reference at a captured caret and blocks sending while an upload is pending', () => {
        caretAtEnd();
        const tools = fixture.debugElement.query(By.directive(MessageEditorToolsComponent)).componentInstance as MessageEditorToolsComponent;
        const emit = vi.spyOn(component.contentChange, 'emit');
        tools.insert.emit({ markdown: '[faq]Question(/courses/1/faq?faqId=1)[/faq]', range: tools.captureSelection()()! });
        expect(emit).toHaveBeenLastCalledWith('Hello[faq]Question(/courses/1/faq?faqId=1)[/faq]');
        const bubbled = vi.fn();
        fixture.nativeElement.addEventListener('keydown', bubbled);
        tools.uploadingChange.emit(true);
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
        expect(bubbled).not.toHaveBeenCalled();
        tools.uploadingChange.emit(false);
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
        expect(bubbled).toHaveBeenCalledOnce();
    });

    it('routes pasted and dropped files to the existing conversation upload flow', () => {
        const tools = fixture.debugElement.query(By.directive(MessageEditorToolsComponent)).componentInstance as MessageEditorToolsComponent;
        const upload = vi.spyOn(tools, 'uploadFiles').mockResolvedValue(undefined);
        const files = [new File(['image'], 'image.png')];
        const paste = new Event('paste', { bubbles: true, cancelable: true });
        Object.defineProperty(paste, 'clipboardData', { value: { files } });
        editable.dispatchEvent(paste);
        const drop = new Event('drop', { bubbles: true, cancelable: true });
        Object.defineProperty(drop, 'dataTransfer', { value: { files } });
        editable.dispatchEvent(drop);
        expect(upload).toHaveBeenNthCalledWith(1, files);
        expect(upload).toHaveBeenNthCalledWith(2, files);
        expect(paste.defaultPrevented).toBe(true);
        expect(drop.defaultPrevented).toBe(true);
    });

    it('handles composition and Shift+Enter without bubbling a send shortcut', () => {
        const composing = new KeyboardEvent('keydown', { key: 'Enter', isComposing: true, bubbles: true, cancelable: true });
        const bubbled = vi.fn();
        fixture.nativeElement.addEventListener('keydown', bubbled);
        editable.dispatchEvent(composing);
        expect(bubbled).not.toHaveBeenCalled();
        caretAtEnd();
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true, bubbles: true, cancelable: true }));
        expect(bubbled).not.toHaveBeenCalled();
    });

    it('uses Enter to accept a completion without sending', async () => {
        fixture.componentRef.setInput('content', '@ann');
        fixture.detectChanges();
        await fixture.whenStable();
        caretAtEnd();
        await component.refreshSuggestions();
        const emit = vi.spyOn(component.contentChange, 'emit');
        const bubbled = vi.fn();
        fixture.nativeElement.addEventListener('keydown', bubbled);
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
        expect(emit).toHaveBeenLastCalledWith('[user]Anna(anna)[/user]');
        expect(bubbled).not.toHaveBeenCalled();
    });

    it('ignores search results that arrive after the query is dismissed', async () => {
        let resolve!: (value: { label: string; kind: 'user'; markdown: string }[]) => void;
        search.mockReturnValue(new Promise((complete) => (resolve = complete)));
        fixture.componentRef.setInput('content', '@ann');
        fixture.detectChanges();
        await fixture.whenStable();
        caretAtEnd();
        const pending = component.refreshSuggestions();
        component.dismissSuggestions();
        resolve([{ label: '@Anna', kind: 'user', markdown: '[user]Anna(anna)[/user]' }]);
        await pending;
        expect(component.suggestions()).toEqual([]);
        expect(component.suggestionsOpen()).toBe(false);
    });

    it('dismisses completion ranges before toolbar formatting moves the underlying nodes', async () => {
        fixture.componentRef.setInput('content', '@ann');
        fixture.detectChanges();
        await fixture.whenStable();
        caretAtEnd();
        await component.refreshSuggestions();
        expect(component.suggestionsOpen()).toBe(true);
        (fixture.nativeElement.querySelector('[data-testid="message-block-ul"]') as HTMLButtonElement).click();
        expect(component.suggestionsOpen()).toBe(false);
        expect(component.suggestions()).toEqual([]);
    });

    it('keeps typed input and shows an error when completion search fails', async () => {
        search.mockRejectedValue(new Error('offline'));
        fixture.componentRef.setInput('content', '@ann');
        fixture.detectChanges();
        await fixture.whenStable();
        caretAtEnd();
        await component.refreshSuggestions();
        expect(editable.textContent).toContain('@ann');
        expect(component.searchFailed()).toBe(true);
    });

    it('prevents link clicks from navigating while composing', () => {
        editable.innerHTML = '<p><a href="https://example.org">Guide</a></p>';
        const click = new MouseEvent('click', { bubbles: true, cancelable: true });
        editable.querySelector('a')!.dispatchEvent(click);
        expect(click.defaultPrevented).toBe(true);
        expect(component.linkOpen()).toBe(true);
        expect(component.linkUrl()).toBe('https://example.org');
    });

    it('preserves the selection when applying toolbar formatting and block styles', () => {
        caretAtEnd();
        const button = fixture.nativeElement.querySelector('[data-testid="message-format-strong"]') as HTMLButtonElement;
        const down = new MouseEvent('mousedown', { bubbles: true, cancelable: true });
        button.dispatchEvent(down);
        expect(down.defaultPrevented).toBe(true);
        button.click();
        expect(editable.querySelector('strong')).not.toBeNull();
        (fixture.nativeElement.querySelector('[data-testid="message-block-ul"]') as HTMLButtonElement).click();
        expect(editable.querySelector('ul')).not.toBeNull();
    });

    it.each(['b', 'i', 'u', 'k'])('handles the Ctrl/Cmd+%s shortcut inside the composer', (key) => {
        caretAtEnd();
        const event = new KeyboardEvent('keydown', { key, ctrlKey: true, bubbles: true, cancelable: true });
        editable.dispatchEvent(event);
        expect(event.defaultPrevented).toBe(true);
        if (key === 'k') {
            expect(component.linkOpen()).toBe(true);
        } else {
            expect(editable.querySelector(key === 'b' ? 'strong' : key === 'i' ? 'em' : 'ins')).not.toBeNull();
        }
    });

    it('provides keyboard and browser-event undo and redo', () => {
        const emit = vi.spyOn(component.contentChange, 'emit');
        caretAtEnd();
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'b', ctrlKey: true, bubbles: true, cancelable: true }));
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true, cancelable: true }));
        expect(editable.querySelector('strong')).toBeNull();
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, shiftKey: true, bubbles: true, cancelable: true }));
        expect(editable.querySelector('strong')).not.toBeNull();
        editable.dispatchEvent(new InputEvent('beforeinput', { inputType: 'historyUndo', bubbles: true, cancelable: true }));
        expect(editable.querySelector('strong')).toBeNull();
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'y', ctrlKey: true, bubbles: true, cancelable: true }));
        editable.dispatchEvent(new InputEvent('beforeinput', { inputType: 'historyUndo', bubbles: true, cancelable: true }));
        editable.dispatchEvent(new InputEvent('beforeinput', { inputType: 'historyRedo', bubbles: true, cancelable: true }));
        expect(editable.querySelector('strong')).not.toBeNull();
        expect(emit).toHaveBeenCalled();
    });

    it('commits a composed word only after composition ends', () => {
        const emit = vi.spyOn(component.contentChange, 'emit');
        editable.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }));
        editable.innerHTML = '<p>日本語</p>';
        editable.dispatchEvent(new InputEvent('input', { isComposing: true, bubbles: true }));
        expect(emit).not.toHaveBeenCalled();
        editable.dispatchEvent(new CompositionEvent('compositionend', { bubbles: true }));
        expect(emit).toHaveBeenLastCalledWith('日本語');
    });

    it('owns cancellable code insertion so WebKit cannot move typing before a line break', () => {
        caretAtEnd();
        (fixture.nativeElement.querySelector('[data-testid="message-block-pre"]') as HTMLButtonElement).click();
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
        const emit = vi.spyOn(component.contentChange, 'emit');
        const input = new InputEvent('beforeinput', { inputType: 'insertText', data: 'second', bubbles: true, cancelable: true });
        editable.dispatchEvent(input);
        expect(input.defaultPrevented).toBe(true);
        expect(emit).toHaveBeenLastCalledWith('```\nHello\nsecond\n```');
    });

    it('owns the first typed text after Shift+Enter so WebKit keeps it on the new line', () => {
        caretAtEnd();
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true, bubbles: true, cancelable: true }));
        const emit = vi.spyOn(component.contentChange, 'emit');
        const input = new InputEvent('beforeinput', { inputType: 'insertText', data: 'second', bubbles: true, cancelable: true });
        editable.dispatchEvent(input);
        expect(input.defaultPrevented).toBe(true);
        expect(emit).toHaveBeenLastCalledWith('Hello\nsecond');
    });

    it('inserts trigger characters through the toolbar', () => {
        caretAtEnd();
        (fixture.nativeElement.querySelector('[data-testid="message-reference-button"]') as HTMLButtonElement).click();
        expect(editable.textContent).toContain('#');
        (fixture.nativeElement.querySelector('[data-testid="message-mention-button"]') as HTMLButtonElement).click();
        expect(editable.textContent).toContain('@');
    });

    it('validates a link, applies it on Enter, and unlinks it while preserving its label', () => {
        caretAtEnd();
        (fixture.nativeElement.querySelector('[data-testid="message-link-button"]') as HTMLButtonElement).click();
        fixture.detectChanges();
        const input = (testId: string, value: string) => {
            const field = fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLInputElement;
            field.value = value;
            field.dispatchEvent(new Event('input', { bubbles: true }));
            return field;
        };
        input('message-link-url', 'javascript:alert(1)');
        (fixture.nativeElement.querySelector('[data-testid="apply-message-link"]') as HTMLButtonElement).click();
        expect(component.linkInvalid()).toBe(true);
        input('message-link-text', 'Guide');
        const url = input('message-link-url', 'https://example.org');
        url.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
        expect(component.linkOpen()).toBe(false);
        const link = editable.querySelector('a')!;
        expect(link.textContent).toBe('Guide');
        link.click();
        fixture.detectChanges();
        (fixture.nativeElement.querySelector('[data-testid="remove-message-link"]') as HTMLButtonElement).click();
        expect(editable.querySelector('a')).toBeNull();
        expect(editable.textContent).toContain('Guide');
    });

    it('does not send when keyboard activation targets a toolbar button', () => {
        const bubbled = vi.fn();
        fixture.nativeElement.parentElement.addEventListener('keydown', bubbled);
        fixture.nativeElement.querySelector('[data-testid="message-link-button"]').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
        expect(bubbled).not.toHaveBeenCalled();
        fixture.nativeElement.parentElement.removeEventListener('keydown', bubbled);
    });

    it('lets a normal Enter reach the send handler but keeps Enter inside lists and code', () => {
        const bubbled = vi.fn();
        fixture.nativeElement.addEventListener('keydown', bubbled);
        caretAtEnd();
        const enter = () => editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
        enter();
        expect(bubbled).toHaveBeenCalledOnce();
        (fixture.nativeElement.querySelector('[data-testid="message-block-pre"]') as HTMLButtonElement).click();
        enter();
        expect(bubbled).toHaveBeenCalledOnce();
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true, cancelable: true }));
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true, cancelable: true }));
        (fixture.nativeElement.querySelector('[data-testid="message-block-ul"]') as HTMLButtonElement).click();
        enter();
        expect(bubbled).toHaveBeenCalledOnce();
    });

    it('navigates and dismisses completion rows and prevents destructive shortcuts from submitting', async () => {
        fixture.componentRef.setInput('content', '@ann');
        fixture.detectChanges();
        await fixture.whenStable();
        caretAtEnd();
        await component.refreshSuggestions();
        for (const key of ['ArrowDown', 'ArrowUp']) {
            editable.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
            expect(component.activeIndex()).toBe(0);
        }
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
        expect(component.suggestionsOpen()).toBe(false);
        await component.refreshSuggestions();
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }));
        expect(editable.querySelector('[data-message-reference]')).not.toBeNull();
        const paragraph = editable.querySelector('p')!;
        const range = document.createRange();
        range.setStart(paragraph, 0);
        range.collapse(true);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'Delete', bubbles: true, cancelable: true }));
        expect(window.getSelection()!.toString()).toBe('@Anna');
    });
});
