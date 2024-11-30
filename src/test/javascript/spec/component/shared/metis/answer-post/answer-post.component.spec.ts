import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AnswerPostComponent } from 'app/shared/metis/answer-post/answer-post.component';
import { DebugElement, input, runInInjectionContext } from '@angular/core';
import { MockComponent, MockModule, MockPipe, ngMocks } from 'ng-mocks';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { By } from '@angular/platform-browser';
import { AnswerPostHeaderComponent } from 'app/shared/metis/posting-header/answer-post-header/answer-post-header.component';
import { AnswerPostReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/answer-post-reactions-bar/answer-post-reactions-bar.component';
import { PostingContentComponent } from 'app/shared/metis/posting-content/posting-content.components';
import { metisResolvingAnswerPostUser1 } from '../../../../helpers/sample/metis-sample-data';
import { OverlayModule } from '@angular/cdk/overlay';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { DOCUMENT } from '@angular/common';
import { Reaction } from '../../../../../../../main/webapp/app/entities/metis/reaction.model';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { Posting, PostingType } from 'app/entities/metis/posting.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';

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
            imports: [OverlayModule, MockModule(BrowserAnimationsModule)],
            declarations: [
                AnswerPostComponent,
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(AnswerPostHeaderComponent),
                MockComponent(PostingContentComponent),
                MockComponent(AnswerPostCreateEditModalComponent),
                MockComponent(AnswerPostReactionsBarComponent),
            ],
            providers: [
                { provide: DOCUMENT, useValue: document },
                { provide: MetisService, useClass: MockMetisService },
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

    it('should contain an answer post header when isConsecutive is false', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.isConsecutive = input<boolean>(false);
            component.posting = metisResolvingAnswerPostUser1;
        });

        fixture.detectChanges();
        const header = debugElement.query(By.css('jhi-answer-post-header'));
        expect(header).not.toBeNull();
    });

    it('should not contain an answer post header when isConsecutive is true', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.isConsecutive = input<boolean>(true);
            component.posting = metisResolvingAnswerPostUser1;
        });

        fixture.detectChanges();
        const header = debugElement.query(By.css('jhi-answer-post-header'));
        expect(header).toBeNull();
    });

    it('should contain reference to container for rendering answerPostCreateEditModal component', () => {
        component.posting = metisResolvingAnswerPostUser1;

        fixture.detectChanges();
        expect(component.containerRef).not.toBeNull();
    });

    it('should contain component to edit answer post', () => {
        component.posting = metisResolvingAnswerPostUser1;

        fixture.detectChanges();
        const answerPostCreateEditModal = debugElement.query(By.css('jhi-answer-post-create-edit-modal'));
        expect(answerPostCreateEditModal).not.toBeNull();
    });

    it('should contain an answer post reactions bar', () => {
        component.posting = metisResolvingAnswerPostUser1;

        fixture.detectChanges();
        const reactionsBar = debugElement.query(By.css('jhi-answer-post-reactions-bar'));
        expect(reactionsBar).not.toBeNull();
    });

    it('should have correct content in posting-content component', () => {
        component.posting = metisResolvingAnswerPostUser1;

        fixture.detectChanges();
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
        fixture.detectChanges();

        expect(component.posting).toBeInstanceOf(AnswerPost);
        expect(spy).toHaveBeenCalled();
    });
});
