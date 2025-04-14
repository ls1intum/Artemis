import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ConversationThreadSidebarComponent } from 'app/communication/course-conversations-components/layout/conversation-thread-sidebar/conversation-thread-sidebar.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { PostComponent } from 'app/communication/post/post.component';
import { MessageReplyInlineInputComponent } from 'app/communication/message/message-reply-inline-input/message-reply-inline-input.component';
import { Post } from 'app/communication/shared/entities/post.model';
import { post } from 'test/helpers/sample/metis-sample-data';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ComponentRef, runInInjectionContext, signal } from '@angular/core';
import { TutorSuggestionComponent } from 'app/communication/course-conversations/tutor-suggestion/tutor-suggestion.component';
import { AccountService } from 'app/core/auth/account.service';

describe('ConversationThreadSidebarComponent', () => {
    let component: ConversationThreadSidebarComponent;
    let fixture: ComponentFixture<ConversationThreadSidebarComponent>;
    let componentRef: ComponentRef<ConversationThreadSidebarComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            declarations: [
                ConversationThreadSidebarComponent,
                MockComponent(FaIconComponent),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(PostComponent),
                MockComponent(MessageReplyInlineInputComponent),
                MockDirective(TranslateDirective),
                MockComponent(TutorSuggestionComponent),
            ],
            providers: [MockProvider(AccountService)],
        }).compileComponents();

        TestBed.inject(AccountService);
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ConversationThreadSidebarComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
        componentRef.setInput('course', { id: 1 } as any);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should create empty default answer post', () => {
        const newPost = new Post();
        post.id = 1;
        component.activePost = newPost;
        expect(component.createdAnswerPost).toBeDefined();
        expect(component.createdAnswerPost.content).toBe('');
        expect(component.createdAnswerPost.post).toEqual(newPost);
    });

    it.each([true, false])('should determine channel moderation rights based on active conversation', (hasModerationRights: boolean) => {
        const conversation = new ChannelDTO();
        conversation.hasChannelModerationRights = hasModerationRights;
        component.activeConversation = conversation;
        expect(component.hasChannelModerationRights).toBe(hasModerationRights);
    });

    it('should set min and max width for the resizable thread section', () => {
        const expandedThreadElement = fixture.debugElement.nativeElement.querySelector('.expanded-thread');
        const minWidth = window.innerWidth * 0.3;
        const maxWidth = window.innerWidth;

        expandedThreadElement.style.width = `${minWidth}px`;
        expect(parseFloat(expandedThreadElement.style.width)).toBeGreaterThanOrEqual(minWidth);

        expandedThreadElement.style.width = `${maxWidth}px`;
        expect(parseFloat(expandedThreadElement.style.width)).toBeLessThanOrEqual(maxWidth);
    });

    it('should toggle isExpanded and call close() on expandTooltip signal when toggleExpand() is called', () => {
        const closeMock = jest.fn();

        runInInjectionContext(fixture.debugElement.injector, () => {
            component.expandTooltip = signal<NgbTooltip | undefined>({ close: closeMock } as unknown as NgbTooltip);
        });

        expect(component.isExpanded).toBeFalse();

        component.toggleExpand();
        expect(component.isExpanded).toBeTrue();
        expect(closeMock).toHaveBeenCalledOnce();

        component.toggleExpand();
        expect(component.isExpanded).toBeFalse();
        expect(closeMock).toHaveBeenCalledTimes(2);
    });

    it('should set isAtLeastTutor based on account service', fakeAsync(() => {
        const isAtLeastTutorInCourseSpy = jest.spyOn(TestBed.inject(AccountService), 'isAtLeastTutorInCourse').mockReturnValue(true);
        component.ngAfterViewInit();
        tick();
        expect(isAtLeastTutorInCourseSpy).toHaveBeenCalled();
    }));
});
