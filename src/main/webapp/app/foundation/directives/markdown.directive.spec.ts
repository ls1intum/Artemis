import { WritableSignal, inputBinding, outputBinding, signal } from '@angular/core';
import { DirectiveFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';

describe('MarkdownDirective', () => {
    let fixture: DirectiveFixture<MarkdownDirective>;
    let text: WritableSignal<string | undefined>;
    let posting: WritableSignal<boolean>;
    let renderCount: number;

    const element = (): Element => fixture.nativeElement;

    beforeEach(() => {
        text = signal<string | undefined>(undefined);
        posting = signal(false);
        renderCount = 0;
        fixture = TestBed.createDirective(MarkdownDirective, {
            tagName: 'div',
            bindings: [
                inputBinding('jhiMarkdown', text),
                inputBinding('markdownPosting', posting),
                inputBinding('markdownContentBeforeReference', () => true),
                outputBinding('markdownRendered', () => renderCount++),
            ],
        });
    });

    it('renders markdown into the host innerHTML (lazily)', async () => {
        text.set('# Heading\n\nsome **bold** text');
        fixture.detectChanges();
        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(element().innerHTML).toContain('<h1');
        });
        expect(element().innerHTML).toContain('<strong>bold</strong>');
        expect(renderCount).toBe(1);
    });

    it('renders nothing for empty content', async () => {
        text.set('');
        fixture.detectChanges();
        await Promise.resolve();
        fixture.detectChanges();
        expect(element().innerHTML).toBe('');
    });

    it('highlights fenced code blocks of a registered language', async () => {
        text.set('```java\npublic class A {}\n```');
        fixture.detectChanges();
        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(element().innerHTML).toContain('hljs');
        });
    });

    it('applies the inline-paragraph class in posting mode', async () => {
        posting.set(true);
        text.set('hello world');
        fixture.detectChanges();
        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(element().innerHTML).toContain('inline-paragraph');
        });
    });
});
