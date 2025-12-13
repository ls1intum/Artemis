import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { runInInjectionContext } from '@angular/core';
import { MockComponent, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { Post } from 'app/communication/shared/entities/post.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Posting, PostingType } from 'app/communication/shared/entities/posting.model';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { PostingContentComponent } from 'app/communication/posting-content/posting-content.components';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { ForwardedMessageComponent } from 'app/communication/forwarded-message/forwarded-message.component';

describe('ForwardedMessageComponent', () => {
    let component: ForwardedMessageComponent;
    let fixture: ComponentFixture<ForwardedMessageComponent>;

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
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

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
        jest.spyOn(component, 'updateSourceName');
        jest.spyOn(component, 'getTodayFlag');

        fixture.componentRef.setInput('originalPostDetails', mockPost);
        fixture.detectChanges();

        await fixture.whenStable();

        expect(component.updateSourceName).toHaveBeenCalled();
        expect(component.getTodayFlag).toHaveBeenCalled();
        expect(component.postingIsOfToday).toBeFalse();
    });

    it('should set sourceName correctly for a channel post', () => {
        fixture.componentRef.setInput('originalPostDetails', mockPost);
        fixture.detectChanges();
        expect(component.sourceName).toBe('#general |');
    });

    it('should set sourceName correctly for a one-to-one chat post when isAnswerPost is false', () => {
        const oneToOnePost: Post = {
            id: 3,
            conversation: { type: 'oneToOneChat' } as any,
        } as Post;

        fixture.componentRef.setInput('originalPostDetails', oneToOnePost);
        fixture.detectChanges();
        expect(component.sourceName).toBe('a direct message ');
    });

    it('should set sourceName correctly for a one-to-one chat post when isAnswerPost is true', () => {
        const oneToOneAnswerPost: AnswerPost = {
            id: 4,
            post: { ...mockPost, conversation: { type: 'oneToOneChat' } as any },
        } as AnswerPost;

        fixture.componentRef.setInput('originalPostDetails', oneToOneAnswerPost);
        fixture.detectChanges();
        expect(component.sourceName).toBe('a thread in a direct message ');
    });

    it('should set todayFlag to "artemisApp.metis.today" if post is created today', () => {
        fixture.componentRef.setInput('originalPostDetails', mockAnswerPost);
        fixture.detectChanges();
        expect(component.postingIsOfToday).toBeTrue();
        expect(component.todayFlag).toBe('artemisApp.metis.today');
    });

    it('should set todayFlag to undefined if post is not created today', () => {
        fixture.componentRef.setInput('originalPostDetails', mockPost);
        fixture.detectChanges();
        expect(component.postingIsOfToday).toBeFalse();
        expect(component.todayFlag).toBeUndefined();
    });

    it('should set sourceName correctly for a group chat post when isAnswerPost is true', () => {
        const groupChatAnswerPost: AnswerPost = {
            id: 5,
            post: { ...mockPost, conversation: { type: 'groupChat' } as any },
        } as AnswerPost;

        fixture.componentRef.setInput('originalPostDetails', groupChatAnswerPost);
        fixture.detectChanges();
        expect(component.sourceName).toBe('a thread in a group message ');
    });

    it('should set sourceName correctly for a group chat post when isAnswerPost is false', () => {
        const groupChatPost: Post = {
            id: 6,
            conversation: { type: 'groupChat', name: 'dev-team' } as any,
        } as Post;

        fixture.componentRef.setInput('originalPostDetails', groupChatPost);
        fixture.detectChanges();
        expect(component.sourceName).toBe('a group message ');
    });

    it('should set sourceName to "#unknown |" for channel post without name and isAnswerPost false', () => {
        const channelPostWithoutName: Post = {
            id: 7,
            conversation: { type: 'channel', name: undefined } as any,
        } as Post;

        fixture.componentRef.setInput('originalPostDetails', channelPostWithoutName);
        fixture.detectChanges();
        expect(component.sourceName).toBe('#unknown |');
    });

    it('should set sourceName correctly for an unknown conversation type when isAnswerPost is true', () => {
        const unknownTypeAnswerPost: AnswerPost = {
            id: 8,
            post: { ...mockPost, conversation: { type: 'unknownType' } as any },
        } as AnswerPost;

        fixture.componentRef.setInput('originalPostDetails', unknownTypeAnswerPost);
        fixture.detectChanges();
        expect(component.sourceName).toBe('a thread in a group message ');
    });

    it('should set sourceName correctly for an unknown conversation type when isAnswerPost is false', () => {
        const unknownTypePost: Post = {
            id: 9,
            conversation: { type: 'unknownType' } as any,
        } as Post;

        fixture.componentRef.setInput('originalPostDetails', unknownTypePost);
        fixture.detectChanges();
        expect(component.sourceName).toBe('a group message ');
    });

    it('should set sourceName to empty string when conversation is undefined', () => {
        const postWithoutConversation: Post = {
            id: 10,
            conversation: undefined,
        } as Post;

        fixture.componentRef.setInput('originalPostDetails', postWithoutConversation);
        fixture.detectChanges();
        expect(component.sourceName).toBe('');
    });

    it('should emit onNavigateToPost event when onTriggerNavigateToPost is called', () => {
        const spy = jest.spyOn(component.onNavigateToPost, 'emit');
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
        expect(component.sourceName).toBe('a thread in #general |');
    });

    it('should update sourceName correctly based on isAnswerPost flag (false case)', () => {
        const channelPost: Posting = {
            id: 11,
            conversation: { type: 'channel', name: 'general' } as any,
            postingType: PostingType.POST,
        };

        fixture.componentRef.setInput('originalPostDetails', channelPost);
        fixture.detectChanges();
        expect(component.sourceName).toBe('#general |');
    });

    it('should update sourceName correctly when updateSourceName is called manually', () => {
        const oneToOnePost: Post = {
            id: 13,
            conversation: { type: 'oneToOneChat' } as any,
        };

        fixture.componentRef.setInput('originalPostDetails', oneToOnePost);
        fixture.detectChanges();
        component.updateSourceName();
        expect(component.sourceName).toBe('a direct message ');
    });

    it('should handle missing conversation gracefully in updateSourceName', () => {
        const postWithoutConversation: Post = {
            id: 14,
            conversation: undefined,
        } as Post;

        fixture.componentRef.setInput('originalPostDetails', postWithoutConversation);
        fixture.detectChanges();
        expect(component.sourceName).toBe('');
    });

    it('should toggle showFullForwardedMessage when toggleShowFullForwardedMessage is called', () => {
        expect(component.showFullForwardedMessage).toBeFalse();
        component.toggleShowFullForwardedMessage();
        expect(component.showFullForwardedMessage).toBeTrue();
        component.toggleShowFullForwardedMessage();
        expect(component.showFullForwardedMessage).toBeFalse();
    });

    it('should set isContentLong to true if content overflows', () => {
        fixture.detectChanges();
        const messageContentDebugElement = fixture.debugElement.query(By.css('#messageContent'));

        expect(messageContentDebugElement).toBeTruthy();

        const messageContentElement = messageContentDebugElement.nativeElement;

        Object.defineProperty(messageContentElement, 'scrollHeight', { value: 200, configurable: true });
        Object.defineProperty(messageContentElement, 'clientHeight', { value: 100, configurable: true });

        component.checkIfContentOverflows();

        expect(component.isContentLong).toBeTrue();
    });

    it('should set isContentLong to false if content does not overflow', () => {
        fixture.detectChanges();

        const messageContentDebugElement = fixture.debugElement.query(By.css('#messageContent'));
        expect(messageContentDebugElement).toBeTruthy();

        const messageContentElement = messageContentDebugElement.nativeElement;

        Object.defineProperty(messageContentElement, 'scrollHeight', { value: 100, configurable: true });
        Object.defineProperty(messageContentElement, 'clientHeight', { value: 200, configurable: true });

        component.checkIfContentOverflows();

        expect(component.isContentLong).toBeFalse();
    });

    it('should call checkIfContentOverflows in ngAfterViewInit', fakeAsync(() => {
        const spy = jest.spyOn(component, 'checkIfContentOverflows');
        component.ngAfterViewInit();
        tick();
        expect(spy).toHaveBeenCalled();
    }));
});
