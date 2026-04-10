import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { DisplayPriority } from 'app/communication/metis.util';
import { PostingDirective } from 'app/communication/directive/posting.directive';
import { MetisService } from 'app/communication/service/metis.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { of } from 'rxjs';
import { Router } from '@angular/router';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockProvider } from 'ng-mocks';
import { User } from 'app/core/user/user.model';
import * as courseModel from 'app/core/course/shared/entities/course.model';
import { OneToOneChatService } from 'app/communication/conversations/service/one-to-one-chat.service';

class MockOneToOneChatService {
    createWithId = vi.fn().mockReturnValue(of({ body: { id: 1 } }));
    create = vi.fn().mockReturnValue(of({ body: { id: 1 } }));
}

class MockPosting implements Posting {
    id: number;
    content: string;
    author?: User;

    constructor(id: number, content: string, author: User) {
        this.id = id;
        this.content = content;
        this.author = author;
    }
}

class MockReactionsBar {
    editPosting = vi.fn();
    togglePin = vi.fn();
    deletePosting = vi.fn();
    forwardMessage = vi.fn();
    checkIfPinned = vi.fn().mockReturnValue(DisplayPriority.NONE);
    selectReaction = vi.fn();
}

@Component({
    template: `<div jhiPosting></div>`,
})
class TestPostingComponent extends PostingDirective<MockPosting> {
    reactionsBar: MockReactionsBar = new MockReactionsBar();

    get reactionsBarInstance() {
        return this.reactionsBar;
    }

    get reactionsBarGetter() {
        return this.reactionsBar;
    }
}

