import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { signal } from '@angular/core';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { By } from '@angular/platform-browser';
import { EmojiEvent } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { EmojiPickerComponent } from 'app/communication/emoji/emoji-picker.component';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FileUploaderService } from 'app/foundation/service/file-uploader.service';
import { MessageEditorResources } from './message-editor-resources';
import { MessageEditorToolsComponent } from './message-editor-tools.component';

describe('Formatted message attachment and resource controls', () => {
    let fixture: ComponentFixture<MessageEditorToolsComponent>;
    let component: MessageEditorToolsComponent;
    let root: HTMLDivElement;
    let range: Range;
    const upload = vi.fn();
    const search = vi.fn();

    beforeEach(async () => {
        upload.mockReset().mockResolvedValue({ path: '/api/files/image.png' });
        search.mockReset().mockResolvedValue([{ label: 'Lecture 2 › Slides › 1', kind: 'slide', markdown: '[slide]Slides Slide 1(#7)[/slide]' }]);
        await TestBed.configureTestingModule({
            imports: [MessageEditorToolsComponent],
            providers: [
                MockProvider(TranslateService),
                MockProvider(ThemeService, { currentTheme: signal(Theme.LIGHT) }),
                { provide: FileUploaderService, useValue: { uploadMarkdownFileInCurrentConversation: upload, acceptedMarkdownFileExtensions: ['png', 'pdf'] } },
            ],
        })
            .overrideComponent(MessageEditorToolsComponent, {
                set: { providers: [{ provide: MessageEditorResources, useValue: { search } }] },
            })
            .compileComponents();
        root = document.createElement('div');
        root.textContent = 'Before after';
        document.body.append(root);
        range = document.createRange();
        range.setStart(root.firstChild!, 7);
        range.collapse(true);
        fixture = TestBed.createComponent(MessageEditorToolsComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('conversationId', 2);
        fixture.componentRef.setInput('captureSelection', () => range.cloneRange());
        fixture.detectChanges();
        await fixture.whenStable();
    });
    afterEach(() => {
        fixture.destroy();
        root.remove();
    });

    it('uploads to the current conversation and inserts at the captured caret', async () => {
        const emit = vi.spyOn(component.insert, 'emit');
        const busy = vi.spyOn(component.uploadingChange, 'emit');
        const file = new File(['image'], 'diagram.png', { type: 'image/png' });
        await component.uploadFiles([file]);
        expect(upload).toHaveBeenCalledWith(file, 1, 2);
        expect(emit).toHaveBeenCalledWith({ markdown: '![diagram.png](/api/files/image.png)', range: expect.any(Range) });
        expect(emit.mock.calls[0][0].range.startOffset).toBe(7);
        expect(busy.mock.calls.map(([value]) => value)).toEqual([true, false]);
    });

    it('inserts PDFs as links and reports failed uploads without inserting broken content', async () => {
        const emit = vi.spyOn(component.insert, 'emit');
        upload.mockResolvedValueOnce({ path: '/api/files/handbook.pdf' });
        await component.uploadFiles([new File(['pdf'], 'handbook.pdf')]);
        expect(emit.mock.calls[0][0].markdown).toBe('[handbook.pdf](/api/files/handbook.pdf)');
        upload.mockRejectedValueOnce(new Error('offline'));
        await component.uploadFiles([new File(['image'], 'missing.png')]);
        expect(emit).toHaveBeenCalledTimes(1);
        expect(component.uploading()).toBe(false);
        expect(component.error()).toBe('upload');
    });

    it('discards an upload result after moving to another conversation', async () => {
        let complete!: (value: { path: string }) => void;
        upload.mockReturnValue(
            new Promise((resolve) => {
                complete = resolve;
            }),
        );
        const emit = vi.spyOn(component.insert, 'emit');
        const pending = component.uploadFiles([new File(['x'], 'old.png')]);
        fixture.componentRef.setInput('conversationId', 3);
        fixture.detectChanges();
        await fixture.whenStable();
        complete({ path: '/api/files/old.png' });
        await pending;
        expect(emit).not.toHaveBeenCalled();
        expect(component.uploading()).toBe(false);
    });

    it('requires a conversation and a valid message-text caret before uploading', async () => {
        fixture.componentRef.setInput('conversationId', undefined);
        fixture.detectChanges();
        await component.uploadFiles([new File(['x'], 'file.png')]);
        expect(upload).not.toHaveBeenCalled();
        expect(component.error()).toBe('conversation');
        fixture.componentRef.setInput('conversationId', 2);
        fixture.componentRef.setInput('captureSelection', () => undefined);
        fixture.detectChanges();
        await component.uploadFiles([new File(['x'], 'file.png')]);
        expect(upload).not.toHaveBeenCalled();
        expect(component.error()).toBe('position');
    });

    it('inserts the selected slide at the original caret after the search field takes focus', async () => {
        const emit = vi.spyOn(component.insert, 'emit');
        (fixture.nativeElement.querySelector('[data-testid="message-slides-button"]') as HTMLButtonElement).click();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(search).toHaveBeenCalledWith('slides', '', 1);
        const option = document.querySelector('[data-testid="message-resource-option"]') as HTMLElement;
        expect(option.textContent).toContain('Lecture 2');
        option.click();
        expect(emit.mock.calls[0][0].markdown).toBe('[slide]Slides Slide 1(#7)[/slide] ');
        expect(emit.mock.calls[0][0].range.startOffset).toBe(7);
    });

    it('does not select a resource when Enter confirms an IME composition', async () => {
        const emit = vi.spyOn(component.insert, 'emit');
        (fixture.nativeElement.querySelector('[data-testid="message-slides-button"]') as HTMLButtonElement).click();
        await fixture.whenStable();
        fixture.detectChanges();
        const input = document.querySelector('[data-testid="message-resource-search"]')!;
        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', isComposing: true, bubbles: true, cancelable: true }));
        expect(emit).not.toHaveBeenCalled();
        expect(component.picker()).toBe('slides');
    });

    it('reports another pasted file during an upload instead of silently discarding it', async () => {
        let complete!: (value: { path: string }) => void;
        upload.mockReturnValue(
            new Promise((resolve) => {
                complete = resolve;
            }),
        );
        const pending = component.uploadFiles([new File(['x'], 'first.png')]);
        await component.uploadFiles([new File(['x'], 'second.png')]);
        expect(component.error()).toBe('busy');
        expect(upload).toHaveBeenCalledTimes(1);
        complete({ path: '/first.png' });
        await pending;
    });

    it('uses the existing searchable emoji picker and inserts the chosen glyph', async () => {
        const emit = vi.spyOn(component.insert, 'emit');
        (fixture.nativeElement.querySelector('[data-testid="message-emoji-button"]') as HTMLButtonElement).click();
        fixture.detectChanges();
        await fixture.whenStable();
        const picker = fixture.debugElement.query(By.directive(EmojiPickerComponent)).componentInstance as EmojiPickerComponent;
        picker.emojiSelect.emit({ emoji: { native: '🎉' } } as EmojiEvent);
        expect(emit.mock.calls[0][0].markdown).toBe('🎉');
        expect(component.picker()).toBeUndefined();
    });

    it('searches FAQs, navigates results with the keyboard, and cancels with Escape', async () => {
        const emit = vi.spyOn(component.insert, 'emit');
        const closed = vi.spyOn(component.closed, 'emit');
        search.mockResolvedValue([
            { label: 'First', kind: 'faq', markdown: '[faq]First(/courses/1/faq?faqId=1)[/faq]' },
            { label: 'Second', kind: 'faq', markdown: '[faq]Second(/courses/1/faq?faqId=2)[/faq]' },
        ]);
        (fixture.nativeElement.querySelector('[data-testid="message-faq-button"]') as HTMLButtonElement).click();
        await fixture.whenStable();
        fixture.detectChanges();
        const input = document.querySelector('[data-testid="message-resource-search"]') as HTMLInputElement;
        input.value = 'Second';
        input.dispatchEvent(new Event('input', { bubbles: true }));
        await fixture.whenStable();
        expect(search).toHaveBeenLastCalledWith('faq', 'Second', 1);
        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
        expect(emit.mock.calls[0][0].markdown).toContain('[faq]Second');
        (fixture.nativeElement.querySelector('[data-testid="message-faq-button"]') as HTMLButtonElement).click();
        await fixture.whenStable();
        fixture.detectChanges();
        document.querySelector('[data-testid="message-resource-search"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
        expect(component.picker()).toBeUndefined();
        expect(closed).toHaveBeenCalled();
    });

    it('reports resource load failures and ignores results after the picker closes', async () => {
        search.mockRejectedValueOnce(new Error('offline'));
        (fixture.nativeElement.querySelector('[data-testid="message-slides-button"]') as HTMLButtonElement).click();
        await fixture.whenStable();
        expect(component['failed']()).toBe(true);
        let complete!: (items: never[]) => void;
        search.mockReturnValue(
            new Promise((resolve) => {
                complete = resolve;
            }),
        );
        const pending = component['search']();
        component['close']();
        complete([]);
        await pending;
        expect(component.picker()).toBeUndefined();
        expect(component['results']()).toEqual([]);
    });

    it('opens the file chooser and handles a selected file, including an invalid upload response', async () => {
        const input = fixture.nativeElement.querySelector('[data-testid="message-file-input"]') as HTMLInputElement;
        const click = vi.spyOn(input, 'click').mockImplementation(() => {});
        (fixture.nativeElement.querySelector('[data-testid="message-attachment-button"]') as HTMLButtonElement).click();
        expect(click).toHaveBeenCalled();
        upload.mockResolvedValueOnce({});
        Object.defineProperty(input, 'files', { value: [new File(['x'], 'file.png')] });
        input.dispatchEvent(new Event('change'));
        await vi.waitFor(() => expect(component.error()).toBe('upload'));
        expect(input.value).toBe('');
        await component.uploadFiles([]);
        expect(upload).toHaveBeenCalledTimes(1);
    });
});
