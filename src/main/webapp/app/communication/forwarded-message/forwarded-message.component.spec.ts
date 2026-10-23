import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { Post } from 'app/communication/shared/entities/post.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Posting, PostingType } from 'app/communication/shared/entities/posting.model';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { PostingContentComponent } from 'app/communication/posting-content/posting-content.components';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { ForwardedMessageComponent } from 'app/communication/forwarded-message/forwarded-message.component';
import { CommunicationService } from 'app/communication/service/communication.service';
import { MockCommunicationService } from 'test/helpers/mocks/service/mock-communication.service';

describe('ForwardedMessageComponent', () => {
    let component: ForwardedMessageComponent;
    let fixture: ComponentFixture<ForwardedMessageComponent>;
    let resizeObservers: { callback: ResizeObserverCallback; observer: ResizeObserver }[];

    const mockPost: Post = {
        id: 1,
        creationDate: dayjs().subtract(1, 'day'),
        conversation: { type: 'channel', name: 'general' } as any,
    } as Post;

    const mockAnswerPost: AnswerPost = {
        id: 2,
        creationDate: dayjs(),
        post: mockPost,
    } as AnswerPost;

    beforeEach(() => {
        vi.useFakeTimers();
        resizeObservers = [];
        vi.stubGlobal(
            'ResizeObserver',
            class implements ResizeObserver {
                observe = vi.fn();
                unobserve = vi.fn();
                disconnect = vi.fn();

                constructor(callback: ResizeObserverCallback) {
                    resizeObservers.push({ callback, observer: this });
                }
            },
        );
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                ForwardedMessageComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                FaIconComponent,
                MockComponent(ProfilePictureComponent),
                MockComponent(PostingContentComponent),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: CommunicationService, useClass: MockCommunicationService },
            ],
        }).overrideComponent(ForwardedMessageComponent, {
            remove: { imports: [ProfilePictureComponent, PostingContentComponent, ArtemisTranslatePipe, ArtemisDatePipe] },
            add: { imports: [MockComponent(ProfilePictureComponent), MockComponent(PostingContentComponent), MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe)] },
        });

        fixture = TestBed.createComponent(ForwardedMessageComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('originalPostDetails', {
            content: 'Test content',
        });
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should call updateSourceName and update todayFlag when originalPostDetails changes', async () => {
        vi.spyOn(component, 'updateSourceName');
        vi.spyOn(component, 'getTodayFlag');

        fixture.componentRef.setInput('originalPostDetails', mockPost);
        fixture.detectChanges();

        await fixture.whenStable();

        expect(component.updateSourceName).toHaveBeenCalled();
        expect(component.getTodayFlag).toHaveBeenCalled();
        expect(component.postingIsOfToday()).toBe(false);
    });

    it('should set sourceName correctly for a channel post', () => {
        fixture.componentRef.setInput('originalPostDetails', mockPost);
        fixture.detectChanges();
        expect(component.sourceName()).toBe('#general |');
    });

    it('should set sourceName correctly for a one-to-one chat post when isAnswerPost is false', () => {
        const oneToOnePost: Post = {
            id: 3,
            conversation: { type: 'oneToOneChat' } as any,
        } as Post;

        fixture.componentRef.setInput('originalPostDetails', oneToOnePost);
        fixture.detectChanges();
        expect(component.sourceName()).toBe('a direct message ');
    });

    it('should set sourceName correctly for a one-to-one chat post when isAnswerPost is true', () => {
        const oneToOneAnswerPost: AnswerPost = {
            id: 4,
            post: { ...mockPost, conversation: { type: 'oneToOneChat' } as any },
        } as AnswerPost;

        fixture.componentRef.setInput('originalPostDetails', oneToOneAnswerPost);
        fixture.detectChanges();
        expect(component.sourceName()).toBe('a thread in a direct message ');
    });

    it('should set todayFlag to "artemisApp.communication.today" if post is created today', () => {
        fixture.componentRef.setInput('originalPostDetails', mockAnswerPost);
        fixture.detectChanges();
        expect(component.postingIsOfToday()).toBe(true);
        expect(component.todayFlag()).toBe('artemisApp.communication.today');
    });

    it('should set todayFlag to undefined if post is not created today', () => {
        fixture.componentRef.setInput('originalPostDetails', mockPost);
        fixture.detectChanges();
        expect(component.postingIsOfToday()).toBe(false);
        expect(component.todayFlag()).toBeUndefined();
    });

    it('should set sourceName correctly for a group chat post when isAnswerPost is true', () => {
        const groupChatAnswerPost: AnswerPost = {
            id: 5,
            post: { ...mockPost, conversation: { type: 'groupChat' } as any },
        } as AnswerPost;

        fixture.componentRef.setInput('originalPostDetails', groupChatAnswerPost);
        fixture.detectChanges();
        expect(component.sourceName()).toBe('a thread in a group message ');
    });

    it('should set sourceName correctly for a group chat post when isAnswerPost is false', () => {
        const groupChatPost: Post = {
            id: 6,
            conversation: { type: 'groupChat', name: 'dev-team' } as any,
        } as Post;

        fixture.componentRef.setInput('originalPostDetails', groupChatPost);
        fixture.detectChanges();
        expect(component.sourceName()).toBe('a group message ');
    });

    it('should set sourceName to "#unknown |" for channel post without name and isAnswerPost false', () => {
        const channelPostWithoutName: Post = {
            id: 7,
            conversation: { type: 'channel', name: undefined } as any,
        } as Post;

        fixture.componentRef.setInput('originalPostDetails', channelPostWithoutName);
        fixture.detectChanges();
        expect(component.sourceName()).toBe('#unknown |');
    });

    it('should set sourceName correctly for an unknown conversation type when isAnswerPost is true', () => {
        const unknownTypeAnswerPost: AnswerPost = {
            id: 8,
            post: { ...mockPost, conversation: { type: 'unknownType' } as any },
        } as AnswerPost;

        fixture.componentRef.setInput('originalPostDetails', unknownTypeAnswerPost);
        fixture.detectChanges();
        expect(component.sourceName()).toBe('a thread in a group message ');
    });

    it('should set sourceName correctly for an unknown conversation type when isAnswerPost is false', () => {
        const unknownTypePost: Post = {
            id: 9,
            conversation: { type: 'unknownType' } as any,
        } as Post;

        fixture.componentRef.setInput('originalPostDetails', unknownTypePost);
        fixture.detectChanges();
        expect(component.sourceName()).toBe('a group message ');
    });

    it('should set sourceName to empty string when conversation is undefined', () => {
        const postWithoutConversation: Post = {
            id: 10,
            conversation: undefined,
        } as Post;

        fixture.componentRef.setInput('originalPostDetails', postWithoutConversation);
        fixture.detectChanges();
        expect(component.sourceName()).toBe('');
    });

    it('should emit onNavigateToPost event when onTriggerNavigateToPost is called', () => {
        const spy = vi.spyOn(component.onNavigateToPost, 'emit');
        fixture.componentRef.setInput('originalPostDetails', mockPost);
        component.onTriggerNavigateToPost();

        expect(spy).toHaveBeenCalledWith(mockPost);
    });

    it('should update sourceName correctly based on isAnswerPost flag (true case)', () => {
        const channelAnswerPost: AnswerPost = {
            id: 12,
            postingType: PostingType.ANSWER,
            conversation: { type: 'channel', name: 'general' } as any,
            post: mockPost,
        };

        fixture.componentRef.setInput('originalPostDetails', channelAnswerPost);
        fixture.detectChanges();
        expect(component.sourceName()).toBe('a thread in #general |');
    });

    it('should update sourceName correctly based on isAnswerPost flag (false case)', () => {
        const channelPost: Posting = {
            id: 11,
            conversation: { type: 'channel', name: 'general' } as any,
            postingType: PostingType.POST,
        };

        fixture.componentRef.setInput('originalPostDetails', channelPost);
        fixture.detectChanges();
        expect(component.sourceName()).toBe('#general |');
    });

    it('should update sourceName correctly when updateSourceName is called manually', () => {
        const oneToOnePost: Post = {
            id: 13,
            conversation: { type: 'oneToOneChat' } as any,
        };

        fixture.componentRef.setInput('originalPostDetails', oneToOnePost);
        fixture.detectChanges();
        component.updateSourceName();
        expect(component.sourceName()).toBe('a direct message ');
    });

    it('should handle missing conversation gracefully in updateSourceName', () => {
        const postWithoutConversation: Post = {
            id: 14,
            conversation: undefined,
        } as Post;

        fixture.componentRef.setInput('originalPostDetails', postWithoutConversation);
        fixture.detectChanges();
        expect(component.sourceName()).toBe('');
    });

    it('should toggle showFullForwardedMessage when toggleShowFullForwardedMessage is called', () => {
        expect(component.showFullForwardedMessage()).toBe(false);
        component.toggleShowFullForwardedMessage();
        expect(component.showFullForwardedMessage()).toBe(true);
        component.toggleShowFullForwardedMessage();
        expect(component.showFullForwardedMessage()).toBe(false);
    });

    it('should set isContentLong to true if content overflows', () => {
        fixture.detectChanges();
        const messageContentDebugElement = fixture.debugElement.query(By.css('#messageContent'));

        expect(messageContentDebugElement).toBeTruthy();

        const messageContentElement = messageContentDebugElement.nativeElement;

        Object.defineProperty(messageContentElement, 'scrollHeight', { value: 200, configurable: true });
        Object.defineProperty(messageContentElement, 'clientHeight', { value: 100, configurable: true });

        component.checkIfContentOverflows();

        expect(component.isContentLong()).toBe(true);
    });

    it('should set isContentLong to false if content does not overflow', () => {
        fixture.detectChanges();

        const messageContentDebugElement = fixture.debugElement.query(By.css('#messageContent'));
        expect(messageContentDebugElement).toBeTruthy();

        const messageContentElement = messageContentDebugElement.nativeElement;

        Object.defineProperty(messageContentElement, 'scrollHeight', { value: 100, configurable: true });
        Object.defineProperty(messageContentElement, 'clientHeight', { value: 200, configurable: true });

        component.checkIfContentOverflows();

        expect(component.isContentLong()).toBe(false);
    });

    it('should offer expansion when markdown finishes rendering after the initial height check', () => {
        fixture.detectChanges();
        vi.advanceTimersByTime(0);
        const body = fixture.nativeElement.querySelector('#messageContent');
        Object.defineProperty(body, 'scrollHeight', { value: 400, configurable: true });
        Object.defineProperty(body, 'clientHeight', { value: 100, configurable: true });

        for (const { callback, observer } of resizeObservers) {
            callback([], observer);
        }
        fixture.detectChanges();

        const expandButton = fixture.nativeElement.querySelector('button');
        expect(expandButton).not.toBeNull();
        expandButton.click();
        fixture.detectChanges();
        expect(body.classList.contains('expanded')).toBe(true);

        // Once expanded, the preview no longer overflows, but the collapse control must remain available.
        Object.defineProperty(body, 'clientHeight', { value: 400, configurable: true });
        for (const { callback, observer } of resizeObservers) {
            callback([], observer);
        }
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('button')).not.toBeNull();
        fixture.nativeElement.querySelector('button').click();
        fixture.detectChanges();
        expect(body.classList.contains('expanded')).toBe(false);
    });

    it('should detect long content when the forwarded source arrives after the component is initialized', () => {
        fixture.componentRef.setInput('originalPostDetails', undefined);
        fixture.detectChanges();
        vi.advanceTimersByTime(0);
        fixture.componentRef.setInput('originalPostDetails', { content: 'Long content arriving asynchronously' });
        fixture.detectChanges();
        const body = fixture.nativeElement.querySelector('#messageContent');
        Object.defineProperty(body, 'scrollHeight', { value: 400, configurable: true });
        Object.defineProperty(body, 'clientHeight', { value: 100, configurable: true });

        for (const { callback, observer } of resizeObservers) {
            callback([], observer);
        }
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('button')).not.toBeNull();
    });

    it('should keep expansion available when the forwarded source is replaced by content of the same size', () => {
        const source = { id: 17, content: 'Refetched long source' };
        fixture.componentRef.setInput('originalPostDetails', source);
        fixture.detectChanges();
        const body = fixture.nativeElement.querySelector('#messageContent');
        Object.defineProperty(body, 'scrollHeight', { value: 400, configurable: true });
        Object.defineProperty(body, 'clientHeight', { value: 100, configurable: true });
        for (const { callback, observer } of resizeObservers) {
            callback([], observer);
        }
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('button')).not.toBeNull();

        // The source can be refetched without changing either observed box's dimensions.
        fixture.componentRef.setInput('originalPostDetails', { ...source });
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('button')).not.toBeNull();
    });

    it.each([mockPost, mockAnswerPost])('preserves an expanded source when posting $id is refetched as a fresh object', (original) => {
        const source = { ...original, content: 'Long source content' };
        fixture.componentRef.setInput('originalPostDetails', source);
        fixture.detectChanges();
        const body = fixture.nativeElement.querySelector('#messageContent');
        Object.defineProperty(body, 'scrollHeight', { value: 400, configurable: true });
        Object.defineProperty(body, 'clientHeight', { value: 100, configurable: true });
        component.checkIfContentOverflows();
        component.toggleShowFullForwardedMessage();
        fixture.detectChanges();
        expect(component.showFullForwardedMessage()).toBe(true);

        fixture.componentRef.setInput('originalPostDetails', { ...source, content: 'Updated content from the same source' });
        fixture.detectChanges();

        expect(component.showFullForwardedMessage()).toBe(true);
        expect(component.isContentLong()).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="forwarded-message-expand"]').getAttribute('aria-expanded')).toBe('true');
        component.toggleShowFullForwardedMessage();
        fixture.detectChanges();
        expect(component.showFullForwardedMessage()).toBe(false);
    });

    it.each([
        ['a different post', { ...mockPost, id: 99 }],
        ['a reply with the same numeric id', { ...mockAnswerPost, id: mockPost.id }],
        ['a forwarded-only reply with the same numeric id', { id: mockPost.id, content: 'Shared reply', resolvesPost: false }],
        ['a missing source', undefined],
    ])('collapses the preview when the source changes to %s', (_description, replacement) => {
        fixture.componentRef.setInput('originalPostDetails', mockPost);
        fixture.detectChanges();
        component.toggleShowFullForwardedMessage();
        expect(component.showFullForwardedMessage()).toBe(true);

        fixture.componentRef.setInput('originalPostDetails', replacement);
        fixture.detectChanges();

        expect(component.showFullForwardedMessage()).toBe(false);
    });

    it('preserves expansion when the same reply is returned without its private parent', () => {
        fixture.componentRef.setInput('originalPostDetails', mockAnswerPost);
        fixture.detectChanges();
        component.toggleShowFullForwardedMessage();

        fixture.componentRef.setInput('originalPostDetails', { id: mockAnswerPost.id, content: 'Shared reply', resolvesPost: false });
        fixture.detectChanges();

        expect(component.showFullForwardedMessage()).toBe(true);
        expect(component.sourceName()).toBe('');
        expect(component['viewButtonVisible']()).toBe(false);
    });

    it('should stop observing forwarded content after the component is destroyed', () => {
        fixture.detectChanges();
        expect(resizeObservers.length).toBeGreaterThan(0);
        const { observer } = resizeObservers[0];
        fixture.destroy();

        expect(observer.disconnect).toHaveBeenCalledOnce();
    });

    it('allows expanding a long source that becomes available after an unavailable placeholder', () => {
        fixture.componentRef.setInput('originalPostDetails', undefined);
        fixture.componentRef.setInput('isSourceUnavailable', true);
        fixture.detectChanges();
        vi.advanceTimersByTime(0);
        expect(fixture.nativeElement.querySelector('[data-testid="forwarded-message-unavailable"]')).not.toBeNull();

        vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockReturnValue(200);
        vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(100);
        fixture.componentRef.setInput('originalPostDetails', { id: 1, content: 'A long forwarded message' });
        fixture.componentRef.setInput('isSourceUnavailable', false);
        fixture.detectChanges();
        vi.advanceTimersByTime(0);
        fixture.detectChanges();

        const expandButton: HTMLButtonElement | null = fixture.nativeElement.querySelector('button');
        expect(expandButton).not.toBeNull();
        expandButton!.click();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('#messageContent').classList.contains('expanded')).toBe(true);
    });
});
