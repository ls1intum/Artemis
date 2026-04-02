import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { MockComponent, MockModule, MockProvider } from 'ng-mocks';
import { MetisService } from 'app/communication/service/metis.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { MockExerciseService } from 'test/helpers/mocks/service/mock-exercise.service';
import { AnswerPostService } from 'app/communication/service/answer-post.service';
import { MockAnswerPostService } from 'test/helpers/mocks/service/mock-answer-post.service';
import { PostService } from 'app/communication/service/post.service';
import { MockPostService } from 'test/helpers/mocks/service/mock-post.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { DiscussionSectionComponent } from 'app/communication/shared/discussion-section/discussion-section.component';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { getElement, getElements } from 'test/helpers/utils/general-test.utils';
import {
    messagesBetweenUser1User2,
    metisCourse,
    metisExercise,
    metisExerciseChannelDTO,
    metisExercisePosts,
    metisLecture,
    metisLectureChannelDTO,
    metisPostTechSupport,
} from 'test/helpers/sample/metis-sample-data';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { PostContextFilter, SortDirection } from 'app/communication/metis.util';
import { Course, CourseInformationSharingConfiguration } from 'app/core/course/shared/entities/course.model';
import { User } from 'app/core/user/user.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Directive, input, output } from '@angular/core';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MockMetisConversationService } from '../../../../../../test/javascript/spec/helpers/mocks/service/mock-metis-conversation.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { PostingThreadComponent } from 'app/communication/posting-thread/posting-thread.component';
import { MessageInlineInputComponent } from 'app/communication/message/message-inline-input/message-inline-input.component';
import { LinkifyService } from 'app/communication/link-preview/services/linkify.service';
import { LinkPreviewService } from 'app/communication/link-preview/services/link-preview.service';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { DialogService } from 'primeng/dynamicdialog';

@Directive({
    selector: '[infinite-scroll]',
})
class InfiniteScrollStubDirective {
    readonly scrollWindow = input(true);
    readonly scrolledUp = output<void>();
}

