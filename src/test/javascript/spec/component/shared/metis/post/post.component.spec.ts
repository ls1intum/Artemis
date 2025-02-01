import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { DebugElement } from '@angular/core';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { getElement } from '../../../../helpers/utils/general.utils';
import { PostingFooterComponent } from 'app/shared/metis/posting-footer/posting-footer.component';
import { PostingHeaderComponent } from 'app/shared/metis/posting-header/posting-header.component';
import { PostingContentComponent } from 'app/shared/metis/posting-content/posting-content.components';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DisplayPriority, PageType } from 'app/shared/metis/metis.util';
import { MockTranslateService, TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { OverlayModule } from '@angular/cdk/overlay';
import {
    metisChannel,
    metisCourse,
    metisPostExerciseUser1,
    metisPostLectureUser1,
    metisUser1,
    post,
    sortedAnswerArray,
    unsortedAnswerArray,
} from '../../../../helpers/sample/metis-sample-data';
import { MockQueryParamsDirective, MockRouterLinkDirective } from '../../../../helpers/mocks/directive/mock-router-link.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { OneToOneChatService } from 'app/shared/metis/conversations/one-to-one-chat.service';
import { Router, RouterState, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { OneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { HttpResponse } from '@angular/common/http';
import { MockRouter } from '../../../../helpers/mocks/mock-router';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { DOCUMENT } from '@angular/common';
import { Posting, PostingType } from 'app/entities/metis/posting.model';
import { Post } from 'app/entities/metis/post.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../../helpers/mocks/service/mock-account.service';
import { MockLocalStorageService } from '../../../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';

describe('PostComponent', () => {
    let component: PostComponent;
    let fixture: ComponentFixture<PostComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceGetLinkSpy: jest.SpyInstance;
    let metisServiceGetQueryParamsSpy: jest.SpyInstance;
    let metisServiceGetPageTypeStub: jest.SpyInstance;
    let router: MockRouter;
    let mainContainer: HTMLElement;

    beforeEach(() => {
        mainContainer = document.createElement('div');
        mainContainer.classList.add('posting-infinite-scroll-container');
        document.body.appendChild(mainContainer);

        return TestBed.configureTestingModule({
            imports: [MockDirective(NgbTooltip), OverlayModule, MockModule(BrowserAnimationsModule)],
            providers: [
                provideRouter([]),
                { provide: MetisService, useClass: MockMetisService },
                { provide: Router, useClass: MockRouter },
                { provide: DOCUMENT, useValue: document },
                MockProvider(MetisConversationService),
                MockProvider(OneToOneChatService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
            ],
            declarations: [
                PostComponent,
                FaIconComponent, // we want to test the type of rendered icons, therefore we cannot mock the component
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(PostingHeaderComponent),
                MockComponent(PostingContentComponent),
                MockComponent(PostingFooterComponent),
                MockComponent(AnswerPostCreateEditModalComponent),
                MockRouterLinkDirective,
                MockQueryParamsDirective,
                TranslatePipeMock,
                ArtemisDatePipe,
                ArtemisTranslatePipe,
                MockDirective(TranslateDirective),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostComponent);
                metisService = TestBed.inject(MetisService);
                metisService.course = metisCourse;

                component = fixture.componentInstance;
                debugElement = fixture.debugElement;
                metisServiceGetPageTypeStub = jest.spyOn(metisService, 'getPageType');
                router = TestBed.inject<MockRouter>(Router as any);
                const mockRouterState = {
                    snapshot: {
                        root: { firstChild: {}, data: {} },
                    },
                } as RouterState;
                router.setRouterState(mockRouterState);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should sort answers', () => {
        component.posting = post;
        component.posting.answers = unsortedAnswerArray;
        component.sortAnswerPosts();
        expect(component.sortedAnswerPosts).toEqual(sortedAnswerArray);
    });

    it('should not sort empty array of answers', () => {
        component.posting = post;
        component.posting.answers = unsortedAnswerArray;
        component.posting.answers = undefined;
        component.sortAnswerPosts();
        expect(component.sortedAnswerPosts).toEqual([]);
    });

    it('should set router link and query params', () => {
        metisServiceGetLinkSpy = jest.spyOn(metisService, 'getLinkForPost');
        metisServiceGetQueryParamsSpy = jest.spyOn(metisService, 'getQueryParamsForPost');

        component.posting = metisPostExerciseUser1;
        component.ngOnChanges();

        expect(metisServiceGetLinkSpy).toHaveBeenCalled();
        expect(metisServiceGetQueryParamsSpy).toHaveBeenCalledWith(metisPostExerciseUser1);
        expect(component.routerLink).toEqual(['/courses', metisPostExerciseUser1.conversation?.course?.id, 'discussion']);
        expect(component.queryParams).toEqual({ searchText: '#' + metisPostExerciseUser1.id });
    });

    it('should initialize post without context information when shown in page section', () => {
        metisServiceGetPageTypeStub.mockReturnValue(PageType.PAGE_SECTION);
        component.posting = metisPostLectureUser1;
        component.ngOnInit();
        fixture.detectChanges();
        const contextLink = getElement(fixture.debugElement, 'a.linked-context-information');
        expect(contextLink).toBeNull();
        component.posting = metisPostExerciseUser1;
        component.ngOnChanges();
        fixture.detectChanges();
        const context = getElement(fixture.debugElement, 'span.context-information');
        expect(context).toBeNull();
    });

    it('should contain the posting content', () => {
        component.posting = metisPostExerciseUser1;
        fixture.detectChanges();

        const header = getElement(debugElement, 'jhi-posting-content');
        expect(header).not.toBeNull();
    });

    it('should contain a post footer', () => {
        const footer = getElement(debugElement, 'jhi-posting-footer');
        expect(footer).not.toBeNull();
    });

    it('should have correct content and title', () => {
        component.posting = metisPostExerciseUser1;
        component.ngOnInit();
        expect(component.content).toBe(metisPostExerciseUser1.content);
        expect(component.posting.title).toBe(metisPostExerciseUser1.title);
    });

    it('should open create answer post modal', () => {
        component.posting = metisPostExerciseUser1;
        component.ngOnInit();
        fixture.detectChanges();
        const postFooterOpenCreateAnswerPostModal = jest.spyOn(component.postFooterComponent, 'openCreateAnswerPostModal');
        component.openCreateAnswerPostModal();
        expect(postFooterOpenCreateAnswerPostModal).toHaveBeenCalledOnce();
    });

    it('should close create answer post modal', () => {
        component.posting = metisPostExerciseUser1;
        component.ngOnInit();
        fixture.detectChanges();
        const postFooterOpenCreateAnswerPostModal = jest.spyOn(component.postFooterComponent, 'closeCreateAnswerPostModal');
        component.closeCreateAnswerPostModal();
        expect(postFooterOpenCreateAnswerPostModal).toHaveBeenCalledOnce();
    });

    it('should create or navigate to oneToOneChat when not on messaging page', () => {
        const navigateSpy = jest.spyOn(router, 'navigate');
        const oneToOneChatService = TestBed.inject(OneToOneChatService);
        const createChatSpy = jest.spyOn(oneToOneChatService, 'create').mockReturnValue(of({ body: { id: 1 } } as HttpResponse<OneToOneChatDTO>));

        component.onUserReferenceClicked(metisUser1.login!);

        expect(navigateSpy).toHaveBeenCalledWith(['courses', metisCourse.id, 'communication'], {
            queryParams: {
                conversationId: 1,
            },
        });
        expect(createChatSpy).toHaveBeenCalledWith(metisCourse.id, metisUser1.login!);
    });

    it('should create or navigate to oneToOneChat when on messaging page', () => {
        const metisConversationService = TestBed.inject(MetisConversationService);
        const createOneToOneChatSpy = jest.fn().mockReturnValue(of({ body: { id: 1 } } as HttpResponse<OneToOneChatDTO>));
        Object.defineProperty(metisConversationService, 'createOneToOneChat', { value: createOneToOneChatSpy });
        component.isCommunicationPage = true;

        component.onUserReferenceClicked(metisUser1.login!);

        expect(createOneToOneChatSpy).toHaveBeenCalledWith(metisUser1.login!);
    });

    it('should navigate to channel when not on messaging page', () => {
        const navigateSpy = jest.spyOn(router, 'navigate');

        component.onChannelReferenceClicked(metisChannel.id!);

        expect(navigateSpy).toHaveBeenCalledWith(['courses', metisCourse.id, 'communication'], {
            queryParams: {
                conversationId: metisChannel.id!,
            },
        });
    });

    it('should navigate to channel when on messaging page', () => {
        const metisConversationService = TestBed.inject(MetisConversationService);
        const setActiveConversationSpy = jest.fn();
        Object.defineProperty(metisConversationService, 'setActiveConversation', { value: setActiveConversationSpy });
        component.isCommunicationPage = true;

        component.onChannelReferenceClicked(metisChannel.id!);

        expect(setActiveConversationSpy).toHaveBeenCalledWith(metisChannel.id!);
    });

    it('should set isDeleted to true', () => {
        component.onDeleteEvent(true);
        expect(component.isDeleted).toBeTrue();
    });

    it('should clear existing timers and intervals', () => {
        const clearTimeoutSpy = jest.spyOn(global, 'clearTimeout');
        const clearIntervalSpy = jest.spyOn(global, 'clearInterval');

        component.deleteTimer = setTimeout(() => {}, 1000);
        component.deleteInterval = setInterval(() => {}, 1000);
        component.onDeleteEvent(true);

        expect(clearIntervalSpy).toHaveBeenCalledOnce();
        expect(clearTimeoutSpy).toHaveBeenCalledOnce();
    });

    it('should clear existing timers and intervals on destroy', () => {
        const clearTimeoutSpy = jest.spyOn(global, 'clearTimeout');
        const clearIntervalSpy = jest.spyOn(global, 'clearInterval');

        component.deleteTimer = setTimeout(() => {}, 1000);
        component.deleteInterval = setInterval(() => {}, 1000);
        component.ngOnDestroy();

        expect(clearIntervalSpy).toHaveBeenCalledOnce();
        expect(clearTimeoutSpy).toHaveBeenCalledOnce();
    });

    it('should set deleteTimer and deleteInterval when isDelete is true', () => {
        component.onDeleteEvent(true);

        expect(component.deleteTimer).toBeDefined();
        expect(component.deleteInterval).toBeDefined();
        expect(component.deleteTimerInSeconds).toBe(component.timeToDeleteInSeconds);
    });

    it('should not set timers when isDelete is false', () => {
        component.onDeleteEvent(false);

        expect(component.deleteTimer).toBeUndefined();
        expect(component.deleteInterval).toBeUndefined();
    });

    it('should return true if the post is pinned', () => {
        component.posting = { ...post, displayPriority: DisplayPriority.PINNED };
        expect(component.isPinned()).toBeTrue();
    });

    it('should return false if the post is not pinned', () => {
        component.posting = { ...post, displayPriority: DisplayPriority.NONE };
        expect(component.isPinned()).toBeFalse();
    });

    it('should close previous dropdown when another is opened', () => {
        const previousComponent = {
            showDropdown: true,
            enableBodyScroll: jest.fn(),
            changeDetector: { detectChanges: jest.fn() },
        } as any as PostComponent;

        PostComponent.activeDropdownPost = previousComponent;

        const event = new MouseEvent('contextmenu', { clientX: 100, clientY: 200 });
        component.onRightClick(event);

        expect(previousComponent.showDropdown).toBeFalse();
        expect(previousComponent.enableBodyScroll).toHaveBeenCalled();
        expect(previousComponent.changeDetector.detectChanges).toHaveBeenCalled();
        expect(PostComponent.activeDropdownPost).toBe(component);
        expect(component.showDropdown).toBeTrue();
    });

    it('should disable body scroll', () => {
        const setStyleSpy = jest.spyOn(component.renderer, 'setStyle');
        (component as any).disableBodyScroll();
        expect(setStyleSpy).toHaveBeenCalledWith(expect.objectContaining({ className: 'posting-infinite-scroll-container' }), 'overflow', 'hidden');
    });

    it('should enable body scroll', () => {
        const setStyleSpy = jest.spyOn(component.renderer, 'setStyle');
        (component as any).enableBodyScroll();
        expect(setStyleSpy).toHaveBeenCalledWith(expect.objectContaining({ className: 'posting-infinite-scroll-container' }), 'overflow-y', 'auto');
    });

    it('should handle click outside and hide dropdown', () => {
        component.showDropdown = true;
        const enableBodyScrollSpy = jest.spyOn(component, 'enableBodyScroll' as any);
        component.onClickOutside();
        expect(component.showDropdown).toBeFalse();
        expect(enableBodyScrollSpy).toHaveBeenCalled();
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

            jest.restoreAllMocks();
        });
    });

    it('should cast the post to Post on change', () => {
        const mockPost: Posting = {
            id: 1,
            author: {
                id: 1,
                name: 'Test Author',
                internal: false,
            },
            content: 'Test Content',
            postingType: PostingType.POST,
        };
        // @ts-ignore method is private
        const spy = jest.spyOn(component, 'assignPostingToPost');
        component.posting = mockPost;
        fixture.detectChanges();

        expect(component.posting).toBeInstanceOf(Post);
        expect(spy).toHaveBeenCalled();
    });

    it('should display post-time span when isConsecutive() returns true', () => {
        const fixedDate = dayjs('2024-12-06T23:39:27.080Z');
        component.posting = { ...metisPostExerciseUser1, creationDate: fixedDate };

        jest.spyOn(component, 'isConsecutive').mockReturnValue(true);
        fixture.detectChanges();

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
        fixture.detectChanges();

        const postTimeElement = debugElement.query(By.css('span.post-time'));
        expect(postTimeElement).toBeFalsy();
    });
});
