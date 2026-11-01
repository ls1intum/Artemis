import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { PostingContentPartComponent } from 'app/communication/posting-content/posting-content-part/posting-content-part.components';
import { PostingContentPart, ReferenceType } from 'app/communication/communication.util';
import { getElement } from 'test/helpers/utils/general-test.utils';
import { MockQueryParamsDirective, MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockFileService } from 'test/helpers/mocks/service/mock-file.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { FileService } from 'app/foundation/service/file.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { DialogService } from 'primeng/dynamicdialog';
import { EnlargeSlideImageComponent } from 'app/communication/posting-content/enlarge-slide-image/enlarge-slide-image.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('PostingContentPartComponent', () => {
    let component: PostingContentPartComponent;
    let fixture: ComponentFixture<PostingContentPartComponent>;
    let debugElement: DebugElement;
    let router: Router;
    let fileService: FileService;
    let dialogService: DialogService;
    let openAttachmentSpy: ReturnType<typeof vi.spyOn>;
    let navigateByUrlSpy: ReturnType<typeof vi.spyOn>;
    let accountService: AccountService;

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(async () => {
        const mockDialogService = { open: vi.fn() } as unknown as DialogService;
        TestBed.configureTestingModule({
            imports: [MockRouterLinkDirective, MockQueryParamsDirective],
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
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            // DialogService is provided at the component level, so override it on the component for the mock to take effect.
            .overrideComponent(PostingContentPartComponent, {
                set: { providers: [{ provide: DialogService, useValue: mockDialogService }] },
            });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(PostingContentPartComponent);
        component = fixture.componentInstance;
        debugElement = fixture.debugElement;
        router = TestBed.inject(Router);
        fileService = TestBed.inject(FileService);
        accountService = TestBed.inject(AccountService);
        // DialogService is provided at the component level, so resolve it from the component's own injector.
        dialogService = fixture.debugElement.injector.get(DialogService);
        navigateByUrlSpy = vi.spyOn(router, 'navigateByUrl');
        openAttachmentSpy = vi.spyOn(fileService, 'downloadFile');
    });

    it.each([true, false])('only exposes actionable user references (own reference: %s)', (own) => {
        const ownLogin = 'own-user';
        vi.spyOn(accountService, 'userIdentity').mockReturnValue({ login: ownLogin } as User);
        fixture.componentRef.setInput('postingContentPart', {
            referenceType: ReferenceType.USER,
            referenceStr: '@user',
            queryParams: { referenceUserLogin: own ? ownLogin : 'other-user' },
        });
        fixture.detectChanges();
        const reference = fixture.nativeElement.querySelector('a.reference') as HTMLElement;
        const emitted = vi.spyOn(component.userReferenceClicked, 'emit');
        expect(reference.getAttribute('role')).toBe(own ? null : 'button');
        if (!own) {
            reference.dispatchEvent(new KeyboardEvent('keyup', { key: ' ', bubbles: true }));
            fixture.detectChanges();
            expect(emitted).toHaveBeenCalledExactlyOnceWith('other-user');
        }
        expect(reference.hasAttribute('role')).toBe(false);
        expect(reference.tabIndex).toBe(-1);
        emitted.mockClear();
        for (const [type, key] of [
            ['keydown', 'Enter'],
            ['keydown', ' '],
            ['keyup', ' '],
        ]) {
            const event = new KeyboardEvent(type, { key, bubbles: true, cancelable: true });
            reference.dispatchEvent(event);
            expect(event.defaultPrevented).toBe(false);
        }
        expect(emitted).not.toHaveBeenCalled();
    });

    it('removes the slide action when its image cannot load', () => {
        fixture.componentRef.setInput('postingContentPart', { slideToReference: '/slide.png', referenceStr: 'Slide 1' });
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[role="button"]')).not.toBeNull();
        fixture.nativeElement.querySelector('img').dispatchEvent(new Event('error'));
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('a.reference')).toBeNull();
        expect(fixture.nativeElement.querySelector('[jhiTranslate="artemisApp.markdownEditor.preview.slideNotFound"]')).not.toBeNull();
    });

    describe('For posting with reference', () => {
        it('should contain a post reference with icon', () => {
            const referenceStr = '#7';
            fixture.componentRef.setInput('postingContentPart', {
                linkToReference: ['/whatever'],
                queryParams: { searchText: referenceStr },
                referenceStr: '#7',
                referenceType: ReferenceType.POST,
            } as PostingContentPart);
            fixture.detectChanges();
            const referenceLink = getElement(debugElement, '.reference');
            expect(referenceLink).not.toBeNull();
            expect(referenceLink.innerHTML).toContain(referenceStr);

            const icon = getElement(debugElement, 'fa-icon');
            expect(icon).not.toBeNull();
            expect(icon.innerHTML).toContain('fa fa-message');

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
            fixture.componentRef.setInput('postingContentPart', {
                linkToReference: [linkToReference],
                referenceStr,
                referenceType,
            } as PostingContentPart);
            fixture.detectChanges();

            const referenceLink = getElement(debugElement, '.reference');
            expect(referenceLink).not.toBeNull();
            expect(referenceLink.innerHTML).toContain(referenceStr);

            const icon = getElement(debugElement, 'fa-icon');
            expect(icon).not.toBeNull();
            expect(icon.innerHTML).toContain(faIcon);

            referenceLink.click();
            expect(navigateByUrlSpy).toHaveBeenCalledOnce();
            expect(openAttachmentSpy).not.toHaveBeenCalled();
        });

        it('should contain a reference to attachment with icon', () => {
            const referenceStr = 'Lecture 1 - Slide';
            const attachmentURL = '/api/core/files/attachments/lectures/1/Lecture-1.pdf';
            fixture.componentRef.setInput('postingContentPart', {
                referenceStr,
                referenceType: ReferenceType.ATTACHMENT,
                attachmentToReference: attachmentURL,
            } as PostingContentPart);
            fixture.detectChanges();

            const referenceLink = getElement(debugElement, '.reference');
            expect(referenceLink).not.toBeNull();
            expect(referenceLink.innerHTML).toContain(referenceStr);

            const icon = getElement(debugElement, 'fa-icon');
            expect(icon).not.toBeNull();
            expect(icon.innerHTML).toContain('fa fa-file');

            referenceLink.click();
            expect(openAttachmentSpy).toHaveBeenCalledOnce();
            expect(openAttachmentSpy).toHaveBeenCalledWith(attachmentURL);
        });

        it('should contain a reference to lecture unit', () => {
            const referenceStr = 'Lecture Unit 1';
            const attachmentURL = '/api/core/files/attachments/attachment-video-units/1/LectureUnit1.pdf';
            fixture.componentRef.setInput('postingContentPart', {
                referenceStr,
                referenceType: ReferenceType.ATTACHMENT_UNITS,
                attachmentToReference: attachmentURL,
            } as PostingContentPart);
            fixture.detectChanges();

            const referenceLink = getElement(debugElement, '.reference');
            expect(referenceLink).not.toBeNull();
            expect(referenceLink.innerHTML).toContain(referenceStr);

            referenceLink.click();
            expect(openAttachmentSpy).toHaveBeenCalledOnce();
            expect(openAttachmentSpy).toHaveBeenCalledWith(attachmentURL);
        });

        it('should contain a reference to lecture unit slide image', () => {
            const referenceStr = 'Lecture Unit1_SLIDE_1';
            const imageURL = '/api/core/files/attachments/slides/attachment-unit/1/AttachmentUnitSlide_2023-04-03T02-21-44-124_9ffe48ee.png';
            fixture.componentRef.setInput('postingContentPart', {
                referenceStr,
                referenceType: ReferenceType.SLIDE,
                slideToReference: imageURL,
            } as PostingContentPart);
            fixture.detectChanges();

            const referenceLink = getElement(debugElement, '.reference');
            expect(referenceLink).not.toBeNull();
            expect(referenceLink.innerHTML).toContain(referenceStr);

            component.enlargeImage = vi.fn();

            const enlargeImageSpy = vi.spyOn(component, 'enlargeImage');

            referenceLink.click();
            expect(enlargeImageSpy).toHaveBeenCalledOnce();
            expect(enlargeImageSpy).toHaveBeenCalledWith(imageURL, referenceStr);
        });

        it('should open the enlarge slide image dialog with the correct config when enlargeImage is called', () => {
            const slideToReference = '/path/x.png';

            component.enlargeImage(slideToReference);

            expect(dialogService.open).toHaveBeenCalledOnce();
            expect(dialogService.open).toHaveBeenCalledWith(
                EnlargeSlideImageComponent,
                expect.objectContaining({
                    data: { slideToReference, imageAlt: 'artemisApp.communication.imagePreviewAlt' },
                    modal: true,
                    closable: true,
                    dismissableMask: true,
                    closeOnEscape: true,
                    header: 'artemisApp.communication.imagePreviewTitle',
                }),
            );
        });

        it('should pass the provided image description as alt text to the enlarge dialog', () => {
            component.enlargeImage('/path/x.png', 'A meaningful image description');

            expect(dialogService.open).toHaveBeenCalledWith(
                EnlargeSlideImageComponent,
                expect.objectContaining({
                    data: { slideToReference: '/path/x.png', imageAlt: 'A meaningful image description' },
                }),
            );
        });

        it('should trigger userReferenceClicked event for different user logins', () => {
            accountService.userIdentity.set({ login: 'user1' } as User);
            const outputEmitter = vi.spyOn(component.userReferenceClicked, 'emit');

            component.onClickUserReference('user2');

            expect(outputEmitter).toHaveBeenCalledWith('user2');
        });

        it('should not trigger userReferenceClicked event for same user logins', () => {
            accountService.userIdentity.set({ login: 'user1' } as User);
            const outputEmitter = vi.spyOn(component.userReferenceClicked, 'emit');

            component.onClickUserReference('user1');

            expect(outputEmitter).not.toHaveBeenCalled();
        });

        it('should not trigger userReferenceClicked event if login is undefined', () => {
            const outputEmitter = vi.spyOn(component.userReferenceClicked, 'emit');

            component.onClickUserReference(undefined);

            expect(outputEmitter).not.toHaveBeenCalled();
        });

        it('should trigger channelReferencedClicked event if channel id is number', () => {
            const outputEmitter = vi.spyOn(component.channelReferenceClicked, 'emit');

            component.onClickChannelReference(1);

            expect(outputEmitter).toHaveBeenCalledWith(1);
        });

        it('should not trigger channelReferencedClicked event if channel id is undefined', () => {
            const outputEmitter = vi.spyOn(component.channelReferenceClicked, 'emit');

            component.onClickChannelReference(undefined);

            expect(outputEmitter).not.toHaveBeenCalled();
        });
    });

    describe('PostingContentPart Reactivity', () => {
        it('should update display when postingContentPart changes', () => {
            fixture.componentRef.setInput('postingContentPart', {
                linkToReference: ['/course/1'],
                queryParams: { searchText: '#122' },
                referenceStr: '#122',
                referenceType: ReferenceType.POST,
            } as PostingContentPart);
            fixture.detectChanges();
            expect(getElement(debugElement, '.reference').innerHTML).toContain('#122');

            fixture.componentRef.setInput('postingContentPart', {
                linkToReference: ['/course/1'],
                queryParams: { searchText: '#123' },
                referenceStr: '#123',
                referenceType: ReferenceType.POST,
            } as PostingContentPart);
            fixture.detectChanges();

            const referenceLink = getElement(debugElement, '.reference');
            expect(referenceLink).not.toBeNull();
            expect(referenceLink.innerHTML).toContain('#123');
            expect(referenceLink.innerHTML).not.toContain('#122');

            const icon = getElement(debugElement, 'fa-icon');
            expect(icon).not.toBeNull();
            expect(icon.innerHTML).toContain('fa fa-message');
        });
    });
});
