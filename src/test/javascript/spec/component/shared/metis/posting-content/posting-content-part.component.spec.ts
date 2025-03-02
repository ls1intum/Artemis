import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement, input, runInInjectionContext } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { PostingContentPartComponent } from 'app/shared/metis/posting-content/posting-content-part/posting-content-part.components';
import { PostingContentPart, ReferenceType } from 'app/shared/metis/metis.util';
import { HtmlForPostingMarkdownPipe } from 'app/shared/pipes/html-for-posting-markdown.pipe';
import { getElement, getElements } from '../../../../helpers/utils/general.utils';
import { MockQueryParamsDirective, MockRouterLinkDirective } from '../../../../helpers/mocks/directive/mock-router-link.directive';
import { FileService } from 'app/shared/http/file.service';
import { MockFileService } from '../../../../helpers/mocks/service/mock-file.service';
import { MockRouter } from '../../../../helpers/mocks/mock-router';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { MockProvider } from 'ng-mocks';
import { MockActivatedRoute } from '../../../../helpers/mocks/activated-route/mock-activated-route';

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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                HtmlForPostingMarkdownPipe, // we want to test against the rendered string, therefore we cannot mock the pipe
                MockRouterLinkDirective,
                MockQueryParamsDirective,
            ],
            providers: [
                { provide: FileService, useClass: MockFileService },
                {
                    provide: Router,
                    useClass: MockRouter,
                },
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute(),
                },
                MockProvider(AccountService),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(PostingContentPartComponent);
        component = fixture.componentInstance;
        debugElement = fixture.debugElement;
        router = TestBed.inject(Router);
        fileService = TestBed.inject(FileService);
        navigateByUrlSpy = jest.spyOn(router, 'navigateByUrl');
        openAttachmentSpy = jest.spyOn(fileService, 'downloadFile');
        contentBeforeReference = '**Be aware**\n\n I want to reference the following Post ';
        contentAfterReference = 'in my content,\n\n does it *actually* work?';
    });

    describe('For posting without reference', () => {
        it('should not contain a reference but only markdown content', () => {
            const postingContent = 'I do not want to reference a Post.';
            runInInjectionContext(fixture.debugElement.injector, () => {
                component.postingContentPart = input<PostingContentPart>({
                    contentBeforeReference: postingContent,
                    linkToReference: undefined,
                    queryParams: undefined,
                    referenceStr: undefined,
                    contentAfterReference: undefined,
                } as PostingContentPart);
                fixture.detectChanges();
                const markdownRenderedTexts = getElements(debugElement, '.markdown-preview');
                expect(markdownRenderedTexts).toHaveLength(1);
                expect(markdownRenderedTexts![0].innerHTML).toBe('<p class="inline-paragraph">' + postingContent + '</p>');

                const referenceLink = getElement(debugElement, '.reference-hash');
                expect(referenceLink).toBeNull();
            });
        });
    });

    describe('For posting with reference', () => {
        it('should contain a post reference with icon and markdown content before and after', () => {
            const referenceStr = '#7';
            runInInjectionContext(fixture.debugElement.injector, () => {
                component.postingContentPart = input<PostingContentPart>({
                    contentBeforeReference,
                    linkToReference: ['/whatever'],
                    queryParams: { searchText: referenceStr },
                    referenceStr: '#7',
                    referenceType: ReferenceType.POST,
                    contentAfterReference,
                } as PostingContentPart);
                fixture.detectChanges();
                const markdownRenderedTexts = getElements(debugElement, '.markdown-preview');
                expect(markdownRenderedTexts).toHaveLength(2);
                // check that the paragraph right before the reference and the paragraph right after have the class `inline-paragraph`
                expect(markdownRenderedTexts![0].innerHTML).toInclude('<p><strong>Be aware</strong></p>');
                expect(markdownRenderedTexts![0].innerHTML).toInclude('<p class="inline-paragraph">I want to reference the following Post</p>'); // last paragraph before reference
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
            runInInjectionContext(fixture.debugElement.injector, () => {
                component.postingContentPart = input<PostingContentPart>({
                    contentBeforeReference,
                    linkToReference: [linkToReference],
                    referenceStr,
                    referenceType,
                    contentAfterReference,
                } as PostingContentPart);
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
        });

        it('should contain a reference to attachment with icon', () => {
            const referenceStr = 'Lecture 1 - Slide';
            const attachmentURL = '/api/core/files/attachments/lecture/1/Lecture-1.pdf';
            runInInjectionContext(fixture.debugElement.injector, () => {
                component.postingContentPart = input<PostingContentPart>({
                    contentBeforeReference,
                    referenceStr,
                    referenceType: ReferenceType.ATTACHMENT,
                    attachmentToReference: attachmentURL,
                    contentAfterReference,
                } as PostingContentPart);
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

        it('should contain a reference to lecture unit', () => {
            const referenceStr = 'Lecture Unit 1';
            const attachmentURL = '/api/core/files/attachments/attachment-unit/1/LectureUnit1.pdf';
            runInInjectionContext(fixture.debugElement.injector, () => {
                component.postingContentPart = input<PostingContentPart>({
                    contentBeforeReference,
                    referenceStr,
                    referenceType: ReferenceType.ATTACHMENT_UNITS,
                    attachmentToReference: attachmentURL,
                    contentAfterReference,
                } as PostingContentPart);
                fixture.detectChanges();

                // should display attachment unit file name to user
                const referenceLink = getElement(debugElement, '.reference');
                expect(referenceLink).not.toBeNull();
                expect(referenceLink.innerHTML).toInclude(referenceStr);

                // on click should open referenced attachment unit within new tab
                referenceLink.click();
                expect(openAttachmentSpy).toHaveBeenCalledOnce();
                expect(openAttachmentSpy).toHaveBeenCalledWith(attachmentURL);
            });
        });

        it('should contain a reference to lecture unit slide image', () => {
            const referenceStr = 'Lecture Unit1_SLIDE_1';
            const imageURL = '/api/core/files/attachments/slides/attachment-unit/1/AttachmentUnitSlide_2023-04-03T02-21-44-124_9ffe48ee.png';
            runInInjectionContext(fixture.debugElement.injector, () => {
                component.postingContentPart = input<PostingContentPart>({
                    referenceStr,
                    referenceType: ReferenceType.SLIDE,
                    slideToReference: imageURL,
                } as PostingContentPart);
                fixture.detectChanges();

                // should display attachment unit slide name and link to user
                const referenceLink = getElement(debugElement, '.reference');
                expect(referenceLink).not.toBeNull();
                expect(referenceLink.innerHTML).toInclude(referenceStr);

                component.enlargeImage = jest.fn();

                const enlargeImageSpy = jest.spyOn(component, 'enlargeImage');

                // on click should open referenced attachment unit slide
                referenceLink.click();
                expect(enlargeImageSpy).toHaveBeenCalledOnce();
                expect(enlargeImageSpy).toHaveBeenCalledWith(imageURL);
            });
        });

        it('should trigger userReferenceClicked event for different user logins', () => {
            const accountService = TestBed.inject(AccountService);
            jest.spyOn(accountService, 'userIdentity', 'get').mockReturnValue({ login: 'user1' } as User);
            const outputEmitter = jest.spyOn(component.userReferenceClicked, 'emit');

            component.onClickUserReference('user2');

            expect(outputEmitter).toHaveBeenCalledWith('user2');
        });

        it('should not trigger userReferenceClicked event for same user logins', () => {
            const accountService = TestBed.inject(AccountService);
            jest.spyOn(accountService, 'userIdentity', 'get').mockReturnValue({ login: 'user1' } as User);
            const outputEmitter = jest.spyOn(component.userReferenceClicked, 'emit');

            component.onClickUserReference('user1');

            expect(outputEmitter).not.toHaveBeenCalled();
        });

        it('should not trigger userReferenceClicked event if login is undefined', () => {
            const outputEmitter = jest.spyOn(component.userReferenceClicked, 'emit');

            component.onClickUserReference(undefined);

            expect(outputEmitter).not.toHaveBeenCalled();
        });

        it('should trigger channelReferencedClicked event if channel id is number', () => {
            const outputEmitter = jest.spyOn(component.channelReferenceClicked, 'emit');

            component.onClickChannelReference(1);

            expect(outputEmitter).toHaveBeenCalledWith(1);
        });

        it('should not trigger channelReferencedClicked event if channel id is undefined', () => {
            const outputEmitter = jest.spyOn(component.channelReferenceClicked, 'emit');

            component.onClickChannelReference(undefined);

            expect(outputEmitter).not.toHaveBeenCalled();
        });
    });

    describe('Content processing', () => {
        it('should process content before and after reference with escaped numbered and unordered lists', () => {
            const contentBefore = '1. This is a numbered list\n2. Another item\n- This is an unordered list';
            const contentAfter = '1. Numbered again\n- Unordered again';
            runInInjectionContext(fixture.debugElement.injector, () => {
                component.postingContentPart = input<PostingContentPart>({
                    contentBeforeReference: contentBefore,
                    contentAfterReference: contentAfter,
                    linkToReference: undefined,
                    queryParams: undefined,
                    referenceStr: undefined,
                } as PostingContentPart);
                fixture.detectChanges();

                component.processContent();

                expect(component.processedContentBeforeReference).toBe('1\\.  This is a numbered list\n2\\.  Another item\n\\- This is an unordered list');
                expect(component.processedContentAfterReference).toBe('1\\.  Numbered again\n\\- Unordered again');
            });
        });

        it('should escape numbered lists correctly', () => {
            const content = '1. First item\n2. Second item\n3. Third item';
            const escapedContent = component.escapeNumberedList(content);
            expect(escapedContent).toBe('1\\.  First item\n2\\.  Second item\n3\\.  Third item');
        });

        it('should escape unordered lists correctly', () => {
            const content = '- First item\n- Second item\n- Third item';
            const escapedContent = component.escapeUnorderedList(content);
            expect(escapedContent).toBe('\\- First item\n\\- Second item\n\\- Third item');
        });

        it('should not escape text without numbered or unordered lists', () => {
            const content = 'This is just a paragraph.\nAnother paragraph.';
            const escapedNumbered = component.escapeNumberedList(content);
            const escapedUnordered = component.escapeUnorderedList(content);
            expect(escapedNumbered).toBe(content);
            expect(escapedUnordered).toBe(content);
        });

        it('should handle mixed numbered and unordered lists in content', () => {
            const content = '1. Numbered item\n- Unordered item\n2. Another numbered item\n- Another unordered item';
            const escapedContent = component.escapeNumberedList(component.escapeUnorderedList(content));
            expect(escapedContent).toBe('1\\.  Numbered item\n\\- Unordered item\n2\\.  Another numbered item\n\\- Another unordered item');
        });
    });

    describe('PostingContentPart Reactivity', () => {
        it('should update display when postingContentPart changes', () => {
            runInInjectionContext(fixture.debugElement.injector, () => {
                component.postingContentPart = input<PostingContentPart>({
                    contentBeforeReference: 'Initial content',
                    linkToReference: undefined,
                    queryParams: undefined,
                    referenceStr: undefined,
                    contentAfterReference: undefined,
                } as PostingContentPart);
                fixture.detectChanges();

                const initialMarkdownElements = getElements(debugElement, '.markdown-preview');
                expect(initialMarkdownElements).toHaveLength(1);
                expect(initialMarkdownElements![0].innerHTML).toBe('<p class="inline-paragraph">Initial content</p>');

                fixture.componentRef.setInput('postingContentPart', {
                    contentBeforeReference: 'Updated content before',
                    linkToReference: ['/course/1'],
                    queryParams: { searchText: '#123' },
                    referenceStr: '#123',
                    referenceType: ReferenceType.POST,
                    contentAfterReference: 'Updated content after',
                } as PostingContentPart);
                fixture.detectChanges();

                const updatedMarkdownElements = getElements(debugElement, '.markdown-preview');
                expect(updatedMarkdownElements).toHaveLength(2);
                expect(updatedMarkdownElements![0].innerHTML).toBe('<p class="inline-paragraph">Updated content before</p>');
                expect(updatedMarkdownElements![1].innerHTML).toBe('<p class="inline-paragraph">Updated content after</p>');

                const referenceLink = getElement(debugElement, '.reference');
                expect(referenceLink).not.toBeNull();
                expect(referenceLink.innerHTML).toInclude('#123');

                const icon = getElement(debugElement, 'fa-icon');
                expect(icon).not.toBeNull();
                expect(icon.innerHTML).toInclude('fa fa-message');
            });
        });
    });
});
