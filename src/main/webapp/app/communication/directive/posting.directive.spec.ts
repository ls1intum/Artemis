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
    createWithId = jest.fn().mockReturnValue(of({ body: { id: 1 } }));
    create = jest.fn().mockReturnValue(of({ body: { id: 1 } }));
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
    editPosting = jest.fn();
    togglePin = jest.fn();
    deletePosting = jest.fn();
    forwardMessage = jest.fn();
    checkIfPinned = jest.fn().mockReturnValue(DisplayPriority.NONE);
    selectReaction = jest.fn();
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
                { provide: Router, useValue: { navigate: jest.fn() } },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TestPostingComponent);
        component = fixture.componentInstance;
        jest.mock('app/core/course/shared/entities/course.model', () =>
            Object.assign({}, jest.requireActual('app/core/course/shared/entities/course.model'), { isMessagingEnabled: jest.fn() }),
        );
        mockReactionsBar = new MockReactionsBar();
        component.reactionsBar = mockReactionsBar;
        const user = new User();
        user.id = 123;
        component.posting = new MockPosting(123, 'Test content', user);
        component.isCommunicationPage = false;
        component.isThreadSidebar = false;
        fixture.detectChanges();

        mockMetisService = TestBed.inject(MetisService);
        const course = new Course();
        course.id = 1;
        mockMetisService.setCourse(course);
        mockMetisService.getCourse = jest.fn().mockReturnValue(course);
        mockOneToOneChatService = TestBed.inject(OneToOneChatService);
        mockMetisConversationService = TestBed.inject(MetisConversationService);
        mockRouter = TestBed.inject(Router);
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should initialize content on ngOnInit', () => {
        component.ngOnInit();
        expect(component.content).toBe('Test content');
    });

    it('should call editPosting on reactionsBar and hide dropdown', () => {
        component.showDropdown = true;
        component.editPosting();
        expect(mockReactionsBar.editPosting).toHaveBeenCalled();
        expect(component.showDropdown).toBeFalse();
    });

    it('should call togglePin on reactionsBar and hide dropdown', () => {
        component.showDropdown = true;
        component.togglePin();
        expect(mockReactionsBar.togglePin).toHaveBeenCalled();
        expect(component.showDropdown).toBeFalse();
    });

    it('should call deletePosting on reactionsBar and hide dropdown', () => {
        component.showDropdown = true;
        component.deletePost();
        expect(mockReactionsBar.deletePosting).toHaveBeenCalled();
        expect(component.showDropdown).toBeFalse();
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
        expect(component.showReactionSelector).toBeFalse();
    });

    it('should add reaction and set click position', () => {
        const mouseEvent = new MouseEvent('click', {
            clientX: 100,
            clientY: 200,
        });
        const preventDefaultSpy = jest.spyOn(mouseEvent, 'preventDefault');
        component.addReaction(mouseEvent);
        expect(preventDefaultSpy).toHaveBeenCalled();
        expect(component.showDropdown).toBeFalse();
        expect(component.clickPosition).toEqual({ x: 100, y: 200 });
        expect(component.showReactionSelector).toBeTrue();
    });

    it('should toggle reaction selector visibility', () => {
        component.showReactionSelector = false;
        component.toggleEmojiSelect();
        expect(component.showReactionSelector).toBeTrue();

        component.toggleEmojiSelect();
        expect(component.showReactionSelector).toBeFalse();
    });

    it('should not proceed in onUserNameClicked if author is not set', () => {
        const isMessagingEnabledSpy = jest.spyOn(courseModel, 'isMessagingEnabled').mockReturnValue(true);

        component.posting.author = undefined;
        component.onUserNameClicked();

        expect(isMessagingEnabledSpy).not.toHaveBeenCalled();
    });

    it('should not proceed in onUserNameClicked if messaging is not enabled', () => {
        jest.spyOn(courseModel, 'isMessagingEnabled').mockReturnValue(false);
        const createOneToOneChatSpy = jest.spyOn(mockMetisConversationService, 'createOneToOneChatWithId');
        const createChatSpy = jest.spyOn(mockOneToOneChatService, 'createWithId');
        const navigateSpy = jest.spyOn(mockRouter, 'navigate');

        component.onUserNameClicked();

        expect(createOneToOneChatSpy).not.toHaveBeenCalled();
        expect(createChatSpy).not.toHaveBeenCalled();
        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should not proceed in onUserReferenceClicked if messaging is not enabled', () => {
        jest.spyOn(courseModel, 'isMessagingEnabled').mockReturnValue(false);
        const createOneToOneChatSpy = jest.spyOn(mockMetisConversationService, 'createOneToOneChat');
        const createChatSpy = jest.spyOn(mockOneToOneChatService, 'create');
        const navigateSpy = jest.spyOn(mockRouter, 'navigate');

        component.onUserReferenceClicked('test');

        expect(createOneToOneChatSpy).not.toHaveBeenCalled();
        expect(createChatSpy).not.toHaveBeenCalled();
        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should create one-to-one chat in onUserNameClicked when messaging is enabled', () => {
        jest.spyOn(courseModel, 'isMessagingEnabled').mockReturnValue(true);
        component.isCommunicationPage = true;

        const createOneToOneChatIdSpy = jest.spyOn(mockMetisConversationService, 'createOneToOneChatWithId');
        const createWithIdSpy = jest.spyOn(mockOneToOneChatService, 'createWithId');

        component.onUserNameClicked();

        expect(createOneToOneChatIdSpy).toHaveBeenCalledWith(123);

        component.isCommunicationPage = false;

        component.onUserNameClicked();

        expect(createWithIdSpy).toHaveBeenCalledWith(1, 123);
    });

    it('should create one-to-one chat in onUserReferenceClicked when messaging is enabled', () => {
        jest.spyOn(courseModel, 'isMessagingEnabled').mockReturnValue(true);
        component.isCommunicationPage = true;

        const createOneToOneChatSpy = jest.spyOn(mockMetisConversationService, 'createOneToOneChat');
        const createSpy = jest.spyOn(mockOneToOneChatService, 'create');

        component.onUserReferenceClicked('test');

        expect(createOneToOneChatSpy).toHaveBeenCalledWith('test');

        component.isCommunicationPage = false;

        component.onUserReferenceClicked('test');

        expect(createSpy).toHaveBeenCalledWith(1, 'test');
    });

    it('should set isDeleted to true when delete event is triggered', () => {
        component.onDeleteEvent(true);
        expect(component.isDeleted).toBeTrue();
    });

    it('should set isDeleted to false when delete event is false', () => {
        component.onDeleteEvent(false);
        expect(component.isDeleted).toBeFalse();
    });

    it('should clear existing delete timer and interval before setting up new ones', () => {
        const clearTimeoutSpy = jest.spyOn(global, 'clearTimeout');
        const clearIntervalSpy = jest.spyOn(global, 'clearInterval');

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
        const deletePostSpy = jest.spyOn(mockMetisService, 'deletePost');
        jest.useFakeTimers();

        component.isAnswerPost = false;
        component.onDeleteEvent(true);

        jest.runOnlyPendingTimers();

        expect(deletePostSpy).toHaveBeenCalledWith(component.posting);
    });

    it('should call metisService.deleteAnswerPost for answer post', () => {
        const deleteAnswerPostSpy = jest.spyOn(mockMetisService, 'deleteAnswerPost');
        jest.useFakeTimers();

        component.isAnswerPost = true;
        component.onDeleteEvent(true);

        jest.runOnlyPendingTimers();

        expect(deleteAnswerPostSpy).toHaveBeenCalledWith(component.posting);
    });

    it('should set up interval to decrement delete timer', () => {
        jest.useFakeTimers();

        component.onDeleteEvent(true);

        jest.advanceTimersByTime(1000);
        expect(component.deleteTimerInSeconds).toBe(5);

        jest.advanceTimersByTime(1000);
        expect(component.deleteTimerInSeconds).toBe(4);
    });

    it('should stop timer at 0 when decrementing', () => {
        jest.useFakeTimers();

        component.onDeleteEvent(true);

        jest.advanceTimersByTime(7000);

        expect(component.deleteTimerInSeconds).toBe(0);

        jest.useRealTimers();
    });

    it('should do nothing if delete event is false', () => {
        const deletePostSpy = jest.spyOn(mockMetisService, 'deletePost');
        const deleteAnswerPostSpy = jest.spyOn(mockMetisService, 'deleteAnswerPost');

        component.onDeleteEvent(false);

        expect(deletePostSpy).not.toHaveBeenCalled();
        expect(deleteAnswerPostSpy).not.toHaveBeenCalled();
    });

    it('should call forwardMessage on reactionsBar', () => {
        component.forwardMessage();
        expect(mockReactionsBar.forwardMessage).toHaveBeenCalled();
    });
});
