import { editorCodeText } from './message-editor-conversion';
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
import { OverlayRef } from '@angular/cdk/overlay';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular';

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

    function toggleToolbar() {
        const button = fixture.nativeElement.querySelector('[data-testid="message-toolbar-toggle"]') as HTMLButtonElement;
        expect(button).not.toBeNull();
        button.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
        button.click();
        fixture.detectChanges();
        return button;
    }

    it('keeps all text formats grouped and emoji available without expanding', () => {
        for (const id of ['message-format-strong', 'message-format-em', 'message-format-ins', 'message-format-s', 'message-format-code', 'message-emoji-button']) {
            expect(fixture.nativeElement.querySelector(`[data-testid="${id}"]`).closest('[hidden]')).toBeNull();
        }
    });

    it('reveals selection formatting once and respects an explicit collapse of the same selection', () => {
        const range = document.createRange();
        range.setStart(editable.querySelector('p')!.firstChild!, 0);
        range.setEnd(editable.querySelector('p')!.firstChild!, 5);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        document.dispatchEvent(new Event('selectionchange'));
        fixture.detectChanges();
        const toggle = fixture.nativeElement.querySelector('[data-testid="message-toolbar-toggle"]');
        expect(toggle.getAttribute('aria-expanded')).toBe('true');
        toggleToolbar();
        document.dispatchEvent(new Event('selectionchange'));
        fixture.detectChanges();
        expect(toggle.getAttribute('aria-expanded')).toBe('false');
    });

    it.each(['caret', 'empty', 'outside'])('collapses automatically opened controls after clearing the selection via %s', (target) => {
        const range = document.createRange();
        range.selectNodeContents(editable.querySelector('p')!);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        document.dispatchEvent(new Event('selectionchange'));
        fixture.detectChanges();
        const toggle = fixture.nativeElement.querySelector('[data-testid="message-toolbar-toggle"]');
        expect(toggle.getAttribute('aria-expanded')).toBe('true');
        const outside = document.createElement('p');
        outside.textContent = 'Other content';
        if (target === 'caret') range.collapse(false);
        else {
            window.getSelection()!.removeAllRanges();
            if (target === 'outside') {
                document.body.append(outside);
                range.selectNodeContents(outside);
                window.getSelection()!.addRange(range);
            }
        }
        document.dispatchEvent(new Event('selectionchange'));
        fixture.detectChanges();
        expect(toggle.getAttribute('aria-expanded')).toBe('false');
        expect((fixture.nativeElement.querySelector('[data-testid="message-advanced-toolbar"]') as HTMLElement).hidden).toBe(true);
        // A new selection can disclose the controls again.
        range.selectNodeContents(editable.querySelector('p')!);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        document.dispatchEvent(new Event('selectionchange'));
        fixture.detectChanges();
        expect(toggle.getAttribute('aria-expanded')).toBe('true');
        outside.remove();
    });

    it.each(['toolbar', 'picker', 'link'])('retains selection controls while focus moves to a %s input', (target) => {
        const range = document.createRange();
        range.selectNodeContents(editable.querySelector('p')!);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        document.dispatchEvent(new Event('selectionchange'));
        fixture.detectChanges();
        const toggle = fixture.nativeElement.querySelector('[data-testid="message-toolbar-toggle"]') as HTMLButtonElement;
        if (target === 'toolbar') toggle.focus();
        else if (target === 'picker') {
            fixture.debugElement.query(By.directive(MessageEditorToolsComponent)).componentInstance.picker.set('faq');
        } else {
            (fixture.nativeElement.querySelector('[data-testid="message-link-button"]') as HTMLButtonElement).click();
            fixture.detectChanges();
            (fixture.nativeElement.querySelector('[data-testid="message-link-url"]') as HTMLInputElement).focus();
        }
        window.getSelection()!.removeAllRanges();
        document.dispatchEvent(new Event('selectionchange'));
        fixture.detectChanges();
        expect(toggle.getAttribute('aria-expanded')).toBe('true');
    });

    it('keeps explicitly opened controls expanded when a selected range becomes a caret', () => {
        const toggle = toggleToolbar();
        const range = document.createRange();
        range.selectNodeContents(editable.querySelector('p')!);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        document.dispatchEvent(new Event('selectionchange'));
        range.collapse(false);
        document.dispatchEvent(new Event('selectionchange'));
        fixture.detectChanges();
        expect(toggle.getAttribute('aria-expanded')).toBe('true');
    });

    it.each(['ul', 'ol'])('continues a %s list with Shift+Enter and sends with plain Enter', (list) => {
        fixture.componentRef.setInput('content', list === 'ul' ? '- first' : '1. first');
        fixture.detectChanges();
        const text = editable.querySelector('li')!.firstChild!;
        const range = document.createRange();
        range.setStart(text, 5);
        range.collapse(true);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        const bubbled = vi.fn();
        fixture.nativeElement.addEventListener('keydown', bubbled);
        const enter = new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true, bubbles: true, cancelable: true });
        editable.dispatchEvent(enter);
        expect(enter.defaultPrevented).toBe(true);
        expect(bubbled).not.toHaveBeenCalled();
        expect(editable.querySelectorAll(`${list} > li`)).toHaveLength(2);
        editable.dispatchEvent(new InputEvent('beforeinput', { inputType: 'insertText', data: 'second', bubbles: true, cancelable: true }));
        expect(Array.from(editable.querySelectorAll('li'), (item) => item.textContent)).toEqual(['first', 'second']);
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
        expect(bubbled).toHaveBeenCalledOnce();
        fixture.nativeElement.removeEventListener('keydown', bubbled);
    });

    it('removes the list format with Backspace at the start of the first item of the first line only', () => {
        fixture.componentRef.setInput('content', '- first\n- second');
        fixture.detectChanges();
        const caretIn = (item: Element, offset: number) => {
            const range = document.createRange();
            range.setStart(item.firstChild!, offset);
            range.collapse(true);
            window.getSelection()!.removeAllRanges();
            window.getSelection()!.addRange(range);
        };
        const backspace = () => {
            const event = new KeyboardEvent('keydown', { key: 'Backspace', bubbles: true, cancelable: true });
            editable.dispatchEvent(event);
            return event;
        };

        caretIn(editable.querySelector('li')!, 2);
        expect(backspace().defaultPrevented).toBe(false);
        expect(editable.querySelectorAll('li')).toHaveLength(2);

        caretIn(editable.querySelectorAll('li')[1], 0);
        expect(backspace().defaultPrevented).toBe(false);
        expect(editable.querySelectorAll('li')).toHaveLength(2);

        caretIn(editable.querySelector('li')!, 0);
        expect(backspace().defaultPrevented).toBe(true);
        expect(editable.querySelector(':scope > p')?.textContent).toBe('first');
        expect(Array.from(editable.querySelectorAll('li'), (item) => item.textContent)).toEqual(['second']);
    });

    it.each(['-', '*', '+', '1.', '1)'])('turns a typed leading %s and space into a list before sending', (marker) => {
        fixture.componentRef.setInput('content', marker.replace(/[-*+.)]/g, '\\$&'));
        fixture.detectChanges();
        caretAtEnd();
        expect(editable.textContent).toBe(marker);
        const space = new InputEvent('beforeinput', { inputType: 'insertText', data: ' ', bubbles: true, cancelable: true });

        editable.dispatchEvent(space);

        expect(space.defaultPrevented).toBe(true);
        expect(editable.querySelector(marker.startsWith('1') ? 'ol > li' : 'ul > li')).not.toBeNull();
        expect(editable.textContent?.trim()).toBe('');
    });

    it('starts compact and exposes the advanced controls through an accessible toggle', () => {
        const advanced = fixture.nativeElement.querySelector('[data-testid="message-advanced-toolbar"]') as HTMLElement;
        expect(advanced).not.toBeNull();
        expect(advanced.hidden).toBe(true);
        for (const id of ['message-format-strong', 'message-format-em', 'message-link-button', 'message-mention-button', 'message-reference-button']) {
            expect(fixture.nativeElement.querySelector(`[data-testid="${id}"]`).closest('[hidden]')).toBeNull();
        }
        const button = toggleToolbar();
        expect(button.getAttribute('aria-controls')).toBe(advanced.id);
        expect(button.getAttribute('aria-expanded')).toBe('true');
        expect(advanced.hidden).toBe(false);
        toggleToolbar();
        expect(button.getAttribute('aria-expanded')).toBe('false');
        expect(advanced.hidden).toBe(true);
    });

    it('gives every toolbar button a visible tooltip instead of relying on the native title attribute', () => {
        toggleToolbar();
        const buttons = Array.from(fixture.nativeElement.querySelectorAll('[data-testid="message-editor-toolbar"] button')) as HTMLButtonElement[];
        const tooltips = fixture.debugElement.queryAll(By.directive(TumAetUiTooltipDirective)).map((debugElement) => debugElement.nativeElement as HTMLElement);

        expect(buttons.length).toBeGreaterThan(10);
        expect(tooltips).toEqual(expect.arrayContaining(buttons));
        buttons.forEach((button) => expect(button.hasAttribute('title')).toBe(false));
    });

    it('keeps the toolbar on one line and floats the bar of further options above the editor', () => {
        const toolbar = fixture.nativeElement.querySelector('[data-testid="message-editor-toolbar"]') as HTMLElement;
        const line = fixture.nativeElement.querySelector('[data-testid="message-toolbar-line"]') as HTMLElement;
        const group = line.querySelector('[role="group"]') as HTMLElement;
        const advanced = fixture.nativeElement.querySelector('[data-testid="message-advanced-toolbar"]') as HTMLElement;

        // A line that wraps could take a third line, so neither the line nor its group may wrap.
        expect(line.classList.contains('flex-nowrap')).toBe(true);
        expect(group.classList.contains('flex-nowrap')).toBe(true);
        expect(line.classList.contains('flex-wrap')).toBe(false);
        // The bar is out of the flow and sits on top of the toolbar, so opening it moves nothing.
        expect(advanced.classList.contains('absolute')).toBe(true);
        expect(advanced.classList.contains('bottom-full')).toBe(true);
        expect(advanced.classList.contains('top-full')).toBe(false);
        expect(toolbar.classList.contains('relative')).toBe(true);
        // Its own stacking context keeps the bar from painting over other parts of the page, such as a drawer.
        expect(advanced.closest('.message-composer')!.classList.contains('isolate')).toBe(true);
    });

    describe('with a measured toolbar line', () => {
        let measured: ComponentFixture<MessageEditorComponent>;
        let measure: (lineWidth: number) => void;
        // The buttons are 32 px wide without a gap. The test sets the width of the toolbar line, the height of the bar and the place of the toolbar.
        const layout = { lineWidth: 1000, barHeight: 60, toolbarTop: 400 };
        const is = (element: Element, testId: string) => element.matches(`[data-testid="${testId}"]`);

        beforeEach(async () => {
            const callbacks: (() => void)[] = [];
            vi.stubGlobal(
                'ResizeObserver',
                class {
                    constructor(callback: () => void) {
                        callbacks.push(callback);
                    }
                    observe() {}
                    disconnect() {}
                },
            );
            Object.assign(layout, { lineWidth: 1000, barHeight: 60, toolbarTop: 400 });
            vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockImplementation(function (this: HTMLElement) {
                return is(this, 'message-toolbar-toggle') ? 32 : 0;
            });
            vi.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockImplementation(function (this: HTMLElement) {
                return is(this, 'message-advanced-toolbar') ? layout.barHeight : 0;
            });
            vi.spyOn(Element.prototype, 'clientWidth', 'get').mockImplementation(function (this: Element) {
                return is(this, 'message-toolbar-line') ? layout.lineWidth : 0;
            });
            vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
                return { top: is(this, 'message-editor-toolbar') ? layout.toolbarTop : 0, width: 0 } as DOMRect;
            });
            measured = TestBed.createComponent(MessageEditorComponent);
            measured.componentRef.setInput('courseId', 1);
            measured.detectChanges();
            await measured.whenStable();
            measure = (width) => {
                layout.lineWidth = width;
                callbacks.forEach((callback) => callback());
                measured.detectChanges();
            };
        });

        afterEach(() => {
            measured.destroy();
            vi.restoreAllMocks();
            vi.unstubAllGlobals();
        });

        const element = (testId: string) => measured.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLElement;
        const toggleBar = () => {
            element('message-toolbar-toggle').click();
            // The place of the bar is decided after it has been rendered, and rendered again with that place.
            measured.detectChanges();
            measured.detectChanges();
        };
        const inLine = () => testIds(measured.nativeElement.querySelector('[data-testid="message-toolbar-line"] [role="group"]'));
        const inBar = () => testIds(measured.nativeElement.querySelector('[data-testid="message-overflow-controls"]'));
        const testIds = (container: HTMLElement | null) => Array.from(container?.querySelectorAll('button') ?? []).map((button) => button.getAttribute('data-testid'));
        const all = [
            'message-format-strong',
            'message-format-em',
            'message-format-ins',
            'message-format-s',
            'message-format-code',
            'message-link-button',
            'message-mention-button',
            'message-reference-button',
        ];

        it('shows every item on the line when it is wide enough, and the bar has no overflow group', () => {
            measure(1000);
            expect(inLine()).toEqual(all);
            expect(measured.nativeElement.querySelector('[data-testid="message-overflow-controls"]')).toBeNull();
        });

        it('moves the last items into the bar of further options when the line is too narrow, in order', () => {
            // Room for the emoji, the more options button and five items.
            measure(7 * 32);
            expect(inLine()).toEqual(all.slice(0, 5));
            expect(inBar()).toEqual(all.slice(5));

            measure(3 * 32);
            expect(inLine()).toEqual(all.slice(0, 1));
            expect(inBar()).toEqual(all.slice(1));

            measure(2 * 32);
            expect(inLine()).toEqual([]);
            expect(inBar()).toEqual(all);
        });

        it('keeps the overflowed items working from the bar and brings them back when the line grows', () => {
            measure(7 * 32);
            const bar = element('message-advanced-toolbar');
            expect(bar.contains(element('message-link-button'))).toBe(true);
            expect(bar.hidden).toBe(true);

            toggleBar();
            expect(bar.hidden).toBe(false);
            expect(bar.querySelector('[data-testid="message-block-ul"]')).not.toBeNull();
            expect(bar.querySelector('[data-testid="message-attachment-button"]')).not.toBeNull();

            measure(1000);
            expect(inLine()).toEqual(all);
            expect(bar.querySelector('[data-testid="message-link-button"]')).toBeNull();
        });

        it('keeps every item while the toolbar has no width, for example in a hidden tab', () => {
            measure(7 * 32);
            measure(0);
            expect(inLine()).toEqual(all);
        });

        it('shows the longer hint below the editor on a wide line and the short one on a narrow line', () => {
            const hint = () => element('message-editor-hint').textContent!.trim();
            measure(1000);
            expect(hint()).toBe('artemisApp.communication.formattedEditor.hint');
            measure(300);
            expect(hint()).toBe('artemisApp.communication.formattedEditor.hintShort');
            measure(479);
            expect(hint()).toBe('artemisApp.communication.formattedEditor.hintShort');
            measure(480);
            expect(hint()).toBe('artemisApp.communication.formattedEditor.hint');
            measure(0);
            expect(hint()).toBe('artemisApp.communication.formattedEditor.hint');
        });

        it('opens the bar above the toolbar, and below it when the ancestors would clip it there', () => {
            const bar = element('message-advanced-toolbar');
            // Plenty of room above the toolbar.
            toggleBar();
            expect(bar.classList.contains('bottom-full')).toBe(true);
            expect(bar.classList.contains('top-full')).toBe(false);
            toggleBar();

            // A 60 px bar plus its margin does not fit into the 20 px between the toolbar and the top of what the ancestors show.
            layout.toolbarTop = 20;
            toggleBar();
            expect(bar.classList.contains('top-full')).toBe(true);
            expect(bar.classList.contains('bottom-full')).toBe(false);
            toggleBar();

            // It goes back above when there is room again.
            layout.toolbarTop = 400;
            toggleBar();
            expect(bar.classList.contains('bottom-full')).toBe(true);
        });
    });

    describe('hint of the empty editor', () => {
        const placeholder = () => fixture.nativeElement.querySelector('[data-testid="message-editor-placeholder"]') as HTMLElement | null;

        async function emptyEditor(inputs: Record<string, unknown> = {}) {
            fixture.componentRef.setInput('content', '');
            for (const [name, value] of Object.entries(inputs)) fixture.componentRef.setInput(name, value);
            fixture.detectChanges();
            await fixture.whenStable();
        }

        function type(text: string) {
            editable.dispatchEvent(new InputEvent('beforeinput', { inputType: 'insertText', data: text, bubbles: true, cancelable: true }));
            editable.textContent = text;
            editable.dispatchEvent(new InputEvent('input', { inputType: 'insertText', data: text, bubbles: true }));
            fixture.detectChanges();
        }

        it('names the conversation in the light grey hint of the empty editor and leaves it out without a name', async () => {
            await emptyEditor();
            expect(placeholder()).toBeNull();
            expect(editable.hasAttribute('aria-placeholder')).toBe(false);

            await emptyEditor({ conversationName: 'Katja' });
            expect(placeholder()!.textContent).toContain('artemisApp.communication.formattedEditor.placeholder');
            expect(placeholder()!.classList.contains('message-editor-muted')).toBe(true);
        });

        it('gives screen readers the hint as the placeholder of the editor instead of as loose text', async () => {
            await emptyEditor({ conversationName: 'Katja' });
            expect(placeholder()!.getAttribute('aria-hidden')).toBe('true');
            expect(editable.getAttribute('aria-placeholder')).toContain('artemisApp.communication.formattedEditor.placeholder');

            fixture.componentRef.setInput('content', 'Hello');
            fixture.detectChanges();
            await fixture.whenStable();
            expect(editable.hasAttribute('aria-placeholder')).toBe(false);
        });

        it('words the hint for a reply in a thread, and has none while an existing message is edited', async () => {
            await emptyEditor({ conversationName: 'Katja', reply: true });
            expect(placeholder()!.textContent).toContain('artemisApp.communication.formattedEditor.replyPlaceholder');

            await emptyEditor({ conversationName: 'Katja', reply: false, editing: true });
            expect(placeholder()).toBeNull();
        });

        it('hides the hint with the first content and shows it again when the editor is emptied, also from an external reset', async () => {
            await emptyEditor({ conversationName: 'Katja' });
            expect(placeholder()).not.toBeNull();

            fixture.componentRef.setInput('content', 'Hello');
            fixture.detectChanges();
            await fixture.whenStable();
            expect(placeholder()).toBeNull();

            fixture.componentRef.setInput('content', '');
            fixture.detectChanges();
            await fixture.whenStable();
            expect(placeholder()).not.toBeNull();
        });

        it.each([' ', '\n', '  \n  '])('hides the hint for content that only looks empty: %j', async (content) => {
            await emptyEditor({ conversationName: 'Katja' });
            expect(placeholder()).not.toBeNull();

            fixture.componentRef.setInput('content', content);
            fixture.detectChanges();
            await fixture.whenStable();
            expect(placeholder()).toBeNull();
        });

        it('hides the hint while typing and while a composition is pending', async () => {
            await emptyEditor({ conversationName: 'Katja' });
            editable.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }));
            fixture.detectChanges();
            expect(placeholder()).toBeNull();
            editable.dispatchEvent(new CompositionEvent('compositionend', { bubbles: true }));
            fixture.detectChanges();
            expect(placeholder()).not.toBeNull();

            type('H');
            expect(placeholder()).toBeNull();
        });
    });

    it('keeps the full hint in the tooltip of the help button and in the description of the editor', () => {
        const hint = fixture.nativeElement.querySelector('[data-testid="message-editor-hint"]') as HTMLElement;
        const help = fixture.nativeElement.querySelector('[data-testid="message-editor-help"]') as HTMLButtonElement;

        // The visible text is one of two short sentences, and screen readers get the full text as the description of the editor instead.
        expect(hint.textContent!.trim()).toMatch(/formattedEditor\.hint(Short)?$/);
        expect(hint.getAttribute('aria-hidden')).toBe('true');
        const description = fixture.nativeElement.querySelector(`[id="${editable.getAttribute('aria-describedby')}"]`) as HTMLElement;
        expect(description.textContent).toContain('artemisApp.communication.formattedEditor.hintDetails');
        expect(help.getAttribute('aria-label')).toBe('artemisApp.communication.formattedEditor.help');
        expect(fixture.debugElement.queryAll(By.directive(TumAetUiTooltipDirective)).map((debugElement) => debugElement.nativeElement)).toContain(help);
    });

    it('keeps advanced control IDs unique for simultaneously mounted composers', () => {
        const other = TestBed.createComponent(MessageEditorComponent);
        try {
            other.componentRef.setInput('courseId', 1);
            other.detectChanges();
            const first = fixture.nativeElement.querySelector('[data-testid="message-toolbar-toggle"]') as HTMLButtonElement;
            const second = other.nativeElement.querySelector('[data-testid="message-toolbar-toggle"]') as HTMLButtonElement;
            expect(first).not.toBeNull();
            expect(second).not.toBeNull();
            expect(first.getAttribute('aria-controls')).not.toBe(second.getAttribute('aria-controls'));
        } finally {
            other.destroy();
        }
    });

    it('retains keyboard focus on the disclosure for repeated activation', () => {
        const button = fixture.nativeElement.querySelector('[data-testid="message-toolbar-toggle"]') as HTMLButtonElement;
        button.focus();
        button.click();
        fixture.detectChanges();
        expect(button.getAttribute('aria-expanded')).toBe('true');
        expect(document.activeElement).toBe(button);
        button.click();
        fixture.detectChanges();
        expect(button.getAttribute('aria-expanded')).toBe('false');
        expect(document.activeElement).toBe(button);
    });

    it('preserves a selected range through expansion and collapse before applying formatting', () => {
        editable.focus();
        const range = document.createRange();
        range.setStart(editable.querySelector('p')!.firstChild!, 1);
        range.setEnd(editable.querySelector('p')!.firstChild!, 4);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        toggleToolbar();
        expect(window.getSelection()!.toString()).toBe('ell');
        toggleToolbar();
        expect(window.getSelection()!.toString()).toBe('ell');
        (fixture.nativeElement.querySelector('[data-testid="message-format-strong"]') as HTMLButtonElement).click();
        expect(editable.querySelector('strong')!.textContent).toBe('ell');
        expect(editable.textContent).toBe('Hello');
    });

    it('collapses advanced controls on a draft switch without collapsing for normal content updates', () => {
        toggleToolbar();
        fixture.componentRef.setInput('content', 'Updated draft');
        fixture.detectChanges();
        const advanced = fixture.nativeElement.querySelector('[data-testid="message-advanced-toolbar"]') as HTMLElement;
        expect(advanced.hidden).toBe(false);
        fixture.componentRef.setInput('draftIdentity', 'reply:2:new');
        fixture.detectChanges();
        expect(advanced.hidden).toBe(true);
    });

    it('closes an advanced picker while retaining a pending upload and its insertion range', async () => {
        fixture.componentRef.setInput('conversation', { id: 22 });
        fixture.detectChanges();
        caretAtEnd();
        const tools = fixture.debugElement.query(By.directive(MessageEditorToolsComponent)).componentInstance as MessageEditorToolsComponent;
        let resolve!: (value: { path: string }) => void;
        vi.spyOn(TestBed.inject(FileUploaderService), 'uploadMarkdownFileInCurrentConversation').mockReturnValue(new Promise((done) => (resolve = done)));
        const emit = vi.spyOn(component.contentChange, 'emit');
        const upload = tools.uploadFiles([new File(['image'], 'image.png')]);
        toggleToolbar();
        tools.picker.set('faq');
        fixture.detectChanges();
        toggleToolbar();
        expect(tools.picker()).toBeUndefined();
        expect(fixture.debugElement.query(By.directive(MessageEditorToolsComponent)).componentInstance).toBe(tools);
        expect(tools.uploading()).toBe(true);
        expect(component['uploading']()).toBe(true);
        resolve({ path: '/api/core/files/image.png' });
        await upload;
        fixture.detectChanges();
        expect(emit).toHaveBeenLastCalledWith('Hello![image.png](/api/core/files/image.png)');
        expect(tools.uploading()).toBe(false);
        expect(component['uploading']()).toBe(false);
    });

    it.each(['paste', 'drop'])('keeps upload progress and failure feedback visible for a collapsed %s upload', async (eventType) => {
        fixture.componentRef.setInput('conversation', { id: 22 });
        fixture.detectChanges();
        editable.focus();
        caretAtEnd();
        let reject!: (error: Error) => void;
        const pending = new Promise<{ path: string }>((_resolve, fail) => (reject = fail));
        vi.spyOn(TestBed.inject(FileUploaderService), 'uploadMarkdownFileInCurrentConversation').mockReturnValue(pending);
        const event = new Event(eventType, { bubbles: true, cancelable: true });
        Object.defineProperty(event, eventType === 'paste' ? 'clipboardData' : 'dataTransfer', { value: { files: [new File(['image'], 'missing.png')] } });
        editable.dispatchEvent(event);
        fixture.detectChanges();
        const status = fixture.nativeElement.querySelector('[role="status"]') as HTMLElement;
        expect(status).not.toBeNull();
        expect(status.closest('[hidden]')).toBeNull();
        expect(component['uploading']()).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="message-toolbar-toggle"]').getAttribute('aria-expanded')).toBe('false');
        expect(fixture.nativeElement.querySelector('[data-testid="message-attachment-button"]').closest('[hidden]')).not.toBeNull();
        reject(new Error('offline'));
        await pending.catch(() => undefined);
        fixture.detectChanges();
        const alert = fixture.nativeElement.querySelector('[role="alert"]') as HTMLElement;
        expect(alert).not.toBeNull();
        expect(alert.closest('[hidden]')).toBeNull();
        expect(alert.textContent).toContain('artemisApp.communication.formattedEditor.errors.upload');
        expect(fixture.nativeElement.querySelector('[role="status"]')).toBeNull();
        expect(component['uploading']()).toBe(false);
        expect(editable.textContent).toBe('Hello');
    });

    it('closes the link popup when the link toolbar button is clicked again', () => {
        caretAtEnd();
        const button = fixture.nativeElement.querySelector('[data-testid="message-link-button"]') as HTMLButtonElement;
        button.click();
        fixture.detectChanges();
        expect(component.linkOpen()).toBe(true);
        button.click();
        fixture.detectChanges();
        expect(component.linkOpen()).toBe(false);
        expect(document.activeElement).toBe(editable);
    });

    it('dismisses an existing link form before a formatting rebuild can detach its target', () => {
        fixture.componentRef.setInput('content', 'Read [guide](https://example.org) next');
        fixture.detectChanges();
        const link = editable.querySelector('a')!;
        const range = document.createRange();
        range.setStart(link.firstChild!, 2);
        range.collapse(true);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        (fixture.nativeElement.querySelector('[data-testid="message-link-button"]') as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(component.linkOpen()).toBe(true);
        (fixture.nativeElement.querySelector('[data-testid="message-block-blockquote"]') as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(component.linkOpen()).toBe(false);
        expect(editable.querySelectorAll('a')).toHaveLength(1);
        expect(editable.querySelector('a')!.textContent).toBe('guide');
        expect(editable.querySelector('blockquote a')!.getAttribute('href')).toBe('https://example.org');
    });

    it.each(['draftIdentity', 'conversation'])('resets undo history on an equal-content %s switch', (identity) => {
        fixture.componentRef.setInput('draftIdentity', 'reply:1:new');
        fixture.detectChanges();
        editable.querySelector('p')!.textContent = 'Hello!';
        caretAtEnd();
        editable.dispatchEvent(new InputEvent('input', { bubbles: true }));
        editable.querySelector('p')!.textContent = 'Hello';
        caretAtEnd();
        editable.dispatchEvent(new InputEvent('input', { bubbles: true }));
        fixture.componentRef.setInput(identity, identity === 'draftIdentity' ? 'reply:2:new' : { id: 22 });
        fixture.detectChanges();
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true, cancelable: true }));
        expect(editable.textContent).toBe('Hello');
        expect(component.linkOpen()).toBe(false);
    });

    it('preloads an existing link when opened from its keyboard caret and updates only its target', () => {
        fixture.componentRef.setInput('content', 'Read [guide](https://example.org/old) next');
        fixture.detectChanges();
        const link = editable.querySelector('a')!;
        const range = document.createRange();
        range.setStart(link.firstChild!, 2);
        range.collapse(true);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, bubbles: true, cancelable: true }));
        fixture.detectChanges();
        expect(component.linkOpen()).toBe(true);
        expect(component.linkUrl()).toBe('https://example.org/old');
        expect(component.linkText()).toBe('guide');
        component.linkUrl.set('https://example.org/new');
        (fixture.nativeElement.querySelector('[data-testid="apply-message-link"]') as HTMLButtonElement).click();
        expect(editable.querySelectorAll('a')).toHaveLength(1);
        expect(editable.querySelector('a')!.getAttribute('href')).toBe('https://example.org/new');
    });

    it('edits and unlinks an existing code-formatted link from its keyboard caret', () => {
        fixture.componentRef.setInput('content', 'Read [`List`](https://example.org/old) next');
        fixture.detectChanges();
        const range = document.createRange();
        range.setStart(editable.querySelector('a code')!.firstChild!, 2);
        range.collapse(true);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        editable.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
        fixture.detectChanges();
        const button = fixture.nativeElement.querySelector('[data-testid="message-link-button"]') as HTMLButtonElement;
        expect(button.disabled).toBe(false);
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, bubbles: true, cancelable: true }));
        fixture.detectChanges();
        expect(component.linkOpen()).toBe(true);
        expect(component.linkText()).toBe('List');
        expect(component.linkUrl()).toBe('https://example.org/old');
        component.linkUrl.set('https://example.org/new');
        (fixture.nativeElement.querySelector('[data-testid="apply-message-link"]') as HTMLButtonElement).click();
        const link = editable.querySelector('a')!;
        expect(link.getAttribute('href')).toBe('https://example.org/new');
        expect(link.querySelector('code')!.textContent).toBe('List');
        link.click();
        fixture.detectChanges();
        (fixture.nativeElement.querySelector('[data-testid="remove-message-link"]') as HTMLButtonElement).click();
        expect(editable.querySelector('a')).toBeNull();
        expect(editable.querySelector('code')!.textContent).toBe('List');
    });

    it('dismisses the link form when a click moves the caret to another word', () => {
        caretAtEnd();
        (fixture.nativeElement.querySelector('[data-testid="message-link-button"]') as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(component.linkOpen()).toBe(true);
        const range = document.createRange();
        range.setStart(editable.querySelector('p')!.firstChild!, 1);
        range.collapse(true);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        editable.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
        expect(component.linkOpen()).toBe(false);
    });

    it('retains the link form while its inputs are focused but dismisses it after keyboard caret navigation', () => {
        editable.focus();
        caretAtEnd();
        (fixture.nativeElement.querySelector('[data-testid="message-link-button"]') as HTMLButtonElement).click();
        fixture.detectChanges();
        const input = fixture.nativeElement.querySelector('[data-testid="message-link-url"]') as HTMLInputElement;
        input.focus();
        document.dispatchEvent(new Event('selectionchange'));
        expect(component.linkOpen()).toBe(true);
        input.value = 'https://example.org';
        input.dispatchEvent(new InputEvent('input', { bubbles: true }));
        expect(component.linkUrl()).toBe('https://example.org');
        editable.focus();
        const range = document.createRange();
        range.setStart(editable.querySelector('p')!.firstChild!, 2);
        range.collapse(true);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        document.dispatchEvent(new Event('selectionchange'));
        expect(component.linkOpen()).toBe(false);
    });

    it('disables the link action inside code and ignores its keyboard shortcut', () => {
        fixture.componentRef.setInput('content', '`literal`');
        fixture.detectChanges();
        const range = document.createRange();
        range.setStart(editable.querySelector('code')!.firstChild!, 3);
        range.collapse(true);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        editable.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
        fixture.detectChanges();
        expect((fixture.nativeElement.querySelector('[data-testid="message-link-button"]') as HTMLButtonElement).disabled).toBe(true);
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, bubbles: true, cancelable: true }));
        expect(component.linkOpen()).toBe(false);
        expect(editable.querySelector('a')).toBeNull();
    });

    it('disables links across paragraphs through the toolbar and shortcut without changing the document', () => {
        fixture.componentRef.setInput('content', 'First paragraph\n\nSecond paragraph');
        fixture.detectChanges();
        const paragraphs = editable.querySelectorAll('p');
        const range = document.createRange();
        range.setStart(paragraphs[0].firstChild!, 2);
        range.setEnd(paragraphs[1].firstChild!, 3);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        editable.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
        fixture.detectChanges();
        const html = editable.innerHTML;
        expect((fixture.nativeElement.querySelector('[data-testid="message-link-button"]') as HTMLButtonElement).disabled).toBe(true);
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, bubbles: true, cancelable: true }));
        expect(component.linkOpen()).toBe(false);
        expect(editable.innerHTML).toBe(html);
    });

    it('exposes active and pending formatting through toolbar pressed state', () => {
        caretAtEnd();
        const bold = fixture.nativeElement.querySelector('[data-testid="message-format-strong"]') as HTMLButtonElement;
        expect(bold.getAttribute('aria-pressed')).toBe('false');
        bold.click();
        fixture.detectChanges();
        expect(bold.getAttribute('aria-pressed')).toBe('true');
        bold.click();
        fixture.detectChanges();
        expect(bold.getAttribute('aria-pressed')).toBe('false');
        expect(editable.textContent).toBe('Hello');
    });

    it('uses the caret line geometry as the completion origin', async () => {
        fixture.componentRef.setInput('content', 'Hello @ann');
        fixture.detectChanges();
        caretAtEnd();
        const original = Object.getOwnPropertyDescriptor(Range.prototype, 'getBoundingClientRect');
        Object.defineProperty(Range.prototype, 'getBoundingClientRect', { configurable: true, value: () => ({ left: 170, top: 210, width: 0, height: 24 }) });
        try {
            await component.refreshSuggestions();
            expect(component['completionOrigin']()).toEqual({ x: 170, y: 210, width: 0, height: 24 });
            expect(component.suggestionsOpen()).toBe(true);
        } finally {
            if (original) Object.defineProperty(Range.prototype, 'getBoundingClientRect', original);
            else Reflect.deleteProperty(Range.prototype, 'getBoundingClientRect');
        }
    });

    it.each([
        ['b', 'strong'],
        ['i', 'em'],
        ['u', 'ins'],
    ])('immediately reflects the keyboard %s format toggle before text is typed', (key, tag) => {
        caretAtEnd();
        const button = fixture.nativeElement.querySelector(`[data-testid="message-format-${tag}"]`) as HTMLButtonElement;
        editable.dispatchEvent(new KeyboardEvent('keydown', { key, ctrlKey: true, bubbles: true, cancelable: true }));
        fixture.detectChanges();
        expect(button.getAttribute('aria-pressed')).toBe('true');
        expect(editable.querySelector(tag)).toBeNull();
        editable.dispatchEvent(new KeyboardEvent('keydown', { key, ctrlKey: true, bubbles: true, cancelable: true }));
        fixture.detectChanges();
        expect(button.getAttribute('aria-pressed')).toBe('false');
        expect(editable.textContent).toBe('Hello');
    });

    it('keeps suggestions open for popup scrolling but dismisses them when an ancestor scrolls', async () => {
        fixture.componentRef.setInput('content', '@ann');
        fixture.detectChanges();
        caretAtEnd();
        await component.refreshSuggestions();
        fixture.detectChanges();
        const popup = document.querySelector('[data-testid="message-completion-list"]')!;
        expect(popup).not.toBeNull();
        popup.dispatchEvent(new Event('scroll'));
        expect(component.suggestionsOpen()).toBe(true);
        fixture.nativeElement.parentElement.dispatchEvent(new Event('scroll'));
        expect(component.suggestionsOpen()).toBe(false);
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

    function pasteContent(html: string, text: string): ClipboardEvent {
        const event = new Event('paste', { bubbles: true, cancelable: true }) as ClipboardEvent;
        Object.defineProperty(event, 'clipboardData', { value: { files: [], getData: (type: string) => (type === 'text/html' ? html : text) } });
        editable.dispatchEvent(event);
        return event;
    }

    it.each(['copy', 'cut'])('preserves mentions and image attachments through internal %s, paste, and undo', (operation) => {
        const source = '**Read** [user]Anna(anna)[/user] ![image.png](/api/core/files/image.png)';
        fixture.componentRef.setInput('content', source);
        fixture.detectChanges();
        const range = document.createRange();
        range.selectNodeContents(editable);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        const values = new Map<string, string>();
        const clipboardData = { files: [], setData: (type: string, value: string) => values.set(type, value), getData: (type: string) => values.get(type) ?? '' };
        const event = new Event(operation, { bubbles: true, cancelable: true });
        Object.defineProperty(event, 'clipboardData', { value: clipboardData });
        const emit = vi.spyOn(component.contentChange, 'emit');
        editable.dispatchEvent(event);
        expect(event.defaultPrevented).toBe(true);
        if (operation === 'cut') {
            expect(emit).toHaveBeenLastCalledWith('');
            editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true, cancelable: true }));
            expect(emit).toHaveBeenLastCalledWith(source);
        } else {
            expect(emit).not.toHaveBeenCalled();
        }
        fixture.componentRef.setInput('content', '');
        fixture.componentRef.setInput('draftIdentity', 'copy-target');
        fixture.detectChanges();
        const end = document.createRange();
        end.selectNodeContents(editable);
        end.collapse(false);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(end);
        const paste = new Event('paste', { bubbles: true, cancelable: true });
        Object.defineProperty(paste, 'clipboardData', { value: clipboardData });
        editable.dispatchEvent(paste);
        expect(emit).toHaveBeenLastCalledWith(source);
        expect(editable.querySelector('[data-message-reference="[user]Anna(anna)[/user]"]')).not.toBeNull();
        expect(editable.querySelector('img')?.getAttribute('src')).toBe('/api/core/files/image.png');
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true, cancelable: true }));
        expect(emit).toHaveBeenLastCalledWith('');
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'y', ctrlKey: true, bubbles: true, cancelable: true }));
        expect(emit).toHaveBeenLastCalledWith(source);
    });

    it('preserves pasted inline formatting and links as one undoable replacement', () => {
        const range = document.createRange();
        range.selectNodeContents(editable.querySelector('p')!);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        const emit = vi.spyOn(component.contentChange, 'emit');
        const html = '<b>Bold</b> <i>italic</i> <u>under</u> <s>strike</s> <code>code</code> <a href="https://example.org">guide</a>';

        expect(pasteContent(html, 'Bold italic under strike code guide').defaultPrevented).toBe(true);

        expect(editable.querySelector('strong')?.textContent).toBe('Bold');
        expect(editable.querySelector('em')?.textContent).toBe('italic');
        expect(editable.querySelector('ins')?.textContent).toBe('under');
        expect(editable.querySelector('s')?.textContent).toBe('strike');
        expect(editable.querySelector('code')?.textContent).toBe('code');
        expect(editable.querySelector('a')?.getAttribute('href')).toBe('https://example.org');
        expect(emit).toHaveBeenLastCalledWith('**Bold** *italic* <ins>under</ins> ~~strike~~ `code` [guide](https://example.org)');
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true, cancelable: true }));
        expect(editable.textContent).toBe('Hello');
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, shiftKey: true, bubbles: true, cancelable: true }));
        expect(editable.querySelector('strong')?.textContent).toBe('Bold');
    });

    it('retains document-style inline marks without importing source styling or fake references', () => {
        caretAtEnd();
        pasteContent(
            '<b style="font-weight:normal"><span style="font-weight:700;font-style:italic;text-decoration:underline line-through;color:red" data-message-reference="[user]Other(other)[/user]" contenteditable="false"> marked</span></b>',
            ' marked',
        );
        for (const mark of ['strong', 'em', 'ins', 's']) expect(editable.querySelector(mark)?.textContent?.trim()).toBe('marked');
        expect(editable.querySelector('[style], [contenteditable="false"], [data-message-reference]')).toBeNull();
    });

    it('pastes blocks between surrounding text without nested paragraphs and preserves the suffix caret', () => {
        const range = document.createRange();
        range.setStart(editable.querySelector('p')!.firstChild!, 2);
        range.collapse(true);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        const emit = vi.spyOn(component.contentChange, 'emit');

        pasteContent('<p><b>first</b></p><ul><li>item</li></ul><blockquote>quote</blockquote><pre><code>a\nb</code></pre><p>last</p>', 'first\nitem\nquote\na\nb\nlast');

        expect(editable.querySelector('p p, p ul, p blockquote, p pre')).toBeNull();
        expect(Array.from(editable.children, (child) => child.tagName)).toEqual(['P', 'UL', 'BLOCKQUOTE', 'PRE', 'P']);
        expect(editable.firstElementChild?.textContent).toBe('Hefirst');
        expect(editable.lastElementChild?.textContent).toBe('lastllo');
        expect(editorCodeText(editable.querySelector('pre code')!)).toBe('a\nb');
        pasteContent('', '!');
        expect(emit.mock.lastCall?.[0]).toContain('last!llo');
    });

    it.each(['- item', '> item', '```\nitem\n```'])('replaces a selection spanning a paragraph and another block without losing undo: %s', (lastBlock) => {
        fixture.componentRef.setInput('content', 'Hello\n\n' + lastBlock);
        fixture.detectChanges();
        const start = editable.querySelector('p')!.firstChild!;
        const end = editable.querySelector('li, blockquote p, pre code')!.firstChild!;
        const range = document.createRange();
        range.setStart(start, 2);
        range.setEnd(end, 2);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        const original = editable.textContent;
        const emit = vi.spyOn(component.contentChange, 'emit');

        pasteContent('<p><b>first</b></p><p>last</p>', 'first\nlast');

        expect(editable.querySelector('strong')?.textContent).toBe('first');
        expect(editable.firstElementChild?.textContent).toBe('Hefirst');
        expect(editable.textContent).toContain('last');
        expect(emit).toHaveBeenCalledTimes(1);
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true, cancelable: true }));
        expect(editable.textContent).toBe(original);
    });

    it.each(['<table><tr><td>cell</td></tr></table>', '', '<script>alert(1)</script>'])('uses literal clipboard text when HTML is unavailable or unsupported: %s', (html) => {
        caretAtEnd();
        const emit = vi.spyOn(component.contentChange, 'emit');
        pasteContent(html, ' **literal** <img>');
        expect(editable.textContent).toBe('Hello **literal** <img>');
        expect(editable.querySelector('strong, img, script, table')).toBeNull();
        expect(emit.mock.lastCall?.[0]).toContain('\\*\\*literal\\*\\*');
    });

    it.each(['`Hello`', '```\nHello\n```'])('keeps rich paste literal inside code: %s', (message) => {
        fixture.componentRef.setInput('content', message);
        fixture.detectChanges();
        const code = editable.querySelector('code')!;
        const range = document.createRange();
        range.selectNodeContents(code);
        range.collapse(false);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);

        pasteContent('<b> world</b>', ' **world**');

        expect(code.textContent).toBe('Hello **world**');
        expect(code.querySelector('strong')).toBeNull();
    });

    it('keeps clipboard boundary spaces so formatted paste does not join surrounding words', () => {
        fixture.componentRef.setInput('content', 'Hellotail');
        fixture.detectChanges();
        const range = document.createRange();
        range.setStart(editable.querySelector('p')!.firstChild!, 5);
        range.collapse(true);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        const emit = vi.spyOn(component.contentChange, 'emit');

        pasteContent('<b> world </b>', ' world ');

        expect(editable.textContent).toBe('Hello world tail');
        expect(emit).toHaveBeenLastCalledWith('Hello **world** tail');
    });

    it('splits surrounding marks and links so pasted formatting does not inherit or nest them', () => {
        fixture.componentRef.setInput('content', '[**Hello**](https://old.example)');
        fixture.detectChanges();
        const text = editable.querySelector('strong')!.firstChild!;
        const range = document.createRange();
        range.setStart(text, 2);
        range.collapse(true);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);

        pasteContent('<i>new</i> <a href="https://new.example">link</a>', 'new link');

        expect(editable.querySelector('a a, strong em, strong a')).toBeNull();
        expect(editable.querySelector('em')?.textContent).toBe('new');
        expect(Array.from(editable.querySelectorAll('a'), (link) => [link.getAttribute('href'), link.textContent])).toEqual([
            ['https://old.example', 'He'],
            ['https://new.example', 'link'],
            ['https://old.example', 'llo'],
        ]);
    });

    it('normalizes clipboard wrappers before subsequent pastes into an imported link', () => {
        fixture.componentRef.setInput('content', '');
        fixture.detectChanges();
        pasteContent('<a href="https://old.example"><span>Hello</span></a>', 'Hello');
        const range = document.createRange();
        range.setStart(editable.querySelector('a')!.firstChild!, 2);
        range.collapse(true);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        pasteContent('<a href="https://new.example">new</a>', 'new');
        expect(editable.querySelector('a a')).toBeNull();
        expect(Array.from(editable.querySelectorAll('a'), (link) => link.textContent)).toEqual(['He', 'new', 'llo']);
    });

    it.each(['before<p>middle</p>after', 'before<div>middle</div>after', '<span><p>before</p><p>middle</p><p>after</p></span>'])(
        'preserves every block and inline sibling from clipboard HTML: %s',
        (html) => {
            fixture.componentRef.setInput('content', '');
            fixture.detectChanges();
            pasteContent(html, 'before\nmiddle\nafter');
            expect(editable.textContent).toBe('beforemiddleafter');
            expect(Array.from(editable.children, (child) => child.tagName)).toEqual(['P', 'P', 'P']);
            expect(editable.querySelector('p p')).toBeNull();
        },
    );

    it('drops active clipboard content and unsafe links while retaining safe text and links', () => {
        caretAtEnd();
        pasteContent(
            '<script>alert(1)</script><img src="https://tracker.invalid/pixel" onerror="alert(1)"><a href="javascript:alert(1)" onclick="alert(1)">bad</a> <a href="example.org/path">good</a> <a href="/posts/1">#123</a>',
            'bad good #123',
        );
        expect(editable.querySelector('script, img, [onclick], [onerror], [style]')).toBeNull();
        expect(editable.querySelectorAll('a')).toHaveLength(1);
        expect(editable.querySelector('a')?.getAttribute('href')).toBe('https://example.org/path');
        expect(editable.textContent).toContain('bad good #123');
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

    it.each(['漢字', 'かな', '🙂'])('applies pending bold to committed composition %s and preserves undo', (text) => {
        caretAtEnd();
        (fixture.nativeElement.querySelector('[data-testid="message-format-strong"]') as HTMLButtonElement).click();
        const emit = vi.spyOn(component.contentChange, 'emit');
        editable.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }));
        const original = editable.querySelector('p')!.firstChild!;
        original.textContent += text;
        caretAtEnd();
        editable.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertCompositionText', data: text, isComposing: true }));
        expect(emit).not.toHaveBeenCalled();
        editable.dispatchEvent(new CompositionEvent('compositionend', { bubbles: true, data: text }));
        expect(editable.querySelector('strong')?.textContent).toBe(text);
        expect(editable.textContent).toBe(`Hello${text}`);
        expect(emit).toHaveBeenLastCalledWith(`Hello**${text}**`);
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true, cancelable: true }));
        expect(editable.textContent).toBe('Hello');
        expect(editable.querySelector('strong')).toBeNull();
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

    it('still schedules mention completion while typing with a pending format', async () => {
        caretAtEnd();
        (fixture.nativeElement.querySelector('[data-testid="message-format-strong"]') as HTMLButtonElement).click();
        vi.useFakeTimers();
        for (const character of ' @ann') {
            const input = new InputEvent('beforeinput', { inputType: 'insertText', data: character, bubbles: true, cancelable: true });
            editable.dispatchEvent(input);
            expect(input.defaultPrevented).toBe(true);
        }
        expect(editable.querySelector('strong')?.textContent).toBe(' @ann');
        await vi.advanceTimersByTimeAsync(150);
        expect(search).toHaveBeenCalledWith('@', 'ann', 1, undefined, false);
        expect(component.suggestionsOpen()).toBe(true);
        expect(component.suggestions()[0].label).toBe('@Anna');
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

    it('keeps the keyboard-selected completion inside the popup without moving editor focus', async () => {
        search.mockResolvedValue(Array.from({ length: 12 }, (_, index) => ({ label: `@Person ${index}`, kind: 'user', markdown: `[user]Person ${index}(person${index})[/user]` })));
        fixture.componentRef.setInput('content', '@person');
        fixture.detectChanges();
        await fixture.whenStable();
        editable.focus();
        caretAtEnd();
        await component.refreshSuggestions();
        fixture.detectChanges();
        await fixture.whenStable();
        const popup = document.querySelector<HTMLElement>('[data-testid="message-completion-list"]')!;
        Object.defineProperty(popup, 'clientHeight', { value: 120 });
        vi.spyOn(popup, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 100, 200, 120));
        const options = Array.from(popup.querySelectorAll<HTMLElement>('[role="option"]'));
        options.forEach((option, index) => vi.spyOn(option, 'getBoundingClientRect').mockImplementation(() => new DOMRect(0, 124 + index * 32 - popup.scrollTop, 200, 32)));
        const navigate = async (key: string) => {
            editable.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
            fixture.detectChanges();
            await fixture.whenStable();
        };
        await navigate('ArrowUp');
        expect(options[11].getAttribute('aria-selected')).toBe('true');
        expect(popup.scrollTop).toBe(288);
        expect(editable.getAttribute('aria-activedescendant')).toBe(options[11].id);
        expect(document.activeElement).toBe(editable);
        await navigate('ArrowDown');
        expect(options[0].getAttribute('aria-selected')).toBe('true');
        expect(popup.scrollTop).toBe(24);
        expect(document.activeElement).toBe(editable);
        await navigate('Enter');
        expect(editable.querySelector('[data-message-reference]')?.textContent).toBe('@Person 0');
        expect(component.suggestionsOpen()).toBe(false);
    });

    it('refreshes course-reference completions on every insertion and deletion after the old overlay detaches', async () => {
        vi.useFakeTimers();
        search.mockResolvedValue([{ label: '#announcements', kind: 'channel', markdown: '[channel]announcements(7)[/channel]' }]);
        fixture.componentRef.setInput('content', '\\#');
        fixture.detectChanges();
        caretAtEnd();
        await component.refreshSuggestions();
        fixture.detectChanges();
        expect(component.suggestionsOpen()).toBe(true);
        let previous = '';

        for (const query of ['a', 'an', 'ann', 'anno', 'ann', 'an', 'a', '']) {
            const deleting = query.length < previous.length;
            editable.dispatchEvent(new KeyboardEvent('keydown', { key: deleting ? 'Backspace' : query.at(-1), bubbles: true, cancelable: true }));
            editable.dispatchEvent(
                new InputEvent('beforeinput', {
                    inputType: deleting ? 'deleteContentBackward' : 'insertText',
                    data: deleting ? null : query.at(-1),
                    bubbles: true,
                    cancelable: true,
                }),
            );
            editable.querySelector('p')!.textContent = '#' + query;
            caretAtEnd();
            editable.dispatchEvent(new InputEvent('input', { inputType: deleting ? 'deleteContentBackward' : 'insertText', bubbles: true }));
            // Render the closed old overlay before the debounce resolves, matching native typing in the application.
            fixture.detectChanges();
            await vi.advanceTimersByTimeAsync(151);
            fixture.detectChanges();

            expect(search).toHaveBeenLastCalledWith('#', query, 1, undefined, false);
            expect(component.suggestionsOpen()).toBe(true);
            expect(document.querySelector('[data-testid="message-completion-list"] [role="option"]')?.textContent).toContain('#announcements');
            previous = query;
        }
    });

    it('dismisses the popup when the overlay is detached externally while open', async () => {
        fixture.componentRef.setInput('content', '@ann');
        fixture.detectChanges();
        caretAtEnd();
        await component.refreshSuggestions();
        fixture.detectChanges();
        const overlay = component['completionOverlay']()!;
        expect(component.suggestionsOpen()).toBe(true);

        overlay.overlayRef.detach();

        expect(component.suggestionsOpen()).toBe(false);
        expect(component.suggestions()).toEqual([]);
    });

    it('repositions the completion overlay after loading changes its rendered size', async () => {
        let resolve!: (value: { label: string; kind: 'user'; markdown: string }[]) => void;
        search.mockReturnValue(new Promise((complete) => (resolve = complete)));
        fixture.componentRef.setInput('content', '@ann');
        fixture.detectChanges();
        await fixture.whenStable();
        caretAtEnd();
        const pending = component.refreshSuggestions();
        fixture.detectChanges();
        await fixture.whenStable();
        const reposition = vi.spyOn(OverlayRef.prototype, 'updatePosition');
        expect(component.loading()).toBe(true);
        expect(document.querySelectorAll('[role="option"]')).toHaveLength(0);
        resolve([{ label: '@Anna', kind: 'user', markdown: '[user]Anna(anna)[/user]' }]);
        await pending;
        fixture.detectChanges();
        await fixture.whenStable();
        expect(document.querySelectorAll('[role="option"]')).toHaveLength(1);
        expect(component.loading()).toBe(false);
        expect(reposition).toHaveBeenCalled();
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

    it.each(['empty', 'failed'] as const)('lets Enter reach send when completion search is %s', async (outcome) => {
        if (outcome === 'empty') {
            search.mockResolvedValue([]);
        } else {
            search.mockRejectedValue(new Error('offline'));
        }
        fixture.componentRef.setInput('content', 'ping @none');
        fixture.detectChanges();
        caretAtEnd();
        await component.refreshSuggestions();
        expect(component.suggestionsOpen()).toBe(true);
        expect(component.loading()).toBe(false);
        const bubbled = vi.fn();
        fixture.nativeElement.addEventListener('keydown', bubbled);
        const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });

        editable.dispatchEvent(enter);

        // Normal sending suppresses a native newline while the parent handles the bubbled Enter.
        expect(enter.defaultPrevented).toBe(true);
        expect(bubbled).toHaveBeenCalledOnce();
        expect(component.suggestionsOpen()).toBe(false);
        expect(editable.textContent).toBe('ping @none');
        fixture.nativeElement.removeEventListener('keydown', bubbled);
    });

    it('blocks Enter while completion is loading, then permits sending after it finishes empty', async () => {
        let finish!: (result: []) => void;
        search.mockReturnValue(new Promise<[]>((resolve) => (finish = resolve)));
        fixture.componentRef.setInput('content', 'ping @none');
        fixture.detectChanges();
        caretAtEnd();
        const pending = component.refreshSuggestions();
        const bubbled = vi.fn();
        fixture.nativeElement.addEventListener('keydown', bubbled);
        const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
        editable.dispatchEvent(enter);
        expect(component.loading()).toBe(true);
        expect(enter.defaultPrevented).toBe(true);
        expect(bubbled).not.toHaveBeenCalled();
        expect(component.suggestionsOpen()).toBe(true);
        finish([]);
        await pending;

        const send = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
        editable.dispatchEvent(send);

        expect(send.defaultPrevented).toBe(true);
        expect(bubbled).toHaveBeenCalledOnce();
        expect(component.suggestionsOpen()).toBe(false);
        fixture.nativeElement.removeEventListener('keydown', bubbled);
    });

    it.each(['populated', 'loading'] as const)('inserts a newline without accepting or sending when suggestions are %s', async (state) => {
        let finish!: (result: []) => void;
        if (state === 'loading') {
            search.mockReturnValue(new Promise<[]>((resolve) => (finish = resolve)));
        }
        fixture.componentRef.setInput('content', 'ping @ann');
        fixture.detectChanges();
        caretAtEnd();
        const pending = component.refreshSuggestions();
        if (state === 'populated') {
            await pending;
        }
        const bubbled = vi.fn();
        const emit = vi.spyOn(component.contentChange, 'emit');
        fixture.nativeElement.addEventListener('keydown', bubbled);
        const enter = new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true, bubbles: true, cancelable: true });

        editable.dispatchEvent(enter);
        editable.dispatchEvent(new InputEvent('beforeinput', { inputType: 'insertText', data: 'next', bubbles: true, cancelable: true }));
        if (state === 'loading') {
            finish([]);
            await pending;
        }

        expect(enter.defaultPrevented).toBe(true);
        expect(bubbled).not.toHaveBeenCalled();
        expect(component.suggestionsOpen()).toBe(false);
        expect(emit).toHaveBeenLastCalledWith('ping @ann\nnext');
        expect(editable.querySelector('[data-message-reference]')).toBeNull();
        fixture.nativeElement.removeEventListener('keydown', bubbled);
    });

    it.each(['code', 'quote'])('lets ArrowDown leave a final %s block without removing its formatting', (kind) => {
        fixture.componentRef.setInput('content', kind === 'code' ? '```\ninside\n```' : '> inside');
        fixture.detectChanges();
        const block = editable.querySelector(kind === 'code' ? 'pre' : 'blockquote')!;
        const range = document.createRange();
        range.selectNodeContents(block);
        range.collapse(false);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        const key = new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true });

        editable.dispatchEvent(key);

        expect(key.defaultPrevented).toBe(true);
        expect(editable.querySelector(':scope > p')).not.toBeNull();
        expect(block.textContent).toBe('inside');
    });

    it.each([
        ['example.org/path', 'https://example.org/path'],
        ['example.org:8443/path', 'https://example.org:8443/path'],
        ['/courses/1', '/courses/1'],
        ['./guide.pdf', './guide.pdf'],
        ['#section', '#section'],
    ])('normalizes a link destination %s to %s', (input, expected) => {
        caretAtEnd();
        component['openLink']();
        component.linkText.set('Guide');
        component.linkUrl.set(input);

        component['saveLink']();

        expect(editable.querySelector('a')?.getAttribute('href')).toBe(expected);
    });

    it.each([
        ['Read #123', 'https://example.org/guide'],
        ['Read guide', 'https://example.org/guide#123'],
    ])('keeps the link form open with feedback for a numeric reference in %s or %s', (label, url) => {
        caretAtEnd();
        component['openLink']();
        component.linkText.set(label);
        component.linkUrl.set(url);
        const original = editable.innerHTML;
        const emit = vi.spyOn(component.contentChange, 'emit');

        component['saveLink']();
        fixture.detectChanges();

        expect(component.linkOpen()).toBe(true);
        expect(component.linkInvalid()).toBe(true);
        expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
        expect(editable.innerHTML).toBe(original);
        expect(emit).not.toHaveBeenCalled();
    });

    it('keeps Shift+Enter as a newline after an empty completion search', async () => {
        search.mockResolvedValue([]);
        fixture.componentRef.setInput('content', 'ping @none');
        fixture.detectChanges();
        caretAtEnd();
        await component.refreshSuggestions();
        const bubbled = vi.fn();
        const emit = vi.spyOn(component.contentChange, 'emit');
        fixture.nativeElement.addEventListener('keydown', bubbled);
        const enter = new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true, bubbles: true, cancelable: true });

        editable.dispatchEvent(enter);
        editable.dispatchEvent(new InputEvent('beforeinput', { inputType: 'insertText', data: 'next', bubbles: true, cancelable: true }));

        expect(enter.defaultPrevented).toBe(true);
        expect(bubbled).not.toHaveBeenCalled();
        expect(component.suggestionsOpen()).toBe(false);
        expect(emit).toHaveBeenLastCalledWith('ping @none\nnext');
        fixture.nativeElement.removeEventListener('keydown', bubbled);
    });

    it('prevents link clicks from navigating while composing', () => {
        editable.innerHTML = '<p><a href="https://example.org">Guide</a></p>';
        const click = new MouseEvent('click', { bubbles: true, cancelable: true });
        editable.querySelector('a')!.dispatchEvent(click);
        expect(click.defaultPrevented).toBe(true);
        expect(component.linkOpen()).toBe(true);
        expect(component.linkUrl()).toBe('https://example.org');
    });

    it('preserves the selected words when applying toolbar formatting and block styles', () => {
        const range = document.createRange();
        range.selectNodeContents(editable.querySelector('p')!);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        const button = fixture.nativeElement.querySelector('[data-testid="message-format-strong"]') as HTMLButtonElement;
        const down = new MouseEvent('mousedown', { bubbles: true, cancelable: true });
        button.dispatchEvent(down);
        expect(down.defaultPrevented).toBe(true);
        button.click();
        expect(editable.querySelector('strong')!.textContent).toBe('Hello');
        expect(window.getSelection()!.toString()).toBe('Hello');
        (fixture.nativeElement.querySelector('[data-testid="message-block-ul"]') as HTMLButtonElement).click();
        expect(editable.querySelector('ul > li strong')!.textContent).toBe('Hello');
        expect(window.getSelection()!.toString()).toBe('Hello');
    });

    it.each(['b', 'i', 'u', 'k'])('handles the Ctrl/Cmd+%s shortcut inside the composer', (key) => {
        caretAtEnd();
        const event = new KeyboardEvent('keydown', { key, ctrlKey: true, bubbles: true, cancelable: true });
        editable.dispatchEvent(event);
        expect(event.defaultPrevented).toBe(true);
        if (key === 'k') {
            expect(component.linkOpen()).toBe(true);
        } else {
            const typing = new InputEvent('beforeinput', { inputType: 'insertText', data: ' world', bubbles: true, cancelable: true });
            editable.dispatchEvent(typing);
            expect(typing.defaultPrevented).toBe(true);
            expect(editable.querySelector(key === 'b' ? 'strong' : key === 'i' ? 'em' : 'ins')!.textContent).toBe(' world');
            expect(editable.textContent).toBe('Hello world');
        }
    });

    it('provides keyboard and browser-event undo and redo', () => {
        const emit = vi.spyOn(component.contentChange, 'emit');
        const selected = document.createRange();
        selected.selectNodeContents(editable.querySelector('p')!);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(selected);
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

    it('does not let Safari IME-confirmation Enter submit after compositionend', () => {
        const bubbled = vi.fn();
        fixture.nativeElement.addEventListener('keydown', bubbled);
        editable.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }));
        editable.dispatchEvent(new CompositionEvent('compositionend', { bubbles: true }));
        const enter = new KeyboardEvent('keydown', { key: 'Enter', keyCode: 229, bubbles: true, cancelable: true });

        editable.dispatchEvent(enter);

        expect(enter.defaultPrevented).toBe(false);
        expect(bubbled).not.toHaveBeenCalled();
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
        expect(bubbled).toHaveBeenCalledOnce();
        fixture.nativeElement.removeEventListener('keydown', bubbled);
    });

    it('owns cancellable code insertion so WebKit cannot move typing before a line break', () => {
        caretAtEnd();
        (fixture.nativeElement.querySelector('[data-testid="message-block-pre"]') as HTMLButtonElement).click();
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true, bubbles: true, cancelable: true }));
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

    it.each(['message-link-text', 'message-link-url'])('keeps %s open during every IME Enter variant and applies on a later normal Enter', (testId) => {
        fixture.componentRef.setInput('content', '[Guide](https://example.org/old)');
        fixture.detectChanges();
        editable.querySelector('a')!.click();
        fixture.detectChanges();
        const field = fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLInputElement;
        field.value = testId === 'message-link-text' ? '日本語' : 'https://example.org/new';
        field.dispatchEvent(new Event('input', { bubbles: true }));
        const emit = vi.spyOn(component.contentChange, 'emit');
        const html = editable.innerHTML;
        const bubbled = vi.fn();
        fixture.nativeElement.addEventListener('keydown', bubbled);

        field.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }));
        const active = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
        field.dispatchEvent(active);
        expect(active.defaultPrevented).toBe(false);
        expect(component.linkOpen()).toBe(true);
        field.dispatchEvent(new CompositionEvent('compositionend', { bubbles: true }));
        for (const options of [{ isComposing: true }, { keyCode: 229 }]) {
            const candidate = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true, ...options });
            field.dispatchEvent(candidate);
            expect(candidate.defaultPrevented).toBe(false);
            expect(component.linkOpen()).toBe(true);
        }
        expect(editable.innerHTML).toBe(html);
        expect(emit).not.toHaveBeenCalled();
        expect(bubbled).not.toHaveBeenCalled();

        const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
        field.dispatchEvent(enter);
        expect(enter.defaultPrevented).toBe(true);
        expect(component.linkOpen()).toBe(false);
        expect(editable.querySelector('a')!.textContent).toBe(testId === 'message-link-text' ? '日本語' : 'Guide');
        expect(editable.querySelector('a')!.getAttribute('href')).toBe(testId === 'message-link-url' ? 'https://example.org/new' : 'https://example.org/old');
        expect(emit).toHaveBeenCalledOnce();
        fixture.nativeElement.removeEventListener('keydown', bubbled);
    });

    it('does not send when keyboard activation targets a toolbar button', () => {
        const bubbled = vi.fn();
        fixture.nativeElement.parentElement.addEventListener('keydown', bubbled);
        fixture.nativeElement.querySelector('[data-testid="message-link-button"]').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
        expect(bubbled).not.toHaveBeenCalled();
        fixture.nativeElement.parentElement.removeEventListener('keydown', bubbled);
    });

    it.each(['Hello', '- Hello', '> Hello', '```\nHello\n```'])('sends with Enter and Cmd/Ctrl+Enter regardless of formatting: %s', (content) => {
        fixture.componentRef.setInput('content', content);
        fixture.detectChanges();
        const bubbled = vi.fn();
        fixture.nativeElement.addEventListener('keydown', bubbled);
        const range = document.createRange();
        range.selectNodeContents(editable.querySelector('li, code, p')!);
        range.collapse(false);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        const original = editable.innerHTML;
        for (const modifiers of [{}, { ctrlKey: true }, { metaKey: true }]) {
            const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true, ...modifiers });
            editable.dispatchEvent(enter);
            expect(enter.defaultPrevented).toBe(true);
            expect(editable.innerHTML).toBe(original);
        }
        expect(bubbled).toHaveBeenCalledTimes(3);
        fixture.nativeElement.removeEventListener('keydown', bubbled);
    });

    it.each([{}, { ctrlKey: true }, { metaKey: true }])('preserves native newlines in callers that opt out of sending: %o', (modifiers) => {
        fixture.componentRef.setInput('suppressNewlineOnEnter', false);
        fixture.detectChanges();
        caretAtEnd();
        const bubbled = vi.fn();
        fixture.nativeElement.addEventListener('keydown', bubbled);
        const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true, ...modifiers });
        editable.dispatchEvent(enter);
        expect(enter.defaultPrevented).toBe(false);
        expect(bubbled).not.toHaveBeenCalled();
        fixture.nativeElement.removeEventListener('keydown', bubbled);
    });

    it('keeps Enter as literal editing inside code when a dialog opts out of sending', () => {
        fixture.componentRef.setInput('suppressNewlineOnEnter', false);
        fixture.componentRef.setInput('content', '```\nfirst\n```');
        fixture.detectChanges();
        const range = document.createRange();
        range.selectNodeContents(editable.querySelector('code')!);
        range.collapse(false);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        const send = vi.fn();
        fixture.nativeElement.addEventListener('keydown', send);
        const emit = vi.spyOn(component.contentChange, 'emit');
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
        expect(send).not.toHaveBeenCalled();
        expect(emit).toHaveBeenLastCalledWith('```\nfirst\n\n```');
        fixture.nativeElement.removeEventListener('keydown', send);
    });

    it('sends with Cmd+Enter while a completion is open instead of accepting its highlighted row', async () => {
        fixture.componentRef.setInput('content', '@ann');
        fixture.detectChanges();
        caretAtEnd();
        await component.refreshSuggestions();
        const bubbled = vi.fn();
        fixture.nativeElement.addEventListener('keydown', bubbled);
        editable.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', metaKey: true, bubbles: true, cancelable: true }));
        expect(bubbled).toHaveBeenCalledOnce();
        expect(component.suggestionsOpen()).toBe(false);
        expect(editable.textContent).toBe('@ann');
        expect(editable.querySelector('[data-message-reference]')).toBeNull();
        fixture.nativeElement.removeEventListener('keydown', bubbled);
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
