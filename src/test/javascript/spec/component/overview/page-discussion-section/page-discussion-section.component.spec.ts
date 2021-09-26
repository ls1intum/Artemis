import * as chai from 'chai';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Post } from 'app/entities/metis/post.model';
import * as sinon from 'sinon';
import { stub } from 'sinon';
import dayjs from 'dayjs';
import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MetisService } from 'app/shared/metis/metis.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { MockExerciseService } from '../../../helpers/mocks/service/mock-exercise.service';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ArtemisTestModule } from '../../../test.module';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { MockAnswerPostService } from '../../../helpers/mocks/service/mock-answer-post.service';
import { PostService } from 'app/shared/metis/post.service';
import { MockPostService } from '../../../helpers/mocks/service/mock-post.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { PageDiscussionSectionComponent } from 'app/overview/page-discussion-section/page-discussion-section.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { PostingsThreadComponent } from 'app/shared/metis/postings-thread/postings-thread.component';
import { PostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { DisplayPriority } from 'app/shared/metis/metis.util';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
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
    metisUpVoteReactionUser1,
} from '../../../helpers/sample/metis-sample-data';

const expect = chai.expect;

describe('PageDiscussionSectionComponent', () => {
    let component: PageDiscussionSectionComponent;
    let fixture: ComponentFixture<PageDiscussionSectionComponent>;
    let courseManagementService: CourseManagementService;
    let post1: Post;
    let post2: Post;
    let post3: Post;
    let post4: Post;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: PostService, useClass: MockPostService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
            declarations: [
                PageDiscussionSectionComponent,
                MockComponent(PostingsThreadComponent),
                MockComponent(PostCreateEditModalComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
            ],
        })
            .overrideComponent(PageDiscussionSectionComponent, {
                set: {
                    providers: [{ provide: MetisService, useClass: MetisService }],
                },
            })
            .compileComponents()
            .then(() => {
                courseManagementService = TestBed.inject(CourseManagementService);
                stub(courseManagementService, 'findOneForDashboard').returns(of({ body: metisCourse }) as Observable<HttpResponse<Course>>);
                fixture = TestBed.createComponent(PageDiscussionSectionComponent);
                component = fixture.componentInstance;
                fixture.debugElement.injector.get(MetisService);
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should set course and posts for exercise on initialization', fakeAsync(() => {
        component.exercise = metisExercise;
        component.ngOnInit();
        tick();
        expect(component.course).to.deep.equal(metisCourse);
        expect(component.createdPost).to.not.be.undefined;
        expect(component.posts).to.be.deep.equal(metisExercisePosts);
    }));

    it('should set course and posts for lecture on initialization', fakeAsync(() => {
        component.lecture = metisLecture;
        component.ngOnInit();
        tick();
        expect(component.createdPost).to.not.be.undefined;
        expect(component.posts).to.be.deep.equal(metisLecturePosts);
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
        expect(posts).to.be.deep.equal([post1, post3, post2, post4]);
    });
});
