import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { DebugElement } from '@angular/core';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { getElement } from '../../../../helpers/utils/general.utils';
import { PostFooterComponent } from 'app/shared/metis/posting-footer/post-footer/post-footer.component';
import { PostHeaderComponent } from 'app/shared/metis/posting-header/post-header/post-header.component';
import { PostingContentComponent } from 'app/shared/metis/posting-content/posting-content.components';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PageType } from 'app/shared/metis/metis.util';
import { TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { metisChannel, metisCourse, metisPostExerciseUser1, metisPostLectureUser1, metisPostTechSupport, metisUser1 } from '../../../../helpers/sample/metis-sample-data';
import { MockQueryParamsDirective, MockRouterLinkDirective } from '../../../../helpers/mocks/directive/mock-router-link.directive';
import { RouterTestingModule } from '@angular/router/testing';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { OneToOneChatService } from 'app/shared/metis/conversations/one-to-one-chat.service';
import { Router, RouterState } from '@angular/router';
import { of } from 'rxjs';
import { OneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { HttpResponse } from '@angular/common/http';
import { MockRouter } from '../../../../helpers/mocks/mock-router';
import { getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';

describe('PostComponent', () => {
    let component: PostComponent;
    let fixture: ComponentFixture<PostComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceGetLinkSpy: jest.SpyInstance;
    let metisServiceGetQueryParamsSpy: jest.SpyInstance;
    let metisServiceGetPageTypeStub: jest.SpyInstance;
    let router: MockRouter;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [RouterTestingModule, MockDirective(NgbTooltip)],
            providers: [
                { provide: MetisService, useClass: MockMetisService },
                { provide: Router, useClass: MockRouter },
                MockProvider(MetisConversationService),
                MockProvider(OneToOneChatService),
            ],
            declarations: [
                PostComponent,
                FaIconComponent, // we want to test the type of rendered icons, therefore we cannot mock the component
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(PostHeaderComponent),
                MockComponent(PostingContentComponent),
                MockComponent(PostFooterComponent),
                MockRouterLinkDirective,
                MockQueryParamsDirective,
                TranslatePipeMock,
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostComponent);
                metisService = TestBed.inject(MetisService);

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

    it('should display resolved icon on resolved post header', () => {
        component.posting = metisPostExerciseUser1;
        component.posting.resolved = true;

        component.ngOnInit();
        fixture.detectChanges();

        expect(getElement(debugElement, '.resolved')).not.toBeNull();
    });

    it('should not display resolved icon on unresolved post header', () => {
        // per default not resolved
        component.posting = metisPostExerciseUser1;
        component.posting.resolved = false;

        component.ngOnInit();
        fixture.detectChanges();

        expect(getElement(debugElement, '.resolved')).toBeNull();
    });

    it('should contain a post header', () => {
        const header = getElement(debugElement, 'jhi-post-header');
        expect(header).not.toBeNull();
    });

    it('should contain a title with referencable id', () => {
        component.isCommunicationPage = true;
        component.posting = metisPostExerciseUser1;
        component.ngOnInit();
        fixture.detectChanges();
        const title = getElement(debugElement, 'a.post-title');
        expect(title).toBeDefined();
        const idHash = getElement(debugElement, '.reference-hash');
        expect(idHash).toBeDefined();
        expect(idHash.innerHTML).toBe(`#${metisPostExerciseUser1.id}`);
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

    it('should have a course-wide context information shown as title prefix in course discussion overview', () => {
        metisServiceGetPageTypeStub.mockReturnValue(PageType.OVERVIEW);
        component.posting = metisPostTechSupport;
        component.ngOnInit();
        fixture.detectChanges();
        const context = getElement(fixture.debugElement, 'a.linked-context-information');
        expect(context).not.toBeNull();
        expect(component.contextInformation.routerLinkComponents).toEqual(expect.arrayContaining(['messages']));
    });

    it('should have a conversation context information shown as title prefix in course discussion overview', () => {
        metisServiceGetPageTypeStub.mockReturnValue(PageType.OVERVIEW);
        component.posting = metisPostLectureUser1;
        component.ngOnInit();
        fixture.detectChanges();
        const contextLink = getElement(fixture.debugElement, 'a.linked-context-information');
        expect(component.contextInformation.routerLinkComponents).toEqual(expect.arrayContaining(['messages']));
        expect(component.contextInformation.routerLinkComponents).toEqual(expect.arrayContaining([component.posting?.conversation?.course?.id]));
        expect(component.contextInformation.displayName).toBeDefined();
        expect(component.contextInformation.displayName).toEqual(getAsChannelDTO(component.posting?.conversation)?.name);
        expect(contextLink).not.toBeNull();
    });

    it('should contain the posting content', () => {
        component.posting = metisPostExerciseUser1;
        fixture.detectChanges();

        const header = getElement(debugElement, 'jhi-posting-content');
        expect(header).not.toBeNull();
    });

    it('should contain a post footer', () => {
        const footer = getElement(debugElement, 'jhi-post-footer');
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

    it('should create or navigate to oneToOneChat when not on messaging page', () => {
        const navigateSpy = jest.spyOn(router, 'navigate');
        const oneToOneChatService = TestBed.inject(OneToOneChatService);
        const createChatSpy = jest.spyOn(oneToOneChatService, 'create').mockReturnValue(of({ body: { id: 1 } } as HttpResponse<OneToOneChatDTO>));

        component.onUserReferenceClicked(metisUser1.login!);

        expect(navigateSpy).toHaveBeenCalledWith(['courses', metisCourse.id, 'messages'], {
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
        component.isCourseMessagesPage = true;

        component.onUserReferenceClicked(metisUser1.login!);

        expect(createOneToOneChatSpy).toHaveBeenCalledWith(metisUser1.login!);
    });

    it('should navigate to channel when not on messaging page', () => {
        const navigateSpy = jest.spyOn(router, 'navigate');

        component.onChannelReferenceClicked(metisChannel.id!);

        expect(navigateSpy).toHaveBeenCalledWith(['courses', metisCourse.id, 'messages'], {
            queryParams: {
                conversationId: metisChannel.id!,
            },
        });
    });

    it('should navigate to channel when on messaging page', () => {
        const metisConversationService = TestBed.inject(MetisConversationService);
        const setActiveConversationSpy = jest.fn();
        Object.defineProperty(metisConversationService, 'setActiveConversation', { value: setActiveConversationSpy });
        component.isCourseMessagesPage = true;

        component.onChannelReferenceClicked(metisChannel.id!);

        expect(setActiveConversationSpy).toHaveBeenCalledWith(metisChannel.id!);
    });
});
