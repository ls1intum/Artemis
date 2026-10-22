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

    it('isolates pending uploads when switching between equal-content drafts in one conversation', async () => {
        fixture.componentRef.setInput('draftIdentity', 'reply:11:new');
        fixture.detectChanges();
        let completeOld!: (value: { path: string }) => void;
        let completeNew!: (value: { path: string }) => void;
        upload.mockReturnValueOnce(new Promise((resolve) => (completeOld = resolve)));
        upload.mockReturnValueOnce(new Promise((resolve) => (completeNew = resolve)));
        const insert = vi.spyOn(component.insert, 'emit');
        const busy = vi.spyOn(component.uploadingChange, 'emit');
        const oldUpload = component.uploadFiles([new File(['old'], 'old.png')]);

        fixture.componentRef.setInput('draftIdentity', 'reply:12:new');
        fixture.detectChanges();
        expect(component.uploading()).toBe(false);
        expect(busy.mock.calls.map(([value]) => value)).toEqual([true, false]);
        const newUpload = component.uploadFiles([new File(['new'], 'new.png')]);
        completeOld({ path: '/old.png' });
        await oldUpload;
        expect(insert).not.toHaveBeenCalled();
        expect(component.uploading()).toBe(true);
        expect(component.error()).toBeUndefined();

        completeNew({ path: '/new.png' });
        await newUpload;
        expect(insert).toHaveBeenCalledExactlyOnceWith({ markdown: '![new.png](/new.png)', range: expect.any(Range) });
        expect(busy.mock.calls.map(([value]) => value)).toEqual([true, false, true, false]);
    });

    it('cancels pending uploads when source content is externally replaced', async () => {
        let complete!: (value: { path: string }) => void;
        upload.mockReturnValueOnce(new Promise((resolve) => (complete = resolve)));
        const insert = vi.spyOn(component.insert, 'emit');
        const pending = component.uploadFiles([new File(['old'], 'old.png')]);
        fixture.componentRef.setInput('editorRevision', 1);
        fixture.detectChanges();
        complete({ path: '/old.png' });
        await pending;
        expect(insert).not.toHaveBeenCalled();
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

    it('keeps slide numbers separate from long names and previews only the hovered slide', async () => {
        search.mockResolvedValue([
            {
                label: 'A very long lecture title › Slides › 3',
                resourceLabel: 'A very long lecture title › Slides',
                slideNumber: 3,
                previewUrl: '/api/core/files/slides/7',
                kind: 'slide',
                markdown: '[slide]Slides Slide 3(#7)[/slide]',
            },
        ]);
        (fixture.nativeElement.querySelector('[data-testid="message-slides-button"]') as HTMLButtonElement).click();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(document.querySelector('[data-testid="message-slide-preview"]')).toBeNull();
        const option = document.querySelector('[data-testid="message-resource-option"]') as HTMLElement;
        expect(option.querySelector('[data-testid="message-slide-number"]')?.textContent).toContain('3');
        option.dispatchEvent(new MouseEvent('mouseenter'));
        fixture.detectChanges();
        const image = document.querySelector('[data-testid="message-slide-preview"] img') as HTMLImageElement;
        expect(image.getAttribute('src')).toBe('/api/core/files/slides/7');
        expect(image.alt).toBe('A very long lecture title › Slides › 3');
        image.dispatchEvent(new Event('error'));
        fixture.detectChanges();
        expect(document.querySelector('[data-testid="message-slide-preview"] img')).toBeNull();
        expect(document.querySelector('[data-testid="message-slide-preview"]')?.textContent).toContain('artemisApp.communication.formattedEditor.previewUnavailable');
        option.dispatchEvent(new MouseEvent('mouseleave'));
        fixture.detectChanges();
        expect(document.querySelector('[data-testid="message-slide-preview"]')).toBeNull();
    });

    it('previews keyboard-selected slides and removes the preview when the picker closes', async () => {
        search.mockResolvedValue([
            { label: 'Attachment', kind: 'lecture-unit', markdown: '[lecture-unit]Slides(lecture/slides.pdf)[/lecture-unit]' },
            { label: 'Slide 2', kind: 'slide', slideNumber: 2, previewUrl: '/api/core/files/slides/8', markdown: '[slide]Slides Slide 2(#8)[/slide]' },
        ]);
        (fixture.nativeElement.querySelector('[data-testid="message-slides-button"]') as HTMLButtonElement).click();
        await fixture.whenStable();
        fixture.detectChanges();
        const input = document.querySelector('[data-testid="message-resource-search"]')!;
        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
        fixture.detectChanges();
        expect(document.querySelector('[data-testid="message-slide-preview"] img')?.getAttribute('src')).toBe('/api/core/files/slides/8');
        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
        fixture.detectChanges();
        expect(document.querySelector('[data-testid="message-slide-preview"]')).toBeNull();
    });

    it.each([
        { kind: 'slides' as const, height: 120, clientHeight: 120 },
        { kind: 'faq' as const, height: 120, clientHeight: 120 },
        { kind: 'slides' as const, height: 120.75, clientHeight: 121 },
        { kind: 'faq' as const, height: 120.75, clientHeight: 121 },
    ])('scrolls keyboard-selected $kind results in a $height px viewport through wraparound while keeping search focus', async ({ kind, height, clientHeight }) => {
        search.mockResolvedValue(
            Array.from({ length: 40 }, (_, index) => ({
                label: `Resource ${index}`,
                kind: kind === 'slides' ? 'slide' : 'faq',
                markdown: kind === 'slides' ? `[slide]Slide ${index + 1}(#${index + 1})[/slide]` : `[faq]Question ${index}(/courses/1/faq?faqId=${index + 1})[/faq]`,
            })),
        );
        (fixture.nativeElement.querySelector(`[data-testid="message-${kind === 'slides' ? 'slides' : 'faq'}-button"]`) as HTMLButtonElement).click();
        await fixture.whenStable();
        fixture.detectChanges();
        const input = document.querySelector<HTMLInputElement>('[data-testid="message-resource-search"]')!;
        const content = document.querySelector<HTMLElement>('[data-testid="message-resource-content"]')!;
        const options = Array.from(content.querySelectorAll<HTMLElement>('[role="option"]'));
        Object.defineProperty(content, 'clientHeight', { value: clientHeight });
        let scrollTop = 0;
        // Model WebKit's whole-pixel scroll offsets against fractional element dimensions.
        Object.defineProperty(content, 'scrollTop', {
            get: () => scrollTop,
            set: (value: number) => {
                scrollTop = Math.max(0, Math.floor(value));
            },
        });
        vi.spyOn(content, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 100, 200, height));
        options.forEach((option, index) => vi.spyOn(option, 'getBoundingClientRect').mockImplementation(() => new DOMRect(0, 100 + index * 32 - content.scrollTop, 200, 32)));
        const navigate = async (key: string) => {
            input.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
            fixture.detectChanges();
            await fixture.whenStable();
        };
        await navigate('ArrowUp');
        expect(options[39].getAttribute('aria-selected')).toBe('true');
        expect(content.scrollTop).toBe(1160);
        expect(input.getAttribute('aria-activedescendant')).toBe(options[39].id);
        expect(document.activeElement).toBe(input);
        await navigate('ArrowDown');
        expect(options[0].getAttribute('aria-selected')).toBe('true');
        expect(content.scrollTop).toBe(0);
        const emit = vi.spyOn(component.insert, 'emit');
        await navigate('ArrowUp');
        await navigate('Enter');
        expect(emit).toHaveBeenCalledExactlyOnceWith({
            markdown: kind === 'slides' ? '[slide]Slide 40(#40)[/slide] ' : '[faq]Question 39(/courses/1/faq?faqId=40)[/faq] ',
            range: expect.any(Range),
        });
        expect(component.picker()).toBeUndefined();
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

    it('keeps the resource picker open for Safari IME-confirmation Enter after compositionend', async () => {
        const emit = vi.spyOn(component.insert, 'emit');
        (fixture.nativeElement.querySelector('[data-testid="message-slides-button"]') as HTMLButtonElement).click();
        await fixture.whenStable();
        fixture.detectChanges();
        const input = document.querySelector('[data-testid="message-resource-search"]')!;
        input.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }));
        input.dispatchEvent(new CompositionEvent('compositionend', { bubbles: true }));
        const enter = new KeyboardEvent('keydown', { key: 'Enter', keyCode: 229, bubbles: true, cancelable: true });

        input.dispatchEvent(enter);

        expect(enter.defaultPrevented).toBe(false);
        expect(emit).not.toHaveBeenCalled();
        expect(component.picker()).toBe('slides');
        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
        expect(emit).toHaveBeenCalledOnce();
        expect(component.picker()).toBeUndefined();
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

    it('renders an upload failure outside the resource button row so long filenames can wrap below the toolbar', async () => {
        upload.mockRejectedValueOnce(new Error('unsupported file type'));
        await component.uploadFiles([new File(['video'], 'a-long-video-filename.mp4')]);
        fixture.detectChanges();
        const alert = fixture.nativeElement.querySelector('[role="alert"]');
        const button = fixture.nativeElement.querySelector('[data-testid="message-attachment-button"]');
        expect(alert).not.toBeNull();
        expect(alert.parentElement).toBe(fixture.nativeElement);
        expect(alert.contains(button)).toBe(false);
        expect(component.error()).toBe('upload');
        expect(component.uploading()).toBe(false);
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
