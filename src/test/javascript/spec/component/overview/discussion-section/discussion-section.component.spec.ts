import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { MockComponent, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MetisService } from 'app/shared/metis/metis.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { MockExerciseService } from '../../../helpers/mocks/service/mock-exercise.service';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { MockAnswerPostService } from '../../../helpers/mocks/service/mock-answer-post.service';
import { PostService } from 'app/shared/metis/post.service';
import { MockPostService } from '../../../helpers/mocks/service/mock-post.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { DiscussionSectionComponent } from 'app/overview/discussion-section/discussion-section.component';
import { PostingThreadComponent } from 'app/shared/metis/posting-thread/posting-thread.component';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockActivatedRoute } from '../../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { getElement, getElements } from '../../../helpers/utils/general.utils';
import { ButtonComponent } from 'app/shared/components/button.component';
import {
    messagesBetweenUser1User2,
    metisCourse,
    metisExercise,
    metisExerciseChannelDTO,
    metisExercisePosts,
    metisLecture,
    metisLectureChannelDTO,
    metisPostTechSupport,
} from '../../../helpers/sample/metis-sample-data';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { PostContextFilter, SortDirection } from 'app/shared/metis/metis.util';
import { Course, CourseInformationSharingConfiguration } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { Directive, EventEmitter, Input, Output } from '@angular/core';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { MockMetisConversationService } from '../../../helpers/mocks/service/mock-metis-conversation.service';
import { NotificationService } from 'app/shared/notification/notification.service';
import { MockNotificationService } from '../../../helpers/mocks/service/mock-notification.service';

@Directive({
    // eslint-disable-next-line @angular-eslint/directive-selector
    selector: '[infinite-scroll]',
})
class InfiniteScrollStubDirective {
    @Input() scrollWindow = true;
    @Output() scrolledUp = new EventEmitter<void>();
}

