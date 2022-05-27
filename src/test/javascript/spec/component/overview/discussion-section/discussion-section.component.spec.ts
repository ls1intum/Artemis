import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Post } from 'app/entities/metis/post.model';
import dayjs from 'dayjs/esm';
import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
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
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { PostingThreadComponent } from 'app/shared/metis/posting-thread/posting-thread.component';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { DisplayPriority } from 'app/shared/metis/metis.util';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockActivatedRoute } from '../../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { FormBuilder } from '@angular/forms';
import {
    metisCourse,
    metisExercise,
    metisExercisePosts,
    metisLecture,
    metisLecturePosts,
    metisPostExerciseUser1,
    metisPostExerciseUser2,
    metisPostLectureUser1,
    metisPostLectureUser2,
    metisPostTechSupport,
    metisUpVoteReactionUser1,
} from '../../../helpers/sample/metis-sample-data';

describe('PageDiscussionSectionComponent', () => {
    let component: DiscussionSectionComponent;
    let fixture: ComponentFixture<DiscussionSectionComponent>;
    let courseManagementService: CourseManagementService;
    let post1: Post;
    let post2: Post;
    let post3: Post;
    let post4: Post;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                FormBuilder,
                MockProvider(SessionStorageService),
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: PostService, useClass: MockPostService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ postId: metisPostTechSupport.id, courseId: metisCourse.id }),
                },
            ],
            declarations: [
                DiscussionSectionComponent,
                MockComponent(PostingThreadComponent),
                MockComponent(PostCreateEditModalComponent),
                MockComponent(FaIconComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
            ],
        })
            .overrideComponent(DiscussionSectionComponent, {
                set: {
                    providers: [{ provide: MetisService, useClass: MetisService }],
                },
            })
            .compileComponents()
            .then(() => {
                courseManagementService = TestBed.inject(CourseManagementService);
                jest.spyOn(courseManagementService, 'findOneForDashboard').mockReturnValue(of({ body: metisCourse }) as Observable<HttpResponse<Course>>);
                fixture = TestBed.createComponent(DiscussionSectionComponent);
                component = fixture.componentInstance;
                fixture.debugElement.injector.get(MetisService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set course and posts for exercise on initialization', fakeAsync(() => {
        component.exercise = metisExercise;
        component.ngOnInit();
        tick();
        expect(component.course).toEqual(metisCourse);
        expect(component.createdPost).toBeDefined();
        expect(component.posts).toEqual(metisExercisePosts);
    }));

    it('should set course and posts for lecture on initialization', fakeAsync(() => {
        component.lecture = metisLecture;
        component.ngOnInit();
        tick();
        expect(component.createdPost).toBeDefined();
        expect(component.posts).toEqual(metisLecturePosts);
    }));

    it('should show single post if current post is set', fakeAsync(() => {
        component.ngOnInit();
        tick();
        // mock activated route returns id of metisPostTechSupport
        expect(component.currentPost).toEqual(metisPostTechSupport);
    }));

    it('should reset current post', fakeAsync(() => {
        component.resetCurrentPost();
        tick();
        expect(component.currentPost).toEqual(undefined);
        expect(component.currentPostId).toEqual(undefined);
    }));

    it('should sort posts correctly', () => {
        post1 = metisPostExerciseUser1;
        post1.creationDate = dayjs();
        post1.displayPriority = DisplayPriority.PINNED;

        post2 = metisPostExerciseUser2;
        post2.creationDate = dayjs().subtract(1, 'day');
        post2.displayPriority = DisplayPriority.NONE;

        post3 = metisPostLectureUser1;
        post3.creationDate = dayjs().subtract(2, 'day');
        post3.reactions = [metisUpVoteReactionUser1];
        post3.displayPriority = DisplayPriority.NONE;

        post4 = metisPostLectureUser2;
        post4.creationDate = dayjs().subtract(2, 'minute');
        post4.reactions = [metisUpVoteReactionUser1];
        post4.displayPriority = DisplayPriority.ARCHIVED;

        let posts = [post1, post2, post3, post4];
        posts = posts.sort(component.sectionSortFn);
        expect(posts).toEqual([post1, post3, post2, post4]);
    });
});