describe('PostingDirective', () => {
    setupTestBed({ zoneless: true });

    let component: TestPostingComponent;
    let fixture: ComponentFixture<TestPostingComponent>;
    let mockReactionsBar: MockReactionsBar;
    let mockMetisService: MetisService;
    let mockOneToOneChatService: OneToOneChatService;
    let mockMetisConversationService: MetisConversationService;
    let mockRouter: Router;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                MockProvider(MetisService),
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                { provide: OneToOneChatService, useClass: MockOneToOneChatService },
                { provide: Router, useValue: { navigate: vi.fn() } },
            ],
        });

        fixture = TestBed.createComponent(TestPostingComponent);
        component = fixture.componentInstance;
        mockReactionsBar = new MockReactionsBar();
        component.reactionsBar = mockReactionsBar;
        const user = new User();
        user.id = 123;
        component.posting.set(new MockPosting(123, 'Test content', user));
        fixture.componentRef.setInput('isCommunicationPage', false);
        fixture.componentRef.setInput('isThreadSidebar', false);
        fixture.detectChanges();

        mockMetisService = TestBed.inject(MetisService);
        const course = new Course();
        course.id = 1;
        mockMetisService.setCourse(course);
        mockMetisService.getCourse = vi.fn().mockReturnValue(course);
        mockOneToOneChatService = TestBed.inject(OneToOneChatService);
        mockMetisConversationService = TestBed.inject(MetisConversationService);
        mockRouter = TestBed.inject(Router);
    });

    afterEach(() => {
        // Clear any active timers to prevent test leaks
        if (component.deleteTimer) {
            clearTimeout(component.deleteTimer);
        }
        if (component.deleteInterval) {
            clearInterval(component.deleteInterval);
        }
        vi.clearAllMocks();
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('should initialize content on ngOnInit', () => {
        component.ngOnInit();
        expect(component.content).toBe('Test content');
    });

    it('should call editPosting on reactionsBar and hide dropdown', () => {
        component.showDropdown = true;
        component.editPosting();
        expect(mockReactionsBar.editPosting).toHaveBeenCalled();
        expect(component.showDropdown).toBe(false);
    });

    it('should call togglePin on reactionsBar and hide dropdown', () => {
        component.showDropdown = true;
        component.togglePin();
        expect(mockReactionsBar.togglePin).toHaveBeenCalled();
        expect(component.showDropdown).toBe(false);
    });

    it('should call deletePosting on reactionsBar and hide dropdown', () => {
        component.showDropdown = true;
        component.deletePost();
        expect(mockReactionsBar.deletePosting).toHaveBeenCalled();
        expect(component.showDropdown).toBe(false);
    });

    it('should return display priority from reactionsBar', () => {
        const priority = component.checkIfPinned();
        expect(mockReactionsBar.checkIfPinned).toHaveBeenCalled();
        expect(priority).toBe(DisplayPriority.NONE);
    });

    it('should call selectReaction on reactionsBar and hide reaction selector', () => {
        const event = { reaction: 'like' };
        component.showReactionSelector = true;
        component.selectReaction(event);
        expect(mockReactionsBar.selectReaction).toHaveBeenCalledWith(event);
        expect(component.showReactionSelector).toBe(false);
    });

    it('should add reaction and set click position', () => {
        const mouseEvent = new MouseEvent('click', {
            clientX: 100,
            clientY: 200,
        });
        const preventDefaultSpy = vi.spyOn(mouseEvent, 'preventDefault');
        component.addReaction(mouseEvent);
        expect(preventDefaultSpy).toHaveBeenCalled();
        expect(component.showDropdown).toBe(false);
        expect(component.clickPosition).toEqual({ x: 100, y: 200 });
        expect(component.showReactionSelector).toBe(true);
    });

    it('should toggle reaction selector visibility', () => {
        component.showReactionSelector = false;
        component.toggleEmojiSelect();
        expect(component.showReactionSelector).toBe(true);

        component.toggleEmojiSelect();
        expect(component.showReactionSelector).toBe(false);
    });

    it('should call markMessageAsUnread on metisService', () => {
        const markMessageAsUnreadSpy = vi.spyOn(mockMetisService, 'markMessageAsUnread');

        component.markMessageAsUnread();

        expect(markMessageAsUnreadSpy).toHaveBeenCalledWith(component.posting());
    });

    it('should not proceed in onUserNameClicked if author is not set', () => {
        const isMessagingEnabledSpy = vi.spyOn(courseModel, 'isMessagingEnabled').mockReturnValue(true);

        component.posting.set({ ...component.posting()!, author: undefined } as MockPosting);
        component.onUserNameClicked();

        expect(isMessagingEnabledSpy).not.toHaveBeenCalled();
    });

    it('should not proceed in onUserNameClicked if messaging is not enabled', () => {
        vi.spyOn(courseModel, 'isMessagingEnabled').mockReturnValue(false);
        const createOneToOneChatSpy = vi.spyOn(mockMetisConversationService, 'createOneToOneChatWithId');
        const createChatSpy = vi.spyOn(mockOneToOneChatService, 'createWithId');
        const navigateSpy = vi.spyOn(mockRouter, 'navigate');

        component.onUserNameClicked();

        expect(createOneToOneChatSpy).not.toHaveBeenCalled();
        expect(createChatSpy).not.toHaveBeenCalled();
        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should not proceed in onUserReferenceClicked if messaging is not enabled', () => {
        vi.spyOn(courseModel, 'isMessagingEnabled').mockReturnValue(false);
        const createOneToOneChatSpy = vi.spyOn(mockMetisConversationService, 'createOneToOneChat');
        const createChatSpy = vi.spyOn(mockOneToOneChatService, 'create');
        const navigateSpy = vi.spyOn(mockRouter, 'navigate');

        component.onUserReferenceClicked('test');

        expect(createOneToOneChatSpy).not.toHaveBeenCalled();
        expect(createChatSpy).not.toHaveBeenCalled();
        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should create one-to-one chat in onUserNameClicked when messaging is enabled', () => {
        vi.spyOn(courseModel, 'isMessagingEnabled').mockReturnValue(true);
        fixture.componentRef.setInput('isCommunicationPage', true);

        const createOneToOneChatIdSpy = vi.spyOn(mockMetisConversationService, 'createOneToOneChatWithId');
        const createWithIdSpy = vi.spyOn(mockOneToOneChatService, 'createWithId');

        component.onUserNameClicked();

        expect(createOneToOneChatIdSpy).toHaveBeenCalledWith(123);

        fixture.componentRef.setInput('isCommunicationPage', false);

        component.onUserNameClicked();

        expect(createWithIdSpy).toHaveBeenCalledWith(1, 123);
    });

    it('should create one-to-one chat in onUserReferenceClicked when messaging is enabled', () => {
        vi.spyOn(courseModel, 'isMessagingEnabled').mockReturnValue(true);
        fixture.componentRef.setInput('isCommunicationPage', true);

        const createOneToOneChatSpy = vi.spyOn(mockMetisConversationService, 'createOneToOneChat');
        const createSpy = vi.spyOn(mockOneToOneChatService, 'create');

        component.onUserReferenceClicked('test');

        expect(createOneToOneChatSpy).toHaveBeenCalledWith('test');

        fixture.componentRef.setInput('isCommunicationPage', false);

        component.onUserReferenceClicked('test');

        expect(createSpy).toHaveBeenCalledWith(1, 'test');
    });

    it('should set isDeleted to true when delete event is triggered', () => {
        component.onDeleteEvent(true);
        expect(component.isDeleted).toBe(true);
    });

    it('should set isDeleted to false when delete event is false', () => {
        component.onDeleteEvent(false);
        expect(component.isDeleted).toBe(false);
    });

    it('should clear existing delete timer and interval before setting up new ones', () => {
        const clearTimeoutSpy = vi.spyOn(global, 'clearTimeout');
        const clearIntervalSpy = vi.spyOn(global, 'clearInterval');

        component.deleteTimer = setTimeout(() => {}, 1000);
        component.deleteInterval = setInterval(() => {}, 1000);

        component.onDeleteEvent(true);

        expect(clearTimeoutSpy).toHaveBeenCalledOnce();
        expect(clearIntervalSpy).toHaveBeenCalledOnce();
    });

    it('should set delete timer to initial value when delete is true', () => {
        component.onDeleteEvent(true);
        expect(component.deleteTimerInSeconds).toBe(component.timeToDeleteInSeconds);
    });

    it('should call metisService.deletePost for regular post', () => {
        const deletePostSpy = vi.spyOn(mockMetisService, 'deletePost');
        vi.useFakeTimers();

        component.isAnswerPost = false;
        component.onDeleteEvent(true);

        vi.runOnlyPendingTimers();

        expect(deletePostSpy).toHaveBeenCalledWith(component.posting());
    });

    it('should call metisService.deleteAnswerPost for answer post', () => {
        const deleteAnswerPostSpy = vi.spyOn(mockMetisService, 'deleteAnswerPost');
        vi.useFakeTimers();

        component.isAnswerPost = true;
        component.onDeleteEvent(true);

        vi.runOnlyPendingTimers();

        expect(deleteAnswerPostSpy).toHaveBeenCalledWith(component.posting());
    });

    it('should set up interval to decrement delete timer', () => {
        vi.useFakeTimers();

        component.onDeleteEvent(true);

        vi.advanceTimersByTime(1000);
        expect(component.deleteTimerInSeconds).toBe(5);

        vi.advanceTimersByTime(1000);
        expect(component.deleteTimerInSeconds).toBe(4);
    });

    it('should stop timer at 0 when decrementing', () => {
        vi.useFakeTimers();

        component.onDeleteEvent(true);

        vi.advanceTimersByTime(7000);

        expect(component.deleteTimerInSeconds).toBe(0);
    });

    it('should do nothing if delete event is false', () => {
        const deletePostSpy = vi.spyOn(mockMetisService, 'deletePost');
        const deleteAnswerPostSpy = vi.spyOn(mockMetisService, 'deleteAnswerPost');

        component.onDeleteEvent(false);

        expect(deletePostSpy).not.toHaveBeenCalled();
        expect(deleteAnswerPostSpy).not.toHaveBeenCalled();
    });

    it('should call forwardMessage on reactionsBar', () => {
        component.forwardMessage();
        expect(mockReactionsBar.forwardMessage).toHaveBeenCalled();
    });
});
