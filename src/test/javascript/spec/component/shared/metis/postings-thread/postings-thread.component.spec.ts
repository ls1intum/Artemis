import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';
import { PostingsThreadComponent } from 'app/shared/metis/postings-thread/postings-thread.component';
import { PostService } from 'app/shared/metis/post.service';
import { MockPostService } from '../../../../helpers/mocks/service/mock-post.service';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { MockAnswerPostService } from '../../../../helpers/mocks/service/mock-answer-post.service';
import { MetisService } from 'app/shared/metis/metis.service';
import dayjs from 'dayjs';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { AnswerPostComponent } from 'app/shared/metis/answer-post/answer-post.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';

describe('PostingsThreadComponent', () => {
    let component: PostingsThreadComponent;
    let fixture: ComponentFixture<PostingsThreadComponent>;
    let metisService: MetisService;
    let metisServiceUserAuthorityStub: jest.SpyInstance;

    const unApprovedAnswerPost1 = {
        id: 1,
        creationDate: dayjs(),
        content: 'not approved most recent',
        resolvesPost: false,
    } as AnswerPost;

    const unApprovedAnswerPost2 = {
        id: 2,
        creationDate: dayjs().subtract(1, 'day'),
        content: 'not approved',
        resolvesPost: false,
    } as AnswerPost;

    const approvedAnswerPost = {
        id: 2,
        creationDate: undefined,
        content: 'approved',
        resolvesPost: true,
    } as AnswerPost;

    const sortedAnswerArray: AnswerPost[] = [approvedAnswerPost, unApprovedAnswerPost2, unApprovedAnswerPost1];
    const unsortedAnswerArray: AnswerPost[] = [unApprovedAnswerPost1, unApprovedAnswerPost2, approvedAnswerPost];

    const post = {
        id: 1,
        creationDate: undefined,
        answers: unsortedAnswerArray,
    } as Post;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [
                { provide: PostService, useClass: MockPostService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: MetisService, useClass: MockMetisService },
            ],
            declarations: [
                PostingsThreadComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(PostComponent),
                MockComponent(AnswerPostComponent),
                MockComponent(FaIconComponent),
                MockComponent(AnswerPostCreateEditModalComponent),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostingsThreadComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceUserAuthorityStub = jest.spyOn(metisService, 'metisUserIsAtLeastTutorInCourse');
            });
    });

    afterEach(function () {
        jest.restoreAllMocks();
    });
    it('should be initialized correctly for users that are at least tutors in course', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        metisServiceUserAuthorityStub.mockReturnValue(true);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).toEqual(true);
        expect(component.createdAnswerPost.resolvesPost).toEqual(true);
    });

    it('should be initialized correctly for users that are not at least tutors in course', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        metisServiceUserAuthorityStub.mockReturnValue(false);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).toEqual(false);
        expect(component.createdAnswerPost.resolvesPost).toEqual(false);
    });

    it('should sort answers', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.sortAnswerPosts();
        expect(component.sortedAnswerPosts).toEqual(sortedAnswerArray);
    });

    it('should not sort empty array of answers', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.post.answers = undefined;
        component.sortAnswerPosts();
        expect(component.sortedAnswerPosts).toEqual([]);
    });

    it('should sort answers on changes', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.ngOnChanges();
        expect(component.sortedAnswerPosts).toEqual(sortedAnswerArray);
    });

    it('should toggle answers', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.showAnswers = false;
        component.toggleAnswers();
        expect(component.showAnswers).toEqual(true);
    });

    it('answer now button should not be visible if post does not yet have any answers', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.nativeElement.querySelector('button');
        expect(answerNowButton).toBeNull();
    });

    it('answer now button should be visible if post does not yet have any answers', () => {
        component.post = post;
        component.post.answers = [];
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.nativeElement.querySelector('button');
        expect(answerNowButton).toBeTruthy();
    });

    it('should contain a post', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.ngOnInit();
        const postComponent = fixture.debugElement.nativeElement.querySelector('jhi-post');
        expect(postComponent).toBeTruthy();
    });

    it('should contain an answer post', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.showAnswers = true;
        fixture.detectChanges();
        const answerPostComponent = fixture.debugElement.nativeElement.querySelector('jhi-answer-post');
        expect(answerPostComponent).toBeTruthy();
    });

    it('should not contain an answer post', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.showAnswers = false;
        fixture.detectChanges();
        const answerPostComponent = fixture.debugElement.nativeElement.querySelector('jhi-answer-post');
        expect(answerPostComponent).toBeFalsy();
    });
});