describe('DiscussionSectionComponent', () => {
    let component: DiscussionSectionComponent;
    let fixture: ComponentFixture<DiscussionSectionComponent>;
    let metisService: MetisService;
    let metisServiceGetFilteredPostsSpy: jest.SpyInstance;
    let channelService: ChannelService;
    let getChannelOfLectureSpy: jest.SpyInstance;
    let getChannelOfExerciseSpy: jest.SpyInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, MockModule(FormsModule), MockModule(ReactiveFormsModule), MockModule(NgbTooltipModule)],
            providers: [
                FormBuilder,
                MockProvider(SessionStorageService),
                MockProvider(ChannelService),
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                { provide: NotificationService, useClass: MockNotificationService },
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: PostService, useClass: MockPostService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: MetisService, useClass: MetisService },
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ postId: metisPostTechSupport.id, courseId: metisCourse.id }),
                },
            ],
            declarations: [
                DiscussionSectionComponent,
                InfiniteScrollStubDirective,
                MockComponent(PostingThreadComponent),
                MockComponent(PostCreateEditModalComponent),
                MockComponent(FaIconComponent),
                MockComponent(ButtonComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
        })
            .overrideComponent(DiscussionSectionComponent, {
                set: {
                    providers: [{ provide: MetisService, useClass: MetisService }],
                },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DiscussionSectionComponent);
                component = fixture.componentInstance;
                metisService = fixture.debugElement.injector.get(MetisService);
                channelService = TestBed.inject(ChannelService);
                getChannelOfLectureSpy = jest.spyOn(channelService, 'getChannelOfLecture').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: metisLectureChannelDTO,
                            status: 200,
                        }),
                    ),
                );
                getChannelOfExerciseSpy = jest.spyOn(channelService, 'getChannelOfExercise').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: metisExerciseChannelDTO,
                            status: 200,
                        }),
                    ),
                );
                metisServiceGetFilteredPostsSpy = jest.spyOn(metisService, 'getFilteredPosts');
                component.lecture = { ...metisLecture, course: metisCourse };
                component.ngOnInit();
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set course and messages for lecture with lecture channel on initialization', fakeAsync(() => {
        component.lecture = { ...metisLecture, course: metisCourse };
        component.ngOnInit();
        tick();
        expect(component.course).toEqual(metisCourse);
        expect(component.createdPost).toBeDefined();
        expect(component.channel).toEqual(metisLectureChannelDTO);
        expect(getChannelOfLectureSpy).toHaveBeenCalled();
        expect(component.posts).toEqual(messagesBetweenUser1User2.reverse());
    }));

    it('should set course and messages for exercise with exercise channel on initialization', fakeAsync(() => {
        component.lecture = undefined;
        component.exercise = { ...metisExercise, course: metisCourse };
        component.ngOnInit();
        tick();
        expect(component.course).toEqual(metisCourse);
        expect(component.createdPost).toBeDefined();
        expect(component.channel).toEqual(metisExerciseChannelDTO);
        expect(getChannelOfExerciseSpy).toHaveBeenCalled();
        expect(component.posts).toEqual(messagesBetweenUser1User2.reverse());
    }));

    it('should reset current post', fakeAsync(() => {
        component.resetCurrentPost();
        tick();
        expect(component.currentPost).toBeUndefined();
        expect(component.currentPostId).toBeUndefined();
    }));

    it('should initialize correctly for exercise posts with default settings', fakeAsync(() => {
        component.lecture = undefined;
        component.exercise = { ...metisExercise, course: metisCourse };
        component.ngOnInit();
        tick();
        expect(component.formGroup.get('filterToUnresolved')?.value).toBeFalse();
        expect(component.formGroup.get('filterToOwn')?.value).toBeFalse();
        expect(component.formGroup.get('filterToAnsweredOrReacted')?.value).toBeFalse();
        fixture.detectChanges();
        const searchInput = getElement(fixture.debugElement, 'input[name=searchText]');
        expect(searchInput.textContent).toBe('');
        tick();
    }));

    it('should display one new message button for more then 3 messages in channel', fakeAsync(() => {
        component.exercise = { ...metisExercise, course: metisCourse };
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        tick();
        component.posts = metisExercisePosts;
        fixture.detectChanges();
        tick();
        const newPostButtons = getElements(fixture.debugElement, '.btn-primary');
        expect(newPostButtons).not.toBeNull();
        expect(newPostButtons).toHaveLength(1);
    }));

    it('should display one new message button', fakeAsync(() => {
        component.exercise = { ...metisExercise, course: metisCourse };
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        const newPostButtons = getElements(fixture.debugElement, '.btn-primary');
        expect(newPostButtons).not.toBeNull();
        expect(newPostButtons).toHaveLength(1);
    }));

    it('should show search-bar and filters if not focused to a post', fakeAsync(() => {
        component.exercise = { ...metisExercise, course: metisCourse };
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        const searchInput = getElement(fixture.debugElement, 'input[name=searchText]');
        const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToUnresolved]');
        const filterOwnCheckbox = getElement(fixture.debugElement, 'input[name=filterToOwn]');
        const filterToAnsweredOrReacted = getElement(fixture.debugElement, 'input[name=filterToAnsweredOrReacted]');

        expect(searchInput).not.toBeNull();
        expect(filterResolvedCheckbox).not.toBeNull();
        expect(filterOwnCheckbox).not.toBeNull();
        expect(filterToAnsweredOrReacted).not.toBeNull();
    }));

    it('should hide search-bar and filters if focused to a post', fakeAsync(() => {
        component.lecture = undefined;
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        const searchInput = getElement(fixture.debugElement, 'input[name=searchText]');
        const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToUnresolved]');
        const filterOwnCheckbox = getElement(fixture.debugElement, 'input[name=filterToOwn]');
        const filterToAnsweredOrReacted = getElement(fixture.debugElement, 'input[name=filterToAnsweredOrReacted]');

        expect(searchInput).toBeNull();
        expect(filterResolvedCheckbox).toBeNull();
        expect(filterOwnCheckbox).toBeNull();
        expect(filterToAnsweredOrReacted).toBeNull();
    }));

    it('triggering filters should invoke the metis service', fakeAsync(() => {
        component.exercise = { ...metisExercise, course: metisCourse };
        metisServiceGetFilteredPostsSpy.mockReset();
        component.ngOnInit();
        tick();
        fixture.detectChanges();
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

        tick();
        fixture.detectChanges();

        expect(component.currentPostContextFilter.filterToUnresolved).toBeTrue();
        expect(component.currentPostContextFilter.filterToOwn).toBeTrue();
        expect(component.currentPostContextFilter.filterToAnsweredOrReacted).toBeTrue();
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(4);
    }));

    it('loads exercise messages if communication only', fakeAsync(() => {
        component.course = { id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_ONLY } as Course;
        component.exercise = { id: 2 } as Exercise;
        component.lecture = undefined;

        component.setChannel(1);

        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith(
            { ...component.currentPostContextFilter, conversationId: metisExerciseChannelDTO.id } as PostContextFilter,
            true,
            metisExerciseChannelDTO,
        );
        expect(component.channel).toBe(metisExerciseChannelDTO);
    }));

    it('loads lecture messages if communication only', fakeAsync(() => {
        component.course = { id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_ONLY } as Course;
        component.lecture = { id: 2 } as Lecture;

        component.setChannel(1);

        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith(
            { ...component.currentPostContextFilter, conversationId: metisLectureChannelDTO.id },
            true,
            metisLectureChannelDTO,
        );
        expect(component.channel).toBe(metisLectureChannelDTO);
    }));

    it('collapses sidebar if no channel exists', fakeAsync(() => {
        component.course = { id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_ONLY } as Course;
        component.lecture = { id: 2 } as Lecture;
        getChannelOfLectureSpy = jest.spyOn(channelService, 'getChannelOfLecture').mockReturnValue(
            of(
                new HttpResponse({
                    body: undefined as any,
                    status: 200,
                }),
            ),
        );

        component.setChannel(1);

        expect(component.channel).toBeUndefined();
        expect(component.noChannelAvailable).toBeTrue();
        expect(component.collapsed).toBeTrue();
    }));

    it('should react to srcoll up event', fakeAsync(() => {
        const fetchNextPageSpy = jest.spyOn(component, 'fetchNextPage');

        const scrolledUp = new CustomEvent('scrolledUp');
        component.content.nativeElement.dispatchEvent(scrolledUp);

        expect(fetchNextPageSpy).toHaveBeenCalledOnce();
    }));

    it('fetches new messages on scroll up if more messages are available', fakeAsync(() => {
        component.posts = [];
        const commandMetisToFetchPostsSpy = jest.spyOn(component, 'fetchNextPage');

        const scrolledUp = new CustomEvent('scrolledUp');
        component.content.nativeElement.dispatchEvent(scrolledUp);

        expect(commandMetisToFetchPostsSpy).toHaveBeenCalledOnce();
    }));

    it('should toggle send message', () => {
        component.shouldSendMessage = true;
        component.toggleSendMessage();
        expect(component.shouldSendMessage).toBeFalse();
        component.toggleSendMessage();
        expect(component.shouldSendMessage).toBeTrue();
    });

    it('should change sort direction', () => {
        component.currentSortDirection = SortDirection.ASCENDING;
        component.onChangeSortDir();
        expect(component.currentSortDirection).toBe(SortDirection.DESCENDING);
        component.onChangeSortDir();
        expect(component.currentSortDirection).toBe(SortDirection.ASCENDING);
    });
});
