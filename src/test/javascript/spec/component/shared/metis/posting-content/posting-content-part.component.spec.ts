import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { PostingContentPartComponent } from 'app/shared/metis/posting-content/posting-content-part/posting-content-part.components';
import { PostingContentPart } from 'app/shared/metis/metis.util';
import { HtmlForPostingMarkdownPipe } from 'app/shared/pipes/html-for-posting-markdown.pipe';
import { getElement, getElements } from '../../../../helpers/utils/general.utils';
import { MockQueryParamsDirective, MockRouterLinkDirective } from '../../../../helpers/mocks/directive/mock-router-link.directive';

describe('PostingContentPartComponent', () => {
    let component: PostingContentPartComponent;
    let fixture: ComponentFixture<PostingContentPartComponent>;
    let debugElement: DebugElement;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [
                PostingContentPartComponent,
                HtmlForPostingMarkdownPipe, // we want to test against the rendered string, therefore we cannot mock the pipe
                MockRouterLinkDirective,
                MockQueryParamsDirective,
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostingContentPartComponent);
                component = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    describe('For posting without reference', () => {
        it('should not contain a reference but only markdown content', () => {
            const postingContent = 'I do not want to reference a Post.';
            component.postingContentPart = {
                contentBeforeReference: postingContent,
                linkToReference: undefined,
                queryParams: undefined,
                referenceStr: undefined,
                contentAfterReference: undefined,
            } as PostingContentPart;
            fixture.detectChanges();
            const markdownRenderedTexts = getElements(debugElement, '.markdown-preview');
            expect(markdownRenderedTexts).toHaveLength(1);
            expect(markdownRenderedTexts![0].innerHTML).toBe('<p class="inline-paragraph">' + postingContent + '</p>');

            const referenceLink = getElement(debugElement, '.reference-hash');
            expect(referenceLink).toBe(null);
        });
    });

    describe('For posting with reference', () => {
        it('should contain a reference and markdown content before and after', () => {
            const contentBeforeReference = '**Be aware**\n\n I want to reference the following Post ';
            const contentAfterReference = 'in my content,\n\n does it *actually* work?';
            const referenceStr = '#7';
            component.postingContentPart = {
                contentBeforeReference,
                linkToReference: ['/whatever'],
                queryParams: { searchText: referenceStr },
                referenceStr,
                contentAfterReference,
            } as PostingContentPart;
            fixture.detectChanges();
            const markdownRenderedTexts = getElements(debugElement, '.markdown-preview');
            expect(markdownRenderedTexts).toHaveLength(2);
            // check that the paragraph right before the reference and the paragraph right after have the class `inline-paragraph`
            expect(markdownRenderedTexts![0].innerHTML).toInclude('<p><strong>Be aware</strong></p>');
            expect(markdownRenderedTexts![0].innerHTML).toInclude('<p class="inline-paragraph">I want to reference the following Post </p>'); // last paragraph before reference
            expect(markdownRenderedTexts![1].innerHTML).toInclude('<p class="inline-paragraph">in my content,</p>'); // first paragraph after reference
            expect(markdownRenderedTexts![1].innerHTML).toInclude('<p>does it <em>actually</em> work?</p>');
            const referenceLink = getElement(debugElement, '.reference-hash');
            expect(referenceLink).not.toBe(null);
            expect(referenceLink.innerHTML).toInclude(referenceStr);
        });
    });
});
