import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostingContentPartComponent } from 'app/shared/metis/posting-content/posting-content-part/posting-content-part.components';
import { PostingContentPart, ReferenceType } from 'app/shared/metis/metis.util';
import { HtmlForPostingMarkdownPipe } from 'app/shared/pipes/html-for-posting-markdown.pipe';
import { getElement, getElements } from '../../../../helpers/utils/general.utils';
import { MockQueryParamsDirective, MockRouterLinkDirective } from '../../../../helpers/mocks/directive/mock-router-link.directive';
import { FileService } from 'app/shared/http/file.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { MockFileService } from '../../../../helpers/mocks/service/mock-file.service';
import { MockRouter } from '../../../../helpers/mocks/mock-router';

describe('PostingContentPartComponent', () => {
    let component: PostingContentPartComponent;
    let fixture: ComponentFixture<PostingContentPartComponent>;
    let debugElement: DebugElement;
    let router: Router;
    let metisService: MetisService;
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
                { provide: MetisService, useClass: MockMetisService },
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
                metisService = TestBed.inject(MetisService);
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
            expect(referenceLink).toBe(null);
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
            expect(referenceLink).not.toBe(null);
            expect(referenceLink.innerHTML).toInclude(referenceStr);

            // should display relevant icon for post
            const icon = getElement(debugElement, 'fa-icon');
            expect(icon).not.toBe(null);
            expect(icon.innerHTML).toInclude('fa fa-message');

            // on click should navigate to referenced post within current tab
            referenceLink.click();
            expect(navigateByUrlSpy).toHaveBeenCalledTimes(1);
            expect(openAttachmentSpy).toBeCalledTimes(0);
        });

        it('should contain a reference to file upload exercise with icon', () => {
            const referenceStr = 'File Upload Exercise';
            component.postingContentPart = {
                contentBeforeReference,
                linkToReference: ['/courses/1/exercises/30'],
                referenceStr,
                referenceType: ReferenceType.FILE_UPLOAD,
                contentAfterReference,
            } as PostingContentPart;
            fixture.detectChanges();

            // should display exercise name to user
            const referenceLink = getElement(debugElement, '.reference');
            expect(referenceLink).not.toBe(null);
            expect(referenceLink.innerHTML).toInclude(referenceStr);

            // should display relevant icon for file upload exercise
            const icon = getElement(debugElement, 'fa-icon');
            expect(icon).not.toBe(null);
            expect(icon.innerHTML).toInclude('fa fa-file-arrow-up');

            // on click should navigate to referenced file upload exercise within current tab
            referenceLink.click();
            expect(navigateByUrlSpy).toHaveBeenCalledTimes(1);
            expect(openAttachmentSpy).toBeCalledTimes(0);
        });

        it('should contain a reference to modeling exercise with icon', () => {
            const referenceStr = 'Modeling Exercise';
            component.postingContentPart = {
                contentBeforeReference,
                linkToReference: ['/courses/1/exercises/29'],
                referenceStr,
                referenceType: ReferenceType.MODELING,
                contentAfterReference,
            } as PostingContentPart;
            fixture.detectChanges();

            // should display exercise name to user
            const referenceLink = getElement(debugElement, '.reference');
            expect(referenceLink).not.toBe(null);
            expect(referenceLink.innerHTML).toInclude(referenceStr);

            // should display relevant icon for modeling exercise
            const icon = getElement(debugElement, 'fa-icon');
            expect(icon).not.toBe(null);
            expect(icon.innerHTML).toInclude('fa fa-diagram-project');

            // on click should navigate to referenced modeling exercise within current tab
            referenceLink.click();
            expect(navigateByUrlSpy).toHaveBeenCalledTimes(1);
            expect(openAttachmentSpy).toBeCalledTimes(0);
        });

        it('should contain a reference to quiz exercise with icon', () => {
            const referenceStr = 'Quiz Exercise';
            component.postingContentPart = {
                contentBeforeReference,
                linkToReference: ['/courses/1/exercises/61'],
                referenceStr,
                referenceType: ReferenceType.QUIZ,
                contentAfterReference,
            } as PostingContentPart;
            fixture.detectChanges();

            // should display exercise name to user
            const referenceLink = getElement(debugElement, '.reference');
            expect(referenceLink).not.toBe(null);
            expect(referenceLink.innerHTML).toInclude(referenceStr);

            // should display relevant icon for quiz exercise
            const icon = getElement(debugElement, 'fa-icon');
            expect(icon).not.toBe(null);
            expect(icon.innerHTML).toInclude('fa fa-check-double');

            // on click should navigate to referenced quiz exercise within current tab
            referenceLink.click();
            expect(navigateByUrlSpy).toHaveBeenCalledTimes(1);
            expect(openAttachmentSpy).toBeCalledTimes(0);
        });

        it('should contain a reference to programming exercise with icon', () => {
            const referenceStr = 'Programming Exercise';
            component.postingContentPart = {
                contentBeforeReference,
                linkToReference: ['/courses/1/exercises/53'],
                referenceStr,
                referenceType: ReferenceType.PROGRAMMING,
                contentAfterReference,
            } as PostingContentPart;
            fixture.detectChanges();

            // should display exercise name to user
            const referenceLink = getElement(debugElement, '.reference');
            expect(referenceLink).not.toBe(null);
            expect(referenceLink.innerHTML).toInclude(referenceStr);

            // should display relevant icon for programming exercise
            const icon = getElement(debugElement, 'fa-icon');
            expect(icon).not.toBe(null);
            expect(icon.innerHTML).toInclude('fa fa-keyboard');

            // on click should navigate to referenced programming exercise within current tab
            referenceLink.click();
            expect(navigateByUrlSpy).toHaveBeenCalledTimes(1);
            expect(openAttachmentSpy).toBeCalledTimes(0);
        });

        it('should contain a reference to text exercise with icon', () => {
            const referenceStr = 'Text Exercise';
            component.postingContentPart = {
                contentBeforeReference,
                linkToReference: ['/courses/1/exercises/28'],
                referenceStr,
                referenceType: ReferenceType.TEXT,
                contentAfterReference,
            } as PostingContentPart;
            fixture.detectChanges();

            // should display exercise name to user
            const referenceLink = getElement(debugElement, '.reference');
            expect(referenceLink).not.toBe(null);
            expect(referenceLink.innerHTML).toInclude(referenceStr);

            // should display relevant icon for test exercise
            const icon = getElement(debugElement, 'fa-icon');
            expect(icon).not.toBe(null);
            expect(icon.innerHTML).toInclude('fa fa-font');

            // on click should navigate to referenced text exercise within current tab
            referenceLink.click();
            expect(navigateByUrlSpy).toHaveBeenCalledTimes(1);
            expect(openAttachmentSpy).toBeCalledTimes(0);
        });

        it('should contain a reference to exercise with default icon', () => {
            const referenceStr = 'Text Exercise';
            component.postingContentPart = {
                contentBeforeReference,
                linkToReference: ['/courses/1/exercises/28'],
                referenceStr,
                referenceType: undefined,
                contentAfterReference,
            } as PostingContentPart;
            fixture.detectChanges();

            // should display exercise name to user
            const referenceLink = getElement(debugElement, '.reference');
            expect(referenceLink).not.toBe(null);
            expect(referenceLink.innerHTML).toInclude(referenceStr);

            // should display relevant icon for exercise
            const icon = getElement(debugElement, 'fa-icon');
            expect(icon).not.toBe(null);
            expect(icon.innerHTML).toInclude('fa fa-paperclip');

            // on click should navigate to referenced exercise within current tab
            referenceLink.click();
            expect(navigateByUrlSpy).toHaveBeenCalledTimes(1);
            expect(openAttachmentSpy).toBeCalledTimes(0);
        });

        it('should contain a reference to lecture with icon', () => {
            const referenceStr = 'Test Lecture';
            component.postingContentPart = {
                contentBeforeReference,
                linkToReference: ['/courses/1/lectures/1/'],
                referenceStr,
                referenceType: ReferenceType.LECTURE,
                contentAfterReference,
            } as PostingContentPart;
            fixture.detectChanges();

            // should display lecture name to user
            const referenceLink = getElement(debugElement, '.reference');
            expect(referenceLink).not.toBe(null);
            expect(referenceLink.innerHTML).toInclude(referenceStr);

            // should display relevant icon for lecture
            const icon = getElement(debugElement, 'fa-icon');
            expect(icon).not.toBe(null);
            expect(icon.innerHTML).toInclude('fa fa-chalkboard-user');

            // on click should navigate to referenced lecture within current tab
            referenceLink.click();
            expect(navigateByUrlSpy).toHaveBeenCalledTimes(1);
            expect(openAttachmentSpy).toBeCalledTimes(0);
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
            expect(referenceLink).not.toBe(null);
            expect(referenceLink.innerHTML).toInclude(referenceStr);

            // should display relevant icon for attachment
            const icon = getElement(debugElement, 'fa-icon');
            expect(icon).not.toBe(null);
            expect(icon.innerHTML).toInclude('fa fa-file');

            // on click should open referenced attachment within new tab
            referenceLink.click();
            expect(openAttachmentSpy).toBeCalledTimes(1);
            expect(openAttachmentSpy).toBeCalledWith(attachmentURL);
        });
    });
});
