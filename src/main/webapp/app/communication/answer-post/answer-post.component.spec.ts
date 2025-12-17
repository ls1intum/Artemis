import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AnswerPostComponent } from 'app/communication/answer-post/answer-post.component';
import { DebugElement } from '@angular/core';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockDirective, MockModule, MockPipe, ngMocks } from 'ng-mocks';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { By } from '@angular/platform-browser';
import { PostingContentComponent } from 'app/communication/posting-content/posting-content.components';
import { metisPostExerciseUser1, metisResolvingAnswerPostUser1, post } from 'test/helpers/sample/metis-sample-data';
import { OverlayModule } from '@angular/cdk/overlay';
import { AnswerPostCreateEditModalComponent } from 'app/communication/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { DOCUMENT } from '@angular/common';
import { Reaction } from 'app/communication/shared/entities/reaction.model';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MetisService } from 'app/communication/service/metis.service';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Posting, PostingType } from 'app/communication/shared/entities/posting.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { PostingHeaderComponent } from 'app/communication/posting-header/posting-header.component';
import dayjs from 'dayjs/esm';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('AnswerPostComponent', () => {
    let component: AnswerPostComponent;
    let fixture: ComponentFixture<AnswerPostComponent>;
    let debugElement: DebugElement;
    let mainContainer: HTMLElement;

    beforeEach(async () => {
        mainContainer = document.createElement('div');
        mainContainer.classList.add('thread-answer-post');
        document.body.appendChild(mainContainer);

        return TestBed.configureTestingModule({
            imports: [OverlayModule, MockModule(BrowserAnimationsModule), MockDirective(NgbTooltip)],
            declarations: [
                AnswerPostComponent,
                FaIconComponent,
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(PostingContentComponent),
                MockComponent(PostingHeaderComponent),
                MockComponent(AnswerPostCreateEditModalComponent),
                ArtemisDatePipe,
                ArtemisTranslatePipe,
                MockDirective(TranslateDirective),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: DOCUMENT, useValue: document },
                { provide: MetisService, useClass: MockMetisService },
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AnswerPostComponent);
                component = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should contain the posting header when isConsecutive is false', () => {
        fixture.componentRef.setInput('isConsecutive', false);
        component.posting = metisResolvingAnswerPostUser1;

        fixture.changeDetectorRef.detectChanges();
        const header = debugElement.query(By.css('jhi-posting-header'));
        expect(header).not.toBeNull();
    });

    it('should not contain the posting header when isConsecutive is true', () => {
        fixture.componentRef.setInput('isConsecutive', true);
        component.posting = metisResolvingAnswerPostUser1;

        fixture.changeDetectorRef.detectChanges();
        const header = debugElement.query(By.css('jhi-posting-header'));
        expect(header).toBeNull();
    });

    it('should contain reference to container for rendering answerPostCreateEditModal component', () => {
        component.posting = metisResolvingAnswerPostUser1;

        fixture.changeDetectorRef.detectChanges();
        expect(component.containerRef).not.toBeNull();
    });

    it('should contain component to edit answer post', () => {
        component.posting = metisResolvingAnswerPostUser1;

        fixture.changeDetectorRef.detectChanges();
        const answerPostCreateEditModal = debugElement.query(By.css('jhi-answer-post-create-edit-modal'));
        expect(answerPostCreateEditModal).not.toBeNull();
    });

    it('should contain an answer post reactions bar', () => {
        component.posting = metisResolvingAnswerPostUser1;

        fixture.changeDetectorRef.detectChanges();
        const reactionsBar = debugElement.query(By.css('jhi-posting-reactions-bar'));
        expect(reactionsBar).not.toBeNull();
    });

    it('should have correct content in posting-content component', () => {
        component.posting = metisResolvingAnswerPostUser1;

        fixture.changeDetectorRef.detectChanges();
        const postingContentDebugElement = debugElement.query(By.directive(PostingContentComponent));
        expect(postingContentDebugElement).not.toBeNull();
        const content = ngMocks.input(postingContentDebugElement, 'content');
        expect(content).toEqual(metisResolvingAnswerPostUser1.content);
    });

    it('should close previous dropdown when another is opened', () => {
        const previousComponent = {
            showDropdown: true,
            enableBodyScroll: jest.fn(),
            changeDetector: { detectChanges: jest.fn() },
        } as any as AnswerPostComponent;

        AnswerPostComponent.activeDropdownPost = previousComponent;

        const event = new MouseEvent('contextmenu', { clientX: 100, clientY: 200 });
        component.onRightClick(event);

        expect(previousComponent.showDropdown).toBeFalse();
        expect(previousComponent.enableBodyScroll).toHaveBeenCalled();
        expect(previousComponent.changeDetector.detectChanges).toHaveBeenCalled();
        expect(AnswerPostComponent.activeDropdownPost).toBe(component);
        expect(component.showDropdown).toBeTrue();
    });

    it('should handle click outside and hide dropdown', () => {
        component.showDropdown = true;
        const enableBodyScrollSpy = jest.spyOn(component, 'enableBodyScroll' as any);
        component.onClickOutside();
        expect(component.showDropdown).toBeFalse();
        expect(enableBodyScrollSpy).toHaveBeenCalled();
    });

    it('should disable body scroll', () => {
        const setStyleSpy = jest.spyOn(component.renderer, 'setStyle');
        (component as any).disableBodyScroll();
        expect(setStyleSpy).toHaveBeenCalledWith(expect.objectContaining({ className: 'thread-answer-post' }), 'overflow', 'hidden');
    });

    it('should enable body scroll', () => {
        const setStyleSpy = jest.spyOn(component.renderer, 'setStyle');
        (component as any).enableBodyScroll();
        expect(setStyleSpy).toHaveBeenCalledWith(expect.objectContaining({ className: 'thread-answer-post' }), 'overflow-y', 'auto');
    });

    it('should adjust dropdown position if it overflows the screen width', () => {
        const dropdownWidth = 200;
        const screenWidth = window.innerWidth;

        component.dropdownPosition = { x: screenWidth - 50, y: 100 };
        component.adjustDropdownPosition();

        expect(component.dropdownPosition.x).toBe(screenWidth - dropdownWidth - 10);
    });

    it('should not adjust dropdown position if it does not overflow the screen width', () => {
        const initialX = 100;
        component.dropdownPosition = { x: initialX, y: 100 };
        component.adjustDropdownPosition();

        expect(component.dropdownPosition.x).toBe(initialX);
    });

    it('should update the posting when onPostingUpdated is called', () => {
        const updatedPosting = { ...metisResolvingAnswerPostUser1, content: 'Updated content' };
        component.onPostingUpdated(updatedPosting);

        expect(component.posting).toEqual(updatedPosting);
    });

    it('should update reactions when onReactionsUpdated is called', () => {
        const updatedReactions = [{ id: 1, emojiId: 'smile', userId: 2 } as Reaction];
        component.onReactionsUpdated(updatedReactions);

        expect(component.posting.reactions).toEqual(updatedReactions);
    });

    it('should handle onRightClick correctly based on cursor style', () => {
        const testCases = [
            {
                cursor: 'pointer',
                preventDefaultCalled: false,
                showDropdown: false,
                dropdownPosition: { x: 0, y: 0 },
            },
            {
                cursor: 'default',
                preventDefaultCalled: true,
                showDropdown: true,
                dropdownPosition: { x: 100, y: 200 },
            },
        ];

        testCases.forEach(({ cursor, preventDefaultCalled, showDropdown, dropdownPosition }) => {
            const event = new MouseEvent('contextmenu', { clientX: 100, clientY: 200 });

            const targetElement = document.createElement('div');
            Object.defineProperty(event, 'target', { value: targetElement });

            jest.spyOn(window, 'getComputedStyle').mockReturnValue({
                cursor,
            } as CSSStyleDeclaration);

            const preventDefaultSpy = jest.spyOn(event, 'preventDefault');

            component.onRightClick(event);

            expect(preventDefaultSpy).toHaveBeenCalledTimes(preventDefaultCalled ? 1 : 0);
            expect(component.showDropdown).toBe(showDropdown);
            expect(component.dropdownPosition).toEqual(dropdownPosition);
        });
    });

    it('should cast the post to answer post on change', () => {
        const mockPost: Posting = {
            id: 1,
            author: {
                id: 1,
                name: 'Test Author',
                internal: false,
            },
            content: 'Test Content',
            postingType: PostingType.ANSWER,
        };
        // @ts-ignore method is private
        const spy = jest.spyOn(component, 'assignPostingToAnswerPost');
        component.posting = mockPost;
        fixture.changeDetectorRef.detectChanges();

        expect(component.posting).toBeInstanceOf(AnswerPost);
        expect(spy).toHaveBeenCalled();
    });

    it('should display post-time span when isConsecutive() returns true', () => {
        const fixedDate = dayjs('2024-12-06T23:39:27.080Z');
        component.posting = { ...metisPostExerciseUser1, creationDate: fixedDate };

        jest.spyOn(component, 'isConsecutive').mockReturnValue(true);
        fixture.changeDetectorRef.detectChanges();

        const postTimeDebugElement = debugElement.query(By.css('span.post-time'));
        const postTimeElement = postTimeDebugElement.nativeElement as HTMLElement;

        expect(postTimeDebugElement).toBeTruthy();

        const expectedTime = dayjs(fixedDate).format('HH:mm');
        expect(postTimeElement.textContent?.trim()).toBe(expectedTime);
    });

    it('should not display post-time span when isConsecutive() returns false', () => {
        const fixedDate = dayjs('2024-12-06T23:39:27.080Z');
        component.posting = { ...metisPostExerciseUser1, creationDate: fixedDate };

        jest.spyOn(component, 'isConsecutive').mockReturnValue(false);
        fixture.changeDetectorRef.detectChanges();

        const postTimeElement = debugElement.query(By.css('span.post-time'));
        expect(postTimeElement).toBeFalsy();
    });

    it('should display forwardMessage button and invoke forwardMessage function when clicked', () => {
        const forwardMessageSpy = jest.spyOn(component, 'forwardMessage');
        component.showDropdown = true;
        component.posting = post;
        fixture.changeDetectorRef.detectChanges();

        const forwardButton = debugElement.query(By.css('button.dropdown-item.d-flex.forward'));
        expect(forwardButton).not.toBeNull();

        forwardButton.nativeElement.click();
        expect(forwardMessageSpy).toHaveBeenCalled();
    });
});
