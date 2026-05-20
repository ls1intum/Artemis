import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ConversationThreadSidebarComponent } from 'app/communication/course-conversations-components/layout/conversation-thread-sidebar/conversation-thread-sidebar.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { PostComponent } from 'app/communication/post/post.component';
import { MessageReplyInlineInputComponent } from 'app/communication/message/message-reply-inline-input/message-reply-inline-input.component';
import { Post } from 'app/communication/shared/entities/post.model';
import { post } from 'test/helpers/sample/metis-sample-data';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ComponentRef, signal } from '@angular/core';
import { TutorSuggestionComponent } from 'app/communication/course-conversations/tutor-suggestion/tutor-suggestion.component';
import { TranslateService } from '@ngx-translate/core';

describe('ConversationThreadSidebarComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ConversationThreadSidebarComponent;
    let fixture: ComponentFixture<ConversationThreadSidebarComponent>;
    let componentRef: ComponentRef<ConversationThreadSidebarComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [
                ConversationThreadSidebarComponent,
                FaIconComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(PostComponent),
                MockComponent(MessageReplyInlineInputComponent),
                MockDirective(TranslateDirective),
                MockComponent(TutorSuggestionComponent),
            ],
            providers: [{ provide: TranslateService, useValue: { instant: vi.fn((key: string) => key), get: vi.fn() } }],
        });
    });

    beforeEach(() => {
        TestBed.overrideComponent(ConversationThreadSidebarComponent, {
            remove: { imports: [PostComponent, MessageReplyInlineInputComponent, TutorSuggestionComponent, TranslateDirective] },
            add: {
                imports: [
                    MockComponent(PostComponent),
                    MockComponent(MessageReplyInlineInputComponent),
                    MockComponent(TutorSuggestionComponent),
                    MockDirective(TranslateDirective),
                ],
            },
        });
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
        fixture.componentRef.setInput('activePost', newPost);
        fixture.detectChanges();
        expect(component.createdAnswerPost).toBeDefined();
        expect(component.createdAnswerPost.content).toBe('');
        expect(component.createdAnswerPost.post).toEqual(newPost);
    });

    it.each([true, false])('should determine channel moderation rights based on active conversation', (hasModerationRights: boolean) => {
        const conversation = new ChannelDTO();
        conversation.hasChannelModerationRights = hasModerationRights;
        fixture.componentRef.setInput('activeConversation', conversation);
        fixture.detectChanges();
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
        const closeMock = vi.fn();

        component.expandTooltip = signal<NgbTooltip | undefined>({ close: closeMock } as unknown as NgbTooltip);

        expect(component.isExpanded).toBe(false);

        component.toggleExpand();
        expect(component.isExpanded).toBe(true);
        expect(closeMock).toHaveBeenCalledOnce();

        component.toggleExpand();
        expect(component.isExpanded).toBe(false);
        expect(closeMock).toHaveBeenCalledTimes(2);
    });
});
