import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';

@Component({
    template: `<div class="md" [jhiMarkdown]="text()" [markdownPosting]="posting()" [markdownContentBeforeReference]="before()"></div>`,
    imports: [MarkdownDirective],
})
class TestHostComponent {
    readonly text = signal<string | undefined>(undefined);
    readonly posting = signal(false);
    readonly before = signal(true);
}

describe('MarkdownDirective', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<TestHostComponent>;
    let host: TestHostComponent;

    const element = (): HTMLElement => fixture.nativeElement.querySelector('div.md');

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TestHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(TestHostComponent);
        host = fixture.componentInstance;
    });

    it('renders markdown into the host innerHTML (lazily)', async () => {
        host.text.set('# Heading\n\nsome **bold** text');
        fixture.detectChanges();
        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(element().innerHTML).toContain('<h1');
        });
        expect(element().innerHTML).toContain('<strong>bold</strong>');
    });

    it('renders nothing for empty content', async () => {
        host.text.set('');
        fixture.detectChanges();
        await Promise.resolve();
        fixture.detectChanges();
        expect(element().innerHTML).toBe('');
    });

    it('highlights fenced code blocks of a registered language', async () => {
        host.text.set('```java\npublic class A {}\n```');
        fixture.detectChanges();
        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(element().innerHTML).toContain('hljs');
        });
    });

    it('applies the inline-paragraph class in posting mode', async () => {
        host.posting.set(true);
        host.text.set('hello world');
        fixture.detectChanges();
        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(element().innerHTML).toContain('inline-paragraph');
        });
    });
});
