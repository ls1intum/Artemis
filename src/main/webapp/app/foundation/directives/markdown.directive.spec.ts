import { WritableSignal, inputBinding, outputBinding, signal } from '@angular/core';
import { DirectiveFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';

describe('MarkdownDirective', () => {
    let fixture: DirectiveFixture<MarkdownDirective>;
    let text: WritableSignal<string | undefined>;
    let renderCount: number;

    const element = (): Element => fixture.nativeElement;

    beforeEach(() => {
        text = signal<string | undefined>(undefined);
        renderCount = 0;
        fixture = TestBed.createDirective(MarkdownDirective, {
            tagName: 'div',
            bindings: [inputBinding('jhiMarkdown', text), outputBinding('markdownRendered', () => renderCount++)],
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
});
