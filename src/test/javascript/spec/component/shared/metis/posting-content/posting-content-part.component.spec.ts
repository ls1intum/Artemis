import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement, Directive, Input } from '@angular/core';
import { getElement, getElements } from '../../../../helpers/utils/general.utils';
import { PostingContentPartComponent } from 'app/shared/metis/posting-content/posting-content-part/posting-content-part.components';
import { PostingContentPart } from 'app/shared/metis/metis.util';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

import { MockDirective } from 'ng-mocks';

chai.use(sinonChai);
const expect = chai.expect;

// tslint:disable-next-line:directive-selector
@Directive({ selector: '[routerLink]' })
export class MockRouterLinkDirective {
    @Input('routerLink') data: any;
}

// tslint:disable-next-line:directive-selector
@Directive({ selector: '[queryParams]' })
export class MockQueryParamsDirective {
    @Input('queryParams') data: any;
}

describe('PostingContentPartComponent', () => {
    let component: PostingContentPartComponent;
    let fixture: ComponentFixture<PostingContentPartComponent>;
    let debugElement: DebugElement;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [
                PostingContentPartComponent,
                HtmlForMarkdownPipe, // we want to test against the rendered string, therefore we cannot mock the pipe
                MockDirective(MockRouterLinkDirective),
                MockDirective(MockQueryParamsDirective),
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
            expect(markdownRenderedTexts).to.have.length(1);
            expect(markdownRenderedTexts![0].innerHTML).to.be.equal('<p>' + postingContent + '</p>');

            const referenceLink = getElement(debugElement, '.reference-hash');
            expect(referenceLink).to.not.exist;
        });
    });

    describe('For posting with reference', () => {
        it('should contain a reference and markdown content before and after', () => {
            const contentBeforeReference = 'I want to reference the following Post ';
            const contentAfterReference = 'in my content.';
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
            expect(markdownRenderedTexts).to.have.length(2);
            expect(markdownRenderedTexts![0].innerHTML).to.be.equal('<p>' + contentBeforeReference + '</p>');
            expect(markdownRenderedTexts![1].innerHTML).to.be.equal('<p>' + contentAfterReference + '</p>');

            const referenceLink = getElement(debugElement, '.reference-hash');
            expect(referenceLink).to.exist;
        });
    });
});
