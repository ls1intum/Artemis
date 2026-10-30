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
import { MESSAGE_CLIPBOARD_TYPE } from './message-editor-clipboard';

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
        // A fixed input avoids the service's posts stream; the real parent still finds the references.
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
        // PostingMarkdownComponent imports its renderer lazily and publishes HTML asynchronously.
        // Checking the DOM on each retry waits for both the parent effect and actual child rendering.
        await vi.waitFor(() => {
            fixture.detectChanges();
            assertion(fixture.nativeElement as HTMLElement);
        });
    }

    const ada = '[user]Ada Example(ada_example)[/user]';
    const ben = '[user]Ben Sample(ben_sample)[/user]';
    const blockTags = new Set(['P', 'DIV', 'UL', 'OL', 'LI', 'BLOCKQUOTE', 'PRE']);

    /** Reads what a user sees as separate lines, with bullets as a marker, so the editor and the sent message can be compared. */
    function visibleLines(root: HTMLElement): string[] {
        const lines: string[] = [];
        let current = '';
        const flush = () => {
            const line = current.replace(/\s+/g, ' ').trim();
            if (line) lines.push(line);
            current = '';
        };
        const walk = (node: Node): void => {
            if (node.nodeType === Node.TEXT_NODE) {
                current += node.textContent;
                return;
            }
            if (node.nodeType !== Node.ELEMENT_NODE) return;
            const element = node as HTMLElement;
            if (element.tagName === 'BR') {
                if (!element.hasAttribute('data-message-placeholder')) flush();
                return;
            }
            if (element.hasAttribute('data-message-reference')) {
                // The editor labels a mention with its @ sign; the posting shows it as an icon next to the name.
                current += ' ' + (element.textContent ?? '').replace(/^[@#]/, '') + ' ';
                return;
            }
            if (element.matches('a.reference')) {
                current += ' ' + (element.textContent ?? '') + ' ';
                return;
            }
            const block = blockTags.has(element.tagName);
            if (block) flush();
            if (element.tagName === 'LI') current += '• ';
            element.childNodes.forEach(walk);
            if (block) flush();
        };
        walk(root);
        flush();
        return lines;
    }

    async function expectPostingToLookLikeEditor(expected: string[]): Promise<string> {
        expect(visibleLines(host)).toEqual(expected);
        const message = editorToMessage(host);
        await render(message, (view) => {
            expect(visibleLines(view)).toEqual(expected);
            expect(view.querySelector('li:empty')).toBeNull();
        });
        return message;
    }

    describe('mentions next to line breaks and in lists', () => {
        beforeEach(() => {
            editor.load('');
            selectAll();
        });

        it('keeps the line break directly after a mention', async () => {
            editor.insertMarkdown(ada);
            editor.insertText('\n');
            editor.insertText('More text ');
            editor.insertMarkdown(ben);
            await expectPostingToLookLikeEditor(['Ada Example', 'More text Ben Sample']);
        });

        it('keeps the line break directly before a mention', async () => {
            editor.insertText('Some text');
            editor.insertText('\n');
            editor.insertMarkdown(ada);
            editor.insertText(' and more');
            await expectPostingToLookLikeEditor(['Some text', 'Ada Example and more']);
        });

        it('keeps the line break between two mentions', async () => {
            editor.insertMarkdown(ada);
            editor.insertText('\n');
            editor.insertMarkdown(ben);
            await expectPostingToLookLikeEditor(['Ada Example', 'Ben Sample']);
        });

        it('keeps blank lines and line breaks around mentions across several paragraphs', async () => {
            editor.insertMarkdown(ada);
            editor.insertText('\nMore text ');
            editor.insertMarkdown(ben);
            editor.insertText('\n\n');
            editor.insertMarkdown(ada);
            editor.insertText(' some text\n');
            editor.insertMarkdown(ben);
            const message = await expectPostingToLookLikeEditor(['Ada Example', 'More text Ben Sample', 'Ada Example some text', 'Ben Sample']);
            // The empty line of the editor is an empty line of the message, which the posting shows as a paragraph break.
            expect(message).toBe(`${ada}\nMore text ${ben}\n\n${ada} some text\n${ben}`);
            await render(message, (view) => expect(view.querySelectorAll('p')).toHaveLength(2));
            editor.load(message);
            expect(visibleLines(host)).toEqual(['Ada Example', 'More text Ben Sample', 'Ada Example some text', 'Ben Sample']);
        });

        it('renders a bullet list whose items contain mentions without extra or empty bullets', async () => {
            editor.block('ul');
            editor.insertMarkdown(ada);
            editor.insertListItem();
            editor.insertText('More text ');
            editor.insertMarkdown(ben);
            editor.insertListItem();
            editor.insertMarkdown(ada);
            editor.insertText(' some text');
            editor.insertListItem();
            editor.insertMarkdown(ben);
            const message = await expectPostingToLookLikeEditor(['• Ada Example', '• More text Ben Sample', '• Ada Example some text', '• Ben Sample']);
            await render(message, (view) => expect(view.querySelectorAll('li')).toHaveLength(4));
        });

        it('renders a numbered list and a quote that contain mentions as in the editor', async () => {
            editor.load(`1. ${ada} first\n2. second ${ben}\n\n> ${ben} quoted`);
            await expectPostingToLookLikeEditor(['• Ada Example first', '• second Ben Sample', 'Ben Sample quoted']);
        });

        it('keeps the mounted mentions when the posting is recomputed without a change of its content', async () => {
            await render(`${ada} and ${ben}`, (view) => expect(view.querySelectorAll('a.reference')).toHaveLength(2));
            const mounted = Array.from(fixture.nativeElement.querySelectorAll('a.reference'));

            // Every update of the posts stream recomputes the references; unchanged ones must not be rebuilt, which would drop focus and selection.
            fixture.componentRef.setInput('isEdited', true);
            await vi.waitFor(() => {
                fixture.detectChanges();
                expect(fixture.nativeElement.querySelector('.edited-text')).not.toBeNull();
            });

            expect(Array.from(fixture.nativeElement.querySelectorAll('a.reference'))).toEqual(mounted);
            mounted.forEach((reference, index) => expect(fixture.nativeElement.querySelectorAll('a.reference')[index]).toBe(reference));
        });
    });

    it('renders a numeric entity typed literally without turning it into a post reference', async () => {
        editor.load('');
        selectAll();
        editor.insertText('Literal &#169; stays text');
        const message = editorToMessage(host);
        await render(message, (view) => {
            expect(view.textContent).toContain('Literal &#169; stays text');
            expect(view.querySelector('a.reference')).toBeNull();
        });
        editor.load(message);
        expect(host.textContent).toBe('Literal &#169; stays text');
    });

    it.each([String.raw`Literal \#123 stays text`, 'Literal &#35;123 stays text'])(
        'keeps a loaded literal numeric hash through unrelated edits, posting and reopening: %s',
        async (source) => {
            await render(source, (view) => {
                expect(view.textContent).toContain('Literal #123 stays text');
                expect(view.querySelector('a.reference')).toBeNull();
            });
            editor.load(source);
            expect(host.querySelector('[data-message-reference]')).toBeNull();
            const paragraph = host.querySelector('p')!;
            const last = paragraph.lastChild!;
            select(last, last.textContent!.length);
            editor.insertText(' updated');
            const saved = editorToMessage(host);
            await render(saved, (view) => {
                expect(view.textContent).toContain('Literal #123 stays text updated');
                expect(view.querySelector('a.reference')).toBeNull();
            });
            editor.load(saved);
            expect(host.textContent).toBe('Literal #123 stays text updated');
            expect(host.querySelector('[data-message-reference]')).toBeNull();
            expect(editorToMessage(host)).toBe(saved);
        },
    );

    it.each([
        [String.raw`Literal \#**123** stays text`, ['strong']],
        ['Literal &#35;**123** stays text', ['strong']],
        [String.raw`Literal \#***123*** stays text`, ['strong', 'em']],
        [String.raw`Literal \#*123* stays text`, ['em']],
        [String.raw`Literal \#<ins>123</ins> stays text`, ['ins']],
        ['Literal \\#`123` stays text', ['code']],
    ] as const)('keeps a literal hash across formatting boundaries when removing marks, saving and reopening: %s', async (source, marks) => {
        editor.load(source);
        for (const mark of marks) {
            const range = document.createRange();
            range.selectNodeContents(host.querySelector(mark)!);
            document.getSelection()!.removeAllRanges();
            document.getSelection()!.addRange(range);
            editor.format(mark);
            expect(host.querySelector(mark)).toBeNull();
        }
        const saved = editorToMessage(host);

        await render(saved, (view) => {
            expect(view.textContent).toContain('Literal #123 stays text');
            expect(view.querySelector('a.reference')).toBeNull();
        });
        editor.load(saved);
        expect(host.textContent).toBe('Literal #123 stays text');
        expect(host.querySelector('[data-message-reference]')).toBeNull();
        expect(editorToMessage(host)).toBe(saved);
    });

    it('keeps an edited literal hash literal through posting and reopening', async () => {
        editor.load(String.raw`Literal \#123 stays text`);
        const hash = host.querySelector('[data-message-literal]')!.firstChild!;
        select(hash, 1);
        editor.insertText('4');
        const saved = editorToMessage(host);
        await render(saved, (view) => {
            expect(view.textContent).toContain('Literal #4123 stays text');
            expect(view.querySelector('a.reference')).toBeNull();
        });
        editor.load(saved);
        expect(host.textContent).toBe('Literal #4123 stays text');
        expect(host.querySelector('[data-message-reference]')).toBeNull();
    });

    it.each([false, true])('keeps copied literal hashes literal through paste, posting and reopening (cut: %s)', async (cut) => {
        editor.load(String.raw`Literal \#123 stays text`);
        selectAll();
        const data = new Map<string, string>();
        expect(editor.writeClipboard({ setData: (type, value) => data.set(type, value) }, cut)).toBe(true);
        if (cut) expect(host.textContent).toBe('');
        editor.load('');
        selectAll();
        editor.insertClipboard(data.get('text/html')!, data.get('text/plain')!, data.get(MESSAGE_CLIPBOARD_TYPE)!);
        const saved = editorToMessage(host);

        await render(saved, (view) => {
            expect(view.textContent).toContain('Literal #123 stays text');
            expect(view.querySelector('a.reference')).toBeNull();
        });
        editor.load(saved);
        expect(host.textContent).toBe('Literal #123 stays text');
        expect(host.querySelector('[data-message-reference]')).toBeNull();
        expect(editorToMessage(host)).toBe(saved);
    });

    it.each(['strong', 'code'] as const)('keeps %s formatting on a loaded literal hash through posting and reopening', async (mark) => {
        editor.load(String.raw`Literal \#123 stays text`);
        selectAll();
        editor.format(mark);
        const saved = editorToMessage(host);
        await render(saved, (view) => {
            expect(view.textContent).toContain('Literal #123 stays text');
            expect(view.querySelector('a.reference')).toBeNull();
            expect(
                Array.from(view.querySelectorAll(mark), (node) => node.textContent)
                    .join('')
                    .replace(/\s/g, ''),
            ).toBe('Literal#123staystext');
        });
        editor.load(saved);
        expect(
            Array.from(host.querySelectorAll(mark), (node) => node.textContent)
                .join('')
                .replace(/\s/g, ''),
        ).toBe('Literal#123staystext');
        expect(editorToMessage(host)).toBe(saved);
    });

    it.each(['`#123`', '```\n#123\n```'])('renders numeric references literally inside code: %s', async (message) => {
        editor.load(message);
        await render(editorToMessage(host), (view) => {
            expect(view.querySelector('code')?.textContent).toContain('#123');
            expect(view.querySelector('a.reference')).toBeNull();
        });
        editor.load(editorToMessage(host));
        expect(host.querySelector('code')?.textContent).toContain('#123');
    });

    it('renders a mention accepted while bold is active without stray Markdown delimiters', async () => {
        editor.load('');
        selectAll();
        editor.format('strong');
        for (const character of 'Hello @ann') {
            editor.insertText(character);
        }
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

    it('renders pasted formatting with the actual posting renderer and reopens it without losing structure', async () => {
        editor.load('');
        selectAll();
        editor.insertClipboard(
            '<p><b>Bold</b> <i>italic</i> <a href="https://example.org">guide</a></p><ul><li>item</li></ul><pre><code>a\nb</code></pre>',
            'Bold italic guide\nitem\na\nb',
        );
        const message = editorToMessage(host);
        await render(message, (view) => {
            expect(view.querySelector('strong')?.textContent).toBe('Bold');
            expect(view.querySelector('em')?.textContent).toBe('italic');
            expect(view.querySelector('a[href="https://example.org"]')?.textContent).toBe('guide');
            expect(view.querySelector('ul li')?.textContent).toBe('item');
            expect(view.querySelector('pre code')?.textContent).toContain('a\nb');
        });
        editor.load(message);
        expect(editorToMessage(host)).toBe(message);
        expect(host.querySelector('strong')?.textContent).toBe('Bold');
        expect(host.querySelector('ul li')?.textContent).toBe('item');
    });

    it('keeps copied mentions and attachments through paste, actual posting rendering and reopening', async () => {
        const source = '**Read** [user]Anna(anna)[/user] ![image.png](/api/core/files/image.png)';
        editor.load(source);
        selectAll();
        const data = new Map<string, string>();
        const clipboard = { setData: (type: string, value: string) => data.set(type, value) };
        expect(editor.writeClipboard(clipboard)).toBe(true);
        editor.load('');
        selectAll();
        editor.insertClipboard(data.get('text/html')!, data.get('text/plain')!, data.get(MESSAGE_CLIPBOARD_TYPE)!);
        expect(editorToMessage(host)).toBe(source);
        await render(source, (view) => {
            expect(view.querySelector('strong')?.textContent).toBe('Read');
            expect(view.querySelector('a.reference')?.textContent).toContain('Anna');
            expect(view.querySelector('img')?.getAttribute('src')).toContain('image.png');
            expect(view.textContent).not.toContain('[user]');
        });
        editor.load(editorToMessage(host));
        expect(host.querySelector('img')?.getAttribute('src')).toBe('/api/core/files/image.png');
        expect(host.querySelector('[data-message-reference="[user]Anna(anna)[/user]"]')).not.toBeNull();
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
