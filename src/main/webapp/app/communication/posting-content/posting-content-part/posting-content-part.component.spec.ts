import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement, input, runInInjectionContext } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { PostingContentPartComponent } from 'app/communication/posting-content/posting-content-part/posting-content-part.components';
import { PostingContentPart, ReferenceType } from 'app/communication/metis.util';
import { HtmlForPostingMarkdownPipe } from 'app/shared/pipes/html-for-posting-markdown.pipe';
import { getElement, getElements } from 'test/helpers/utils/general-test.utils';
import { MockQueryParamsDirective, MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockFileService } from 'test/helpers/mocks/service/mock-file.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { MockProvider } from 'ng-mocks';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { FileService } from 'app/shared/service/file.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('PostingContentPartComponent', () => {
    let component: PostingContentPartComponent;
    let fixture: ComponentFixture<PostingContentPartComponent>;
    let debugElement: DebugElement;
    let router: Router;
    let fileService: FileService;
    let openAttachmentSpy: jest.SpyInstance;
    let navigateByUrlSpy: jest.SpyInstance;
    let accountService: AccountService;

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
                { provide: AccountService, useClass: MockAccountService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(PostingContentPartComponent);
        component = fixture.componentInstance;
        debugElement = fixture.debugElement;
        router = TestBed.inject(Router);
        fileService = TestBed.inject(FileService);
        accountService = TestBed.inject(AccountService);
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

                // should display attachment video unit file name to user
                const referenceLink = getElement(debugElement, '.reference');
                expect(referenceLink).not.toBeNull();
                expect(referenceLink.innerHTML).toInclude(referenceStr);

                // on click should open referenced attachment video unit within new tab
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

                // should display attachment video unit slide name and link to user
                const referenceLink = getElement(debugElement, '.reference');
                expect(referenceLink).not.toBeNull();
                expect(referenceLink.innerHTML).toInclude(referenceStr);

                component.enlargeImage = jest.fn();

                const enlargeImageSpy = jest.spyOn(component, 'enlargeImage');

                // on click should open referenced attachment video unit slide
                referenceLink.click();
                expect(enlargeImageSpy).toHaveBeenCalledOnce();
                expect(enlargeImageSpy).toHaveBeenCalledWith(imageURL);
            });
        });

        it('should trigger userReferenceClicked event for different user logins', () => {
            accountService.userIdentity.set({ login: 'user1' } as User);
            const outputEmitter = jest.spyOn(component.userReferenceClicked, 'emit');

            component.onClickUserReference('user2');

            expect(outputEmitter).toHaveBeenCalledWith('user2');
        });

        it('should not trigger userReferenceClicked event for same user logins', () => {
            accountService.userIdentity.set({ login: 'user1' } as User);
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

    describe('Component internals', () => {
        it('should define the correct allowedHtmlTags', () => {
            const expectedTags = [
                'a',
                'b',
                'br',
                'blockquote',
                'code',
                'del',
                'em',
                'hr',
                'h1',
                'h2',
                'h3',
                'h4',
                'h5',
                'h6',
                'i',
                'ins',
                'li',
                'mark',
                'p',
                'pre',
                'small',
                's',
                'span',
                'strong',
                'sub',
                'sup',
                'ul',
                'ol',
            ];
            expect(component['allowedHtmlTags']).toEqual(expectedTags);
        });
    });

    describe('Content processing (spacing only)', () => {
        it('should process content and normalize excessive line breaks before and after reference', () => {
            const contentBefore = 'Line 1\n\n\nLine 2\n\n\n\nLine 3';
            const contentAfter = 'A\n\n\nB';
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

                expect(component.processedContentBeforeReference).toBe('Line 1\n\nLine 2\n\nLine 3');
                expect(component.processedContentAfterReference).toBe('A\n\nB');
            });
        });

        it('should not alter already correct line spacing', () => {
            const content = 'Paragraph one.\n\nParagraph two.\nLine continues.';
            const result = component.normalizeSpacing(content);
            expect(result).toBe(content);
        });

        it('should collapse 3 or more newlines to exactly 2', () => {
            const content = 'A\n\n\n\nB\n\n\n\n\nC';
            const expected = 'A\n\nB\n\nC';
            expect(component.normalizeSpacing(content)).toBe(expected);
        });

        it('should leave single linebreaks untouched', () => {
            const content = 'Line 1\nLine 2\nLine 3';
            expect(component.normalizeSpacing(content)).toBe(content);
        });

        it('should handle empty input gracefully', () => {
            expect(component.normalizeSpacing('')).toBe('');
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

    describe('Markdown structure rendering', () => {
        it('should render ordered and unordered lists', () => {
            const content = `1. First Number \n 2. Second number \n * First point \n * Second Point`;

            runInInjectionContext(fixture.debugElement.injector, () => {
                component.postingContentPart = input<PostingContentPart>({
                    contentBeforeReference: content,
                } as PostingContentPart);
                fixture.detectChanges();

                const olItems = debugElement.nativeElement.querySelectorAll('ol > li');
                const ulItems = debugElement.nativeElement.querySelectorAll('ul > li');

                expect(olItems).toHaveLength(2);
                expect(ulItems).toHaveLength(2);
            });
        });

        it('should render unordered lists with both possible inputs', () => {
            const content = `- First Input A \n - First Input B \n * Second Input A \n * Second Input B`;

            runInInjectionContext(fixture.debugElement.injector, () => {
                component.postingContentPart = input<PostingContentPart>({
                    contentBeforeReference: content,
                } as PostingContentPart);
                fixture.detectChanges();

                const ulItems = debugElement.nativeElement.querySelectorAll('ul > li');

                expect(ulItems).toHaveLength(4);
            });
        });

        it('should render bold and italic text correctly', () => {
            const content = '**bold** und *italic*';
            runInInjectionContext(fixture.debugElement.injector, () => {
                component.postingContentPart = input<PostingContentPart>({
                    contentBeforeReference: content,
                } as PostingContentPart);
                fixture.detectChanges();

                const strong = debugElement.nativeElement.querySelector('strong');
                const em = debugElement.nativeElement.querySelector('em');

                expect(strong).not.toBeNull();
                expect(strong.textContent).toBe('bold');
                expect(em).not.toBeNull();
                expect(em.textContent).toBe('italic');
            });
        });

        it('should render paragraphs', () => {
            const content = 'Paragraph One.\n\nParagraph Two.';
            runInInjectionContext(fixture.debugElement.injector, () => {
                component.postingContentPart = input<PostingContentPart>({
                    contentBeforeReference: content,
                } as PostingContentPart);
                fixture.detectChanges();

                const paragraphs = getElements(debugElement, '.markdown-preview p');
                expect(paragraphs).toHaveLength(2);
            });
        });

        it('should render single paragraph', () => {
            const content = 'Paragraph One.\nParagraph Two.';
            runInInjectionContext(fixture.debugElement.injector, () => {
                component.postingContentPart = input<PostingContentPart>({
                    contentBeforeReference: content,
                } as PostingContentPart);
                fixture.detectChanges();

                const paragraphs = getElements(debugElement, '.markdown-preview p');
                expect(paragraphs).toHaveLength(1);
            });
        });

        it('should render multiple markdown elements from full example', () => {
            const content = `**Have a good day** \n 1. Point 1\n 2. Point 2 \n * Point A \n * Point B \n \n A normal p element`;
            runInInjectionContext(fixture.debugElement.injector, () => {
                component.postingContentPart = input<PostingContentPart>({
                    contentBeforeReference: content,
                } as PostingContentPart);
                fixture.detectChanges();

                const boldText = debugElement.nativeElement.querySelector('strong');
                const olItems = debugElement.nativeElement.querySelectorAll('ol > li');
                const ulItems = debugElement.nativeElement.querySelectorAll('ul > li');
                const paragraphs = debugElement.nativeElement.querySelectorAll('p');

                expect(boldText).not.toBeNull();
                expect(olItems).toHaveLength(2);
                expect(ulItems).toHaveLength(2);
                expect(paragraphs.length).toBeGreaterThanOrEqual(2);
            });
        });
    });
});
