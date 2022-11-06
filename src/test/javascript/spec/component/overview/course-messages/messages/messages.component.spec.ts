import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { PostingThreadComponent } from 'app/shared/metis/posting-thread/posting-thread.component';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MetisService } from 'app/shared/metis/metis.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { PostService } from 'app/shared/metis/post.service';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';

import { MockExerciseService } from '../../../../helpers/mocks/service/mock-exercise.service';
import { conversationsOfUser1, messagesBetweenUser1User2, metisCourse } from '../../../../helpers/sample/metis-sample-data';
import { MockAnswerPostService } from '../../../../helpers/mocks/service/mock-answer-post.service';
import { MockPostService } from '../../../../helpers/mocks/service/mock-post.service';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { MockRouter } from '../../../../helpers/mocks/mock-router';
import { MockLocalStorageService } from '../../../../helpers/mocks/service/mock-local-storage.service';
import { ConversationMessagesComponent } from 'app/overview/course-conversations/conversation-messages/conversation-messages.component';
import { MessageInlineInputComponent } from 'app/shared/metis/message/message-inline-input/message-inline-input.component';
import { getElement } from '../../../../helpers/utils/general.utils';

describe('MessagesComponent', () => {
    let component: ConversationMessagesComponent;
    let fixture: ComponentFixture<ConversationMessagesComponent>;
    let courseManagementService: CourseManagementService;
    let metisService: MetisService;
    let metisServiceGetFilteredPostsSpy: jest.SpyInstance;
    let fetchNextPageSpy: jest.SpyInstance;
    let scrollToBottomOfMessagesSpy: jest.SpyInstance;

    const id = metisCourse.id;
    const parentRoute = {
        parent: {
            params: of({ id }),
            queryParams: of({ searchText: '' }),
        },
    } as any as ActivatedRoute;
    const route = { parent: parentRoute } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, MockModule(FormsModule), MockModule(ReactiveFormsModule)],
            declarations: [
                ConversationMessagesComponent,
                MockComponent(PostingThreadComponent),
                MockComponent(PostCreateEditModalComponent),
                MockComponent(MessageInlineInputComponent),
                MockComponent(FaIconComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
                MockComponent(ButtonComponent),
                MockComponent(ItemCountComponent),
            ],
            providers: [
                FormBuilder,
                MockProvider(SessionStorageService),
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: PostService, useClass: MockPostService },
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: MetisService, useClass: MetisService },
            ],
        })
            .compileComponents()
            .then(() => {
                courseManagementService = TestBed.inject(CourseManagementService);
                jest.spyOn(courseManagementService, 'findOneForDashboard').mockReturnValue(of({ body: metisCourse }) as Observable<HttpResponse<Course>>);
                fixture = TestBed.createComponent(ConversationMessagesComponent);
                component = fixture.componentInstance;
                metisService = fixture.debugElement.injector.get(MetisService);
                metisServiceGetFilteredPostsSpy = jest.spyOn(metisService, 'getFilteredPosts');
                fetchNextPageSpy = jest.spyOn(component, 'fetchNextPage');
                scrollToBottomOfMessagesSpy = jest.spyOn(component, 'scrollToBottomOfMessages');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.isCourseMessagesPage).toBeTrue();
    });

    it('if user has no conversation, no call should be made to fetch posts', fakeAsync(() => {
        component.itemsPerPage = 5;
        component.isCourseMessagesPage = true;
        component.ngOnInit();
        tick();
        fixture.detectChanges();

        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(0);
    }));

    it('should call fetchNextPage at course messages when scrolled to top and do nothing when scrolled to bottom', fakeAsync(() => {
        component.itemsPerPage = 5;
        component.isCourseMessagesPage = true;
        component.ngOnInit();
        tick();
        fixture.detectChanges();

        const scrollableDiv = getElement(fixture.debugElement, 'div[id=scrollableDiv]');
        scrollableDiv.dispatchEvent(new Event('scrolled'));
        expect(fetchNextPageSpy).toHaveBeenCalledTimes(0);

        scrollableDiv.dispatchEvent(new Event('scrolledUp'));
        expect(fetchNextPageSpy).toHaveBeenCalledOnce();
    }));

    it('if user has conversation, posts should be fetched on page load and displayed in reversed order', fakeAsync(() => {
        initializeFixtureForCourseMessagesPage();

        component.conversation = conversationsOfUser1.first();
        component.onSearch();
        expect(component.posts).toEqual(messagesBetweenUser1User2.slice().reverse());
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledOnce();
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith(component.currentPostContextFilter);
    }));

    it('should fetch posts on activeConversation input outside of the component', fakeAsync(() => {
        initializeFixtureForCourseMessagesPage();
        component.activeConversation = conversationsOfUser1.first()!;
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledOnce();
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith({ ...component.currentPostContextFilter, page: 0 });
        expect(component.conversation).toBe(conversationsOfUser1.first());
    }));

    it('should auto scroll to the bottom of messages on init or if last message is displayed', fakeAsync(() => {
        initializeFixtureForCourseMessagesPage();
        component.conversation = conversationsOfUser1.first();
        component.onSearch();

        tick();
        fixture.detectChanges();

        expect(scrollToBottomOfMessagesSpy).toHaveBeenCalledOnce();
    }));

    function initializeFixtureForCourseMessagesPage() {
        component.itemsPerPage = 5;
        component.isCourseMessagesPage = true;

        tick();
        fixture.detectChanges();
    }
});
