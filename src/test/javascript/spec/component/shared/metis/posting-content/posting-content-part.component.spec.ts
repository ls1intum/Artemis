import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FileService } from 'app/shared/http/file.service';
import { PostingContentPart, ReferenceType } from 'app/shared/metis/metis.util';
import { PostingContentPartComponent } from 'app/shared/metis/posting-content/posting-content-part/posting-content-part.components';
import { HtmlForPostingMarkdownPipe } from 'app/shared/pipes/html-for-posting-markdown.pipe';
import { MockQueryParamsDirective, MockRouterLinkDirective } from '../../../../helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from '../../../../helpers/mocks/mock-router';
import { MockFileService } from '../../../../helpers/mocks/service/mock-file.service';
import { getElement, getElements } from '../../../../helpers/utils/general.utils';

describe('PostingContentPartComponent', () => {
    let component: PostingContentPartComponent;
    let fixture: ComponentFixture<PostingContentPartComponent>;
    let debugElement: DebugElement;
    let router: Router;
    let fileService: FileService;
    let openAttachmentSpy: jest.SpyInstance;
    let navigateByUrlSpy: jest.SpyInstance;

    let contentBeforeReference: string;
    let contentAfterReference: string;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [
                PostingContentPartComponent,
                HtmlForPostingMarkdownPipe, // we want to test against the rendered string, therefore we cannot mock the pipe
                FaIconComponent, // we want to test the type of rendered icons, therefore we cannot mock the component
                MockRouterLinkDirective,
                MockQueryParamsDirective,
            ],
            providers: [
                { provide: FileService, useClass: MockFileService },
                { provide: Router, useClass: MockRouter },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostingContentPartComponent);
                component = fixture.componentInstance;
                debugElement = fixture.debugElement;
                router = TestBed.inject(Router);
                fileService = TestBed.inject(FileService);

                navigateByUrlSpy = jest.spyOn(router, 'navigateByUrl');
                openAttachmentSpy = jest.spyOn(fileService, 'downloadFileWithAccessToken');

                contentBeforeReference = '**Be aware**\n\n I want to reference the following Post ';
                contentAfterReference = 'in my content,\n\n does it *actually* work?';
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
            expect(referenceLink).toBeNull();
        });
    });

    describe('For posting with reference', () => {
        it('should contain a post reference with icon and markdown content before and after', () => {
            const referenceStr = '#7';
            component.postingContentPart = {
                contentBeforeReference,
                linkToReference: ['/whatever'],
                queryParams: { searchText: referenceStr },
                referenceStr: '#7',
                referenceType: ReferenceType.POST,
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

            // should display post number to user
            const referenceLink = getElement(debugElement, '.reference');
            expect(referenceLink).not.toBeNull();
            expect(referenceLink.innerHTML).toInclude(referenceStr);

            // should display relevant icon for post
            const icon = getElement(debugElement, 'fa-icon');
            expect(icon).not.toBeNull();
            expect(icon.innerHTML).toInclude('fa fa-message');

            // on click should navigate to referenced post within current tab
            referenceLink.click();
            expect(navigateByUrlSpy).toHaveBeenCalledOnce();
            expect(openAttachmentSpy).not.toHaveBeenCalled();
        });

        it.each([
            ['File Upload Exercise', '/courses/1/exercises/30', ReferenceType.FILE_UPLOAD, 'fa fa-file-arrow-up'],
            ['Modeling Exercise', '/courses/1/exercises/29', ReferenceType.MODELING, 'fa fa-diagram-project'],
            ['Quiz Exercise', '/courses/1/exercises/61', ReferenceType.QUIZ, 'fa fa-check-double'],
            ['Programming Exercise', '/courses/1/exercises/53', ReferenceType.PROGRAMMING, 'fa fa-keyboard'],
            ['Text Exercise', '/courses/1/exercises/28', ReferenceType.TEXT, 'fa fa-font'],
            ['Exercise', '/courses/1/exercises/28', undefined, 'fa fa-paperclip'],
            ['Test Lecture', '/courses/1/lectures/1/', ReferenceType.LECTURE, 'fa fa-chalkboard-user'],
        ])('should contain a reference to artifact with icon', (referenceStr, linkToReference, referenceType, faIcon) => {
            component.postingContentPart = {
                contentBeforeReference,
                linkToReference: [linkToReference],
                referenceStr,
                referenceType,
                contentAfterReference,
            } as PostingContentPart;
            fixture.detectChanges();

            // should display artifact name to user
            const referenceLink = getElement(debugElement, '.reference');
            expect(referenceLink).not.toBeNull();
            expect(referenceLink.innerHTML).toInclude(referenceStr);

            // should display relevant icon for artifact according to its type
            const icon = getElement(debugElement, 'fa-icon');
            expect(icon).not.toBeNull();
            expect(icon.innerHTML).toInclude(faIcon);

            // on click should navigate to referenced artifact within current tab
            referenceLink.click();
            expect(navigateByUrlSpy).toHaveBeenCalledOnce();
            expect(openAttachmentSpy).not.toHaveBeenCalled();
        });

        it('should contain a reference to attachment with icon', () => {
            const referenceStr = 'Lecture 1 - Slide';
            const attachmentURL = '/api/files/attachments/lecture/1/Lecture-1.pdf';
            component.postingContentPart = {
                contentBeforeReference,
                referenceStr,
                referenceType: ReferenceType.ATTACHMENT,
                attachmentToReference: attachmentURL,
                contentAfterReference,
            } as PostingContentPart;
            fixture.detectChanges();

            // should display file name to user
            const referenceLink = getElement(debugElement, '.reference');
            expect(referenceLink).not.toBeNull();
            expect(referenceLink.innerHTML).toInclude(referenceStr);

            // should display relevant icon for attachment
            const icon = getElement(debugElement, 'fa-icon');
            expect(icon).not.toBeNull();
            expect(icon.innerHTML).toInclude('fa fa-file');

            // on click should open referenced attachment within new tab
            referenceLink.click();
            expect(openAttachmentSpy).toHaveBeenCalledOnce();
            expect(openAttachmentSpy).toHaveBeenCalledWith(attachmentURL);
        });
    });
});
