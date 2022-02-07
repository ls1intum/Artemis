import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';
import { PostingThreadComponent } from 'app/shared/metis/posting-thread/posting-thread.component';
import { PostService } from 'app/shared/metis/post.service';
import { MockPostService } from '../../../../helpers/mocks/service/mock-post.service';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { MockAnswerPostService } from '../../../../helpers/mocks/service/mock-answer-post.service';
import { MetisService } from 'app/shared/metis/metis.service';
import dayjs from 'dayjs/esm';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { MockComponent } from 'ng-mocks';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { AnswerPostComponent } from 'app/shared/metis/answer-post/answer-post.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { metisPostExerciseUser1 } from '../../../../helpers/sample/metis-sample-data';

describe('PostingThreadComponent', () => {
    let component: PostingThreadComponent;
    let fixture: ComponentFixture<PostingThreadComponent>;
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
                PostingThreadComponent,
                TranslatePipeMock,
                MockComponent(PostComponent),
                MockComponent(AnswerPostComponent),
                MockComponent(FaIconComponent),
                MockComponent(AnswerPostCreateEditModalComponent),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostingThreadComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceUserAuthorityStub = jest.spyOn(metisService, 'metisUserIsAtLeastTutorInCourse');
            });
    });

    afterEach(() => {
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

    it('should display button to show multiple answers', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.showAnswers = false;
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.nativeElement.querySelector('button');
        expect(answerNowButton.innerHTML).toContain('showMultipleAnswers');
    });

    it('should display button to show single answer', () => {
        component.post = post;
        component.post.answers = [metisPostExerciseUser1];
        component.showAnswers = false;
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.nativeElement.querySelector('button');
        expect(answerNowButton.innerHTML).toContain('showSingleAnswer');
    });

    it('start discussion button should be visible if post does not yet have any answers', () => {
        component.post = post;
        component.post.answers = [];
        fixture.detectChanges();
        const startDiscussion = fixture.debugElement.nativeElement.querySelector('button');
        expect(startDiscussion.innerHTML).toContain('startDiscussion');
    });

    it('answer now button should not be visible if answer posts are not shown', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.showAnswers = false;
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.nativeElement.querySelector('button');
        expect(answerNowButton.innerHTML).not.toContain('answerNow');
    });

    it('answer now button should be visible if answer posts are shown', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.showAnswers = true;
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.nativeElement.querySelector('button');
        expect(answerNowButton.innerHTML).toContain('answerNow');
    });

    it('should contain a post', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.ngOnInit();
        const postComponent = fixture.debugElement.nativeElement.querySelector('jhi-post');
        expect(postComponent).not.toBe(null);
    });

    it('should contain an answer post', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.showAnswers = true;
        fixture.detectChanges();
        const answerPostComponent = fixture.debugElement.nativeElement.querySelector('jhi-answer-post');
        expect(answerPostComponent).not.toBe(null);
    });

    it('should not contain an answer post', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.showAnswers = false;
        fixture.detectChanges();
        const answerPostComponent = fixture.debugElement.nativeElement.querySelector('jhi-answer-post');
        expect(answerPostComponent).toBe(null);
    });
});
