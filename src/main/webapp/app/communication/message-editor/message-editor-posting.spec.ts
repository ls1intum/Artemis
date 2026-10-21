import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockComponent } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { PostingContentComponent } from 'app/communication/posting-content/posting-content.components';
import { PostingContentPartComponent } from 'app/communication/posting-content/posting-content-part/posting-content-part.components';
import { LinkPreviewContainerComponent } from 'app/communication/link-preview/components/link-preview-container/link-preview-container.component';
import { CommunicationService } from 'app/communication/service/communication.service';
import { AccountService } from 'app/core/auth/account.service';
import { FileService } from 'app/foundation/service/file.service';
import { MockCommunicationService } from 'test/helpers/mocks/service/mock-communication.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockFileService } from 'test/helpers/mocks/service/mock-file.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MessageEditorDom } from './message-editor-dom';
import { editorToMessage } from './message-editor-conversion';

describe('Message editor content in the actual posting view', () => {
    let fixture: ComponentFixture<PostingContentComponent>;
    let host: HTMLDivElement;
    let editor: MessageEditorDom;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PostingContentComponent],
            providers: [
                provideRouter([]),
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: CommunicationService, useClass: MockCommunicationService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: FileService, useClass: MockFileService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(PostingContentComponent, {
                remove: { imports: [LinkPreviewContainerComponent] },
                add: { imports: [MockComponent(LinkPreviewContainerComponent)] },
            })
            .overrideComponent(PostingContentPartComponent, {
                set: { providers: [{ provide: DialogService, useValue: { open: vi.fn() } }] },
            })
            .compileComponents();
        fixture = TestBed.createComponent(PostingContentComponent);
        // A fixed input avoids the service's posts stream; the real parent still performs reference splitting.
        fixture.componentRef.setInput('subscribesToPosts', false);
        host = document.createElement('div');
        host.contentEditable = 'true';
        document.body.append(host);
        editor = new MessageEditorDom(host, () => undefined);
    });

    afterEach(() => {
        fixture.destroy();
        host.remove();
        document.getSelection()?.removeAllRanges();
    });

    function select(node: Node, start: number, end = start): void {
        const range = document.createRange();
        range.setStart(node, start);
        range.setEnd(node, end);
        const selection = document.getSelection()!;
        selection.removeAllRanges();
        selection.addRange(range);
    }

    function selectAll(): void {
        const range = document.createRange();
        range.selectNodeContents(host);
        const selection = document.getSelection()!;
        selection.removeAllRanges();
        selection.addRange(range);
    }

    async function render(message: string, assertion: (view: HTMLElement) => void): Promise<void> {
        fixture.componentRef.setInput('content', message);
        fixture.detectChanges();
        // MarkdownDirective imports its renderer lazily and publishes HTML asynchronously.
        // Checking the DOM on each retry waits for both the parent effect and actual child rendering.
        await vi.waitFor(() => {
            fixture.detectChanges();
            assertion(fixture.nativeElement as HTMLElement);
        });
    }

    it('renders a mention accepted while bold is active without stray Markdown delimiters', async () => {
        editor.load('');
        selectAll();
        editor.format('strong');
        editor.insertText('Hello @ann');
        const beforeAcceptance = editorToMessage(host);
        const completion = editor.completion()!;
        expect(completion.query).toBe('ann');
        editor.insertMarkdown('[user]Anna(anna)[/user] ', completion.range);
        const accepted = editorToMessage(host);
        expect(host.querySelector('strong [data-message-reference]')).toBeNull();

        await render(accepted, (view) => {
            expect(view.querySelector('strong')?.textContent).toBe('Hello');
            expect(view.querySelector('a.reference')?.textContent).toContain('Anna');
            expect(view.textContent).not.toContain('**');
            expect(view.textContent).not.toContain('[user]');
        });

        editor.undo();
        expect(editorToMessage(host)).toBe(beforeAcceptance);
        editor.redo();
        expect(editorToMessage(host)).toBe(accepted);
        await render(editorToMessage(host), (view) => {
            expect(view.querySelector('strong')?.textContent).toBe('Hello');
            expect(view.querySelector('a.reference')?.textContent).toContain('Anna');
            expect(view.textContent).not.toContain('**');
        });

        // Explicitly formatting the surrounding sentence must not put the reference inside a mark again.
        selectAll();
        editor.format('em');
        expect(host.querySelector('strong [data-message-reference], em [data-message-reference]')).toBeNull();
        await render(editorToMessage(host), (view) => {
            expect(view.querySelector('a.reference')?.textContent).toContain('Anna');
            expect(view.querySelector('strong')?.textContent).toBe('Hello');
            expect(view.textContent).not.toContain('*');
        });
    });

    it('rejects linking a numeric post reference and keeps the actual posting view unchanged', async () => {
        editor.load('See #123 today');
        expect(host.querySelector('[data-message-reference="#123"]')?.getAttribute('contenteditable')).toBe('false');
        selectAll();
        const original = editorToMessage(host);
        expect(editor.canLinkSelection()).toBe(false);
        editor.setLink('See #123 today', 'https://example.org');
        expect(editorToMessage(host)).toBe(original);
        expect(host.querySelector('a')).toBeNull();

        await render(editorToMessage(host), (view) => {
            expect(view.querySelectorAll('a.reference')).toHaveLength(1);
            expect(view.querySelector('a.reference')?.textContent).toContain('#123');
            expect(view.textContent).toContain('See');
            expect(view.textContent).toContain('today');
            expect(view.textContent).not.toContain('https://example.org');
            expect(view.textContent).not.toContain('](');
            expect(view.querySelector('a[href="https://example.org"]')).toBeNull();
        });
        editor.undo();
        expect(editorToMessage(host)).toBe(original);
    });

    it('renders a numeric reference typed character by character in bold without replacing the editable DOM', async () => {
        editor.load('');
        selectAll();
        editor.format('strong');
        for (const character of 'Read #123 today') {
            editor.insertText(character);
        }
        const html = host.innerHTML;

        editor.afterInput();
        const message = editorToMessage(host);

        expect(host.innerHTML).toBe(html);
        expect(host.querySelector('[data-message-reference]')).toBeNull();
        await render(message, (view) => {
            expect(Array.from(view.querySelectorAll('strong'), (mark) => mark.textContent)).toEqual(['Read', 'today']);
            expect(view.querySelector('a.reference')?.textContent).toContain('#123');
            expect(view.textContent).not.toContain('**');
        });
    });

    it('also rejects a partial reference typed natively without changing its normal numeric typing', async () => {
        editor.load('');
        const paragraph = document.createElement('p');
        const text = document.createTextNode('See #1');
        paragraph.append(text);
        host.replaceChildren(paragraph);
        select(text, text.length);
        editor.afterInput();
        editor.beforeInput();
        text.appendData('23 today');
        select(text, text.length);
        editor.afterInput();
        // Native input stays editable: turning #1 into an atomic item after each keystroke would break this.
        expect(host.textContent).toBe('See #123 today');
        select(text, 6, 8); // Select only "23", still inside the renderer's #123 token.
        const original = editorToMessage(host);
        expect(editor.canLinkSelection()).toBe(false);
        editor.setLink('23', 'https://example.org');
        expect(editorToMessage(host)).toBe(original);
        await render(original, (view) => {
            expect(view.querySelectorAll('a.reference')).toHaveLength(1);
            expect(view.querySelector('a.reference')?.textContent).toContain('#123');
            expect(view.textContent).not.toContain('](');
        });
    });
});
