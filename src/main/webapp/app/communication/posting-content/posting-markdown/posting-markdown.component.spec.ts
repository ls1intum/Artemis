import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApplicationRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { PostingContentPart, ReferenceType } from 'app/communication/communication.util';
import { PostingContentPartComponent } from 'app/communication/posting-content/posting-content-part/posting-content-part.components';
import { PostingMarkdownComponent, postingReferencePlaceholder } from 'app/communication/posting-content/posting-markdown/posting-markdown.component';
import { AccountService } from 'app/core/auth/account.service';
import { FileService } from 'app/foundation/service/file.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockFileService } from 'test/helpers/mocks/service/mock-file.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import * as markdownRenderer from 'app/foundation/util/markdown-render.util';

describe('PostingMarkdownComponent', () => {
    let fixture: ComponentFixture<PostingMarkdownComponent>;

    afterEach(() => vi.restoreAllMocks());

    const user = (name: string, login: string): PostingContentPart => ({ referenceType: ReferenceType.USER, referenceStr: name, queryParams: { referenceUserLogin: login } });
    const channel = (name: string, channelId: number): PostingContentPart => ({ referenceType: ReferenceType.CHANNEL, referenceStr: name, queryParams: { channelId } });

    const view = (): HTMLElement => fixture.nativeElement;
    const references = (): HTMLElement[] => Array.from(view().querySelectorAll<HTMLElement>('a.reference'));

    async function render(markdown: string, parts: PostingContentPart[], mounted = parts.length): Promise<void> {
        fixture.componentRef.setInput('markdown', markdown);
        fixture.componentRef.setInput('references', parts);
        // The markdown pipeline is imported lazily, so the HTML and the references arrive after the first change detection.
        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(references()).toHaveLength(mounted);
        });
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PostingMarkdownComponent],
            providers: [
                provideRouter([]),
                { provide: AccountService, useClass: MockAccountService },
                { provide: FileService, useClass: MockFileService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(PostingContentPartComponent, { set: { providers: [{ provide: DialogService, useValue: { open: vi.fn() } }] } })
            .compileComponents();
        fixture = TestBed.createComponent(PostingMarkdownComponent);
    });

    it('mounts each reference at its placeholder within one rendered document', async () => {
        const markdown = `Hello ${postingReferencePlaceholder(0)} and ${postingReferencePlaceholder(1)}\n\n- ${postingReferencePlaceholder(0)}\n- later ${postingReferencePlaceholder(1)}`;
        fixture.componentRef.setInput('markdown', markdown);
        fixture.componentRef.setInput('references', [user('Ada Example', 'ada_example'), channel('organisation', 7)]);
        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(references()).toHaveLength(4);
        });

        expect(references().map((reference) => reference.textContent?.trim())).toEqual(['Ada Example', 'organisation', 'Ada Example', 'organisation']);
        const items = Array.from(view().querySelectorAll('li'));
        expect(items).toHaveLength(2);
        expect(items[0].querySelector('a.reference')).not.toBeNull();
        expect(items[1].textContent).toContain('later');
        expect(items[1].querySelector('a.reference')).not.toBeNull();
    });

    it('keeps a list and its line breaks together around references', async () => {
        const ada = postingReferencePlaceholder(0);
        await render(`${ada}\nMore text ${ada}\n\n- ${ada}\n- More text ${ada}`, [user('Ada Example', 'ada_example')], 4);

        expect(view().querySelectorAll('ul')).toHaveLength(1);
        expect(Array.from(view().querySelectorAll('li'), (item) => item.textContent?.trim())).toEqual(['Ada Example', 'More text Ada Example']);
        expect(view().querySelectorAll('p br')).toHaveLength(1);
    });

    it('re-emits clicks on user and channel references', async () => {
        const userClicked = vi.fn();
        const channelClicked = vi.fn();
        fixture.componentInstance.userReferenceClicked.subscribe(userClicked);
        fixture.componentInstance.channelReferenceClicked.subscribe(channelClicked);
        await render(`${postingReferencePlaceholder(0)} ${postingReferencePlaceholder(1)}`, [user('Ada Example', 'ada_example'), channel('organisation', 7)]);

        const [userReference, channelReference] = references();
        userReference.click();
        channelReference.click();

        expect(userClicked).toHaveBeenCalledExactlyOnceWith('ada_example');
        expect(channelClicked).toHaveBeenCalledExactlyOnceWith(7);
    });

    it('replaces the mounted references when the content changes', async () => {
        await render(`first ${postingReferencePlaceholder(0)}`, [user('Ada Example', 'ada_example')]);

        fixture.componentRef.setInput('markdown', `${postingReferencePlaceholder(0)} second`);
        fixture.componentRef.setInput('references', [user('Ben Sample', 'ben_sample')]);
        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(view().textContent).toContain('second');
        });

        expect(references().map((reference) => reference.textContent?.trim())).toEqual(['Ben Sample']);
        expect(view().textContent).not.toContain('first');
    });

    it('remounts the references when only their data changes', async () => {
        const markdown = `See ${postingReferencePlaceholder(0)}`;
        await render(markdown, [{ referenceType: ReferenceType.POST, referenceStr: '#5', linkToReference: ['/discussion'], queryParams: { searchText: '#5' } }]);
        expect(references()[0].getAttribute('href')).toContain('searchText');

        fixture.componentRef.setInput('references', [{ referenceType: ReferenceType.POST, referenceStr: '#5', linkToReference: ['/discussion'], queryParams: { postId: 5 } }]);
        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(references()[0].getAttribute('href')).toContain('postId=5');
        });

        expect(references()).toHaveLength(1);
    });

    it('ignores a placeholder that no reference belongs to', async () => {
        // A user can type this markup into a message; it must not mount anything for an index the message has no reference for.
        fixture.componentRef.setInput('markdown', `typed ${postingReferencePlaceholder(3)} text`);
        fixture.componentRef.setInput('references', [user('Ada Example', 'ada_example')]);
        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(view().textContent).toContain('typed');
        });

        expect(references()).toHaveLength(0);
    });

    it('shows the text of a reference inside a link instead of nesting an anchor in it', async () => {
        fixture.componentRef.setInput('markdown', `[open ${postingReferencePlaceholder(0)}](https://example.org) and ${postingReferencePlaceholder(0)}`);
        fixture.componentRef.setInput('references', [user('Ada Example', 'ada_example')]);
        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(view().querySelector('a[href]')).not.toBeNull();
        });

        expect(references()).toHaveLength(1);
        expect(view().querySelectorAll('a a')).toHaveLength(0);
        expect(view().querySelector('a[href]')?.textContent).toBe('open Ada Example');
    });

    it('does not run scripts or keep unsafe markup of the content', async () => {
        fixture.componentRef.setInput('markdown', `<img src=x onerror="window.postingXss = true"> <script>window.postingXss = true</script> ${postingReferencePlaceholder(0)}`);
        fixture.componentRef.setInput('references', [user('Ada Example', 'ada_example')]);
        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(references()).toHaveLength(1);
        });

        expect(view().querySelector('img, script')).toBeNull();
        expect((window as unknown as { postingXss?: boolean }).postingXss).toBeUndefined();
    });

    it('sanitizes renderer output again at the Angular HTML binding', async () => {
        // Even if the markdown pipeline lets an unsafe attribute through, the binding must not trust it.
        vi.spyOn(markdownRenderer, 'renderMarkdownToHtml').mockResolvedValue('<p onclick="alert(1)">Rendered text</p>');
        fixture.componentRef.setInput('markdown', 'Rendered text');
        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(view().querySelector('p')?.textContent).toBe('Rendered text');
        });

        expect(view().querySelector('p')?.hasAttribute('onclick')).toBe(false);
    });

    it('removes unsafe links while retaining reference placement', async () => {
        await render(`<a href="javascript:alert(1)">unsafe</a> ${postingReferencePlaceholder(0)}`, [user('Ada Example', 'ada_example')]);

        expect(view().querySelector('a')?.hasAttribute('href')).toBe(false);
        expect(references()[0].textContent?.trim()).toBe('Ada Example');
    });

    it('strips user-supplied styling classes while retaining reference markers', async () => {
        await render(`<a class="fixed-top w-100 h-100 bg-white" href="https://example.org">Link</a> <span class="fixed-top">Text</span> ${postingReferencePlaceholder(0)}`, [
            user('Ada Example', 'ada_example'),
        ]);

        expect(view().querySelector('a[href="https://example.org"]')?.hasAttribute('class')).toBe(false);
        expect(view().querySelector('.fixed-top')).toBeNull();
        expect(references()[0].textContent?.trim()).toBe('Ada Example');
    });

    it('destroys the mounted references with the component', async () => {
        const applicationRef = TestBed.inject(ApplicationRef);
        const attachedBefore = applicationRef.viewCount;
        await render(postingReferencePlaceholder(0), [user('Ada Example', 'ada_example')]);
        expect(applicationRef.viewCount).toBe(attachedBefore + 1);

        fixture.destroy();

        // A reference is a component of its own, attached to the application: it must not outlive the posting.
        expect(applicationRef.viewCount).toBe(attachedBefore);
    });
});