describe('DiscussionSectionComponent', () => {
    setupTestBed({ zoneless: true });

    let component: DiscussionSectionComponent;
    let fixture: ComponentFixture<DiscussionSectionComponent>;
    let metisService: MetisService;
    let metisServiceGetFilteredPostsSpy: ReturnType<typeof vi.spyOn>;
    let channelService: ChannelService;
    let getChannelOfLectureSpy: ReturnType<typeof vi.spyOn>;
    let getChannelOfExerciseSpy: ReturnType<typeof vi.spyOn>;
    let courseStorageService: CourseStorageService;

    beforeEach(async () => {
        vi.useFakeTimers();
        await TestBed.configureTestingModule({
            imports: [
                MockModule(FormsModule),
                MockModule(ReactiveFormsModule),
                MockModule(NgbTooltipModule),
                DiscussionSectionComponent,
                FaIconComponent,
                InfiniteScrollStubDirective,
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                FormBuilder,
                MockProvider(SessionStorageService),
                MockProvider(ChannelService),
                { provide: LinkifyService, useClass: LinkifyService },
                { provide: LinkPreviewService, useClass: LinkPreviewService },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: PostService, useClass: MockPostService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: MetisService, useClass: MetisService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: CourseStorageService, useClass: CourseStorageService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                MockProvider(DialogService),
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ postId: metisPostTechSupport.id, courseId: metisCourse.id }),
                },
            ],
        })
            .overrideComponent(DiscussionSectionComponent, {
                set: {
                    providers: [{ provide: MetisService, useClass: MetisService }],
                },
            })
            .overrideComponent(DiscussionSectionComponent, {
                remove: { imports: [PostingThreadComponent, MessageInlineInputComponent] },
                add: { imports: [MockComponent(PostingThreadComponent), MockComponent(MessageInlineInputComponent)] },
            });

        fixture = TestBed.createComponent(DiscussionSectionComponent);
        component = fixture.componentInstance;
        metisService = fixture.debugElement.injector.get(MetisService);
        channelService = TestBed.inject(ChannelService);
        getChannelOfLectureSpy = vi.spyOn(channelService, 'getChannelOfLecture').mockReturnValue(
            of(
                new HttpResponse({
                    body: metisLectureChannelDTO,
                    status: 200,
                }),
            ),
        );
        getChannelOfExerciseSpy = vi.spyOn(channelService, 'getChannelOfExercise').mockReturnValue(
            of(
                new HttpResponse({
                    body: metisExerciseChannelDTO,
                    status: 200,
                }),
            ),
        );
        metisServiceGetFilteredPostsSpy = vi.spyOn(metisService, 'getFilteredPosts');

        courseStorageService = TestBed.inject(CourseStorageService);
        courseStorageService.setCourses([metisCourse]);
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('should set course and messages for lecture with lecture channel on initialization', () => {
        fixture.componentRef.setInput('lecture', { ...metisLecture, course: metisCourse });
        fixture.detectChanges();
        vi.advanceTimersByTime(0);
        expect(component.course).toEqual(metisCourse);
        expect(component.createdPost).toBeDefined();
        expect(component.channel).toEqual(metisLectureChannelDTO);
        expect(getChannelOfLectureSpy).toHaveBeenCalled();
        // Use spread operator to avoid mutating the shared test data array
        expect(component.posts).toEqual([...messagesBetweenUser1User2].reverse());
    });

    it('should set course and messages for exercise with exercise channel on initialization', () => {
        fixture.componentRef.setInput('exercise', { ...metisExercise, course: metisCourse });
        fixture.detectChanges();
        vi.advanceTimersByTime(0);
        expect(component.course).toEqual(metisCourse);
        expect(component.createdPost).toBeDefined();
        expect(component.channel).toEqual(metisExerciseChannelDTO);
        expect(getChannelOfExerciseSpy).toHaveBeenCalled();
        // Use spread operator to avoid mutating the shared test data array
        expect(component.posts).toEqual([...messagesBetweenUser1User2].reverse());
    });

    it('should reset current post', () => {
        fixture.componentRef.setInput('lecture', { ...metisLecture, course: metisCourse });
        fixture.detectChanges();
        component.resetCurrentPost();
        vi.advanceTimersByTime(0);
        expect(component.currentPost).toBeUndefined();
        expect(component.currentPostId).toBeUndefined();
    });

    it('should initialize correctly for exercise posts with default settings', () => {
        fixture.componentRef.setInput('exercise', { ...metisExercise, course: metisCourse });
        fixture.detectChanges();
        vi.advanceTimersByTime(0);
        expect(component.formGroup.get('filterToUnresolved')?.value).toBe(false);
        expect(component.formGroup.get('filterToOwn')?.value).toBe(false);
        expect(component.formGroup.get('filterToAnsweredOrReacted')?.value).toBe(false);
        fixture.changeDetectorRef.detectChanges();
        const searchInput = getElement(fixture.debugElement, 'input#search');
        expect((searchInput as HTMLInputElement).value).toBe('');
        vi.advanceTimersByTime(0);
    });

    it('should display one new message button for more then 3 messages in channel', () => {
        fixture.componentRef.setInput('exercise', { ...metisExercise, course: metisCourse });
        fixture.detectChanges();
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();
        vi.advanceTimersByTime(0);
        // Create posts with unique IDs to avoid duplicate key errors with track by post.id
        component.posts = [
            { ...metisExercisePosts[0], id: 101 },
            { ...metisExercisePosts[1], id: 102 },
            { ...metisExercisePosts[0], id: 103 },
            { ...metisExercisePosts[1], id: 104 },
        ];
        fixture.changeDetectorRef.detectChanges();
        vi.advanceTimersByTime(0);
        const newPostButtons = getElements(fixture.debugElement, '#new-post');
        expect(newPostButtons).not.toBeNull();
        expect(newPostButtons).toHaveLength(1);
    });

    it('should display one new message button', () => {
        fixture.componentRef.setInput('exercise', { ...metisExercise, course: metisCourse });
        fixture.detectChanges();
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();
        const newPostButtons = getElements(fixture.debugElement, '#new-post');
        expect(newPostButtons).not.toBeNull();
        expect(newPostButtons).toHaveLength(1);
    });

    it('should show search-bar and filters if not focused to a post', () => {
        fixture.componentRef.setInput('exercise', { ...metisExercise, course: metisCourse });
        fixture.detectChanges();
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();
        const searchInput = getElement(fixture.debugElement, 'input#search');
        const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToUnresolved]');
        const filterOwnCheckbox = getElement(fixture.debugElement, 'input[name=filterToOwn]');
        const filterToAnsweredOrReacted = getElement(fixture.debugElement, 'input[name=filterToAnsweredOrReacted]');

        expect(searchInput).not.toBeNull();
        expect(filterResolvedCheckbox).not.toBeNull();
        expect(filterOwnCheckbox).not.toBeNull();
        expect(filterToAnsweredOrReacted).not.toBeNull();
    });

    it('should hide search-bar and filters if focused to a post', () => {
        fixture.detectChanges();
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();
        const searchInput = getElement(fixture.debugElement, 'input#search');
        const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToUnresolved]');
        const filterOwnCheckbox = getElement(fixture.debugElement, 'input[name=filterToOwn]');
        const filterToAnsweredOrReacted = getElement(fixture.debugElement, 'input[name=filterToAnsweredOrReacted]');

        expect(searchInput).toBeNull();
        expect(filterResolvedCheckbox).toBeNull();
        expect(filterOwnCheckbox).toBeNull();
        expect(filterToAnsweredOrReacted).toBeNull();
    });

    it('triggering filters should invoke the metis service', () => {
        fixture.componentRef.setInput('exercise', { ...metisExercise, course: metisCourse });
        metisServiceGetFilteredPostsSpy.mockReset();
        fixture.detectChanges();
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();
        component.currentUser = { id: 99, login: 'admin' } as User;
        component.formGroup.patchValue({
            filterToUnresolved: true,
            filterToOwn: true,
            filterToAnsweredOrReacted: true,
        });

        const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToUnresolved]');
        const filterOwnCheckbox = getElement(fixture.debugElement, 'input[name=filterToOwn]');
        const filterToAnsweredOrReacted = getElement(fixture.debugElement, 'input[name=filterToAnsweredOrReacted]');

        filterResolvedCheckbox.dispatchEvent(new Event('change'));
        filterOwnCheckbox.dispatchEvent(new Event('change'));
        filterToAnsweredOrReacted.dispatchEvent(new Event('change'));

        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        expect(component.currentPostContextFilter.filterToUnresolved).toBe(true);
        expect(component.currentPostContextFilter.authorIds!.length > 0).toBe(true);
        expect(component.currentPostContextFilter.filterToAnsweredOrReacted).toBe(true);
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(4);
    });

    it('loads exercise messages if communication only', () => {
        component.course = { id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_ONLY } as Course;
        fixture.componentRef.setInput('exercise', { id: 2 } as Exercise);
        fixture.changeDetectorRef.detectChanges();

        component.setChannel(1);

        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith(
            { ...component.currentPostContextFilter, conversationIds: [metisExerciseChannelDTO.id] } as PostContextFilter,
            true,
            metisExerciseChannelDTO,
        );
        expect(component.channel).toBe(metisExerciseChannelDTO);
    });

    it('loads lecture messages if communication only', () => {
        component.course = { id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_ONLY } as Course;
        fixture.componentRef.setInput('lecture', { id: 2 } as Lecture);
        fixture.changeDetectorRef.detectChanges();

        component.setChannel(1);

        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith(
            { ...component.currentPostContextFilter, conversationIds: [metisLectureChannelDTO.id] },
            true,
            metisLectureChannelDTO,
        );
        expect(component.channel).toBe(metisLectureChannelDTO);
    });

    it('collapses sidebar if no channel exists', () => {
        component.course = { id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_ONLY } as Course;
        fixture.componentRef.setInput('lecture', { id: 2 } as Lecture);
        fixture.changeDetectorRef.detectChanges();
        getChannelOfLectureSpy = vi.spyOn(channelService, 'getChannelOfLecture').mockReturnValue(
            of(
                new HttpResponse({
                    body: undefined as any,
                    status: 200,
                }),
            ),
        );

        component.setChannel(1);

        expect(component.channel).toBeUndefined();
        expect(component.noChannelAvailable).toBe(true);
        expect(component.collapsed).toBe(true);
    });

    it('should react to scroll up event', () => {
        const course = { id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_ONLY } as Course;
        fixture.componentRef.setInput('lecture', { id: 2, course: course } as Lecture);
        fixture.detectChanges();
        const fetchNextPageSpy = vi.spyOn(component, 'fetchNextPage');

        const scrolledUp = new CustomEvent('scrolledUp');
        component.content()!.nativeElement.dispatchEvent(scrolledUp);

        expect(fetchNextPageSpy).toHaveBeenCalledOnce();
    });

    it('should toggle send message', () => {
        component.shouldSendMessage = true;
        component.toggleSendMessage();
        expect(component.shouldSendMessage).toBe(false);
        component.toggleSendMessage();
        expect(component.shouldSendMessage).toBe(true);
    });

    it('should change sort direction', () => {
        fixture.detectChanges();
        component.currentSortDirection = SortDirection.ASCENDING;
        fixture.changeDetectorRef.detectChanges();
        component.onChangeSortDir();
        expect(component.currentSortDirection).toBe(SortDirection.DESCENDING);
        component.onChangeSortDir();
        expect(component.currentSortDirection).toBe(SortDirection.ASCENDING);
    });

    it('fetches new messages on scroll up if more messages are available', () => {
        // Use unique post IDs to avoid duplicate key warnings from Angular's @for track
        metisServiceGetFilteredPostsSpy.mockImplementation(() => {
            component.posts = [{ id: 1001 } as any, { id: 1002 } as any];
        });
        const course = { id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_ONLY } as Course;
        fixture.componentRef.setInput('lecture', { id: 2, course: course } as Lecture);
        fixture.detectChanges();
        component.posts = [];
        const commandMetisToFetchPostsSpy = vi.spyOn(component, 'fetchNextPage');

        const scrolledUp = new CustomEvent('scrolledUp');
        component.content()!.nativeElement.dispatchEvent(scrolledUp);

        expect(commandMetisToFetchPostsSpy).toHaveBeenCalledOnce();
    });
});
