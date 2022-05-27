import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PostFooterComponent } from 'app/shared/metis/posting-footer/post-footer/post-footer.component';
import { MockComponent, MockModule } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/post-reactions-bar/post-reactions-bar.component';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { MetisService } from 'app/shared/metis/metis.service';
import { getElements } from '../../../../../helpers/utils/general.utils';
import { PostService } from 'app/shared/metis/post.service';
import { MockPostService } from '../../../../../helpers/mocks/service/mock-post.service';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { MockAnswerPostService } from '../../../../../helpers/mocks/service/mock-answer-post.service';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { AnswerPostComponent } from 'app/shared/metis/answer-post/answer-post.component';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { TranslatePipeMock } from '../../../../../helpers/mocks/service/mock-translate.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import {
    metisPostExerciseUser1,
    metisPostLectureUser1,
    metisPostLectureUser2,
    metisTags,
    post,
    sortedAnswerArray,
    unsortedAnswerArray,
} from '../../../../../helpers/sample/metis-sample-data';

describe('PostFooterComponent', () => {
    let component: PostFooterComponent;
    let fixture: ComponentFixture<PostFooterComponent>;
    const updatedTags = ['tag1', 'tag2', 'tag3'];
    let metisService: MetisService;
    let metisServiceUserAuthorityStub: jest.SpyInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(ArtemisCoursesRoutingModule)],
            providers: [
                { provide: PostService, useClass: MockPostService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: MetisService, useClass: MockMetisService },
            ],
            declarations: [
                PostFooterComponent,
                TranslatePipeMock,
                MockComponent(FaIconComponent),
                MockComponent(PostReactionsBarComponent),
                MockComponent(PostComponent),
                MockComponent(AnswerPostComponent),
                MockComponent(AnswerPostCreateEditModalComponent),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostFooterComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceUserAuthorityStub = jest.spyOn(metisService, 'metisUserIsAtLeastTutorInCourse');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize post tags correctly', () => {
        component.posting = metisPostLectureUser2;
        component.posting.tags = metisTags;
        component.ngOnInit();
        expect(component.tags).toEqual(metisTags);
    });

    it('should initialize post without tags correctly', () => {
        component.posting = metisPostExerciseUser1;
        component.ngOnInit();
        expect(component.tags).toEqual([]);
    });

    it('should update post tags correctly', () => {
        component.posting = metisPostLectureUser1;
        component.ngOnInit();
        component.posting.tags = updatedTags;
        component.ngOnChanges();
        expect(component.tags).toEqual(updatedTags);
    });

    it('should have a tag shown for each post tag', () => {
        component.posting = metisPostLectureUser1;
        component.posting.tags = metisTags;
        component.ngOnInit();
        fixture.detectChanges();
        const tags = getElements(fixture.debugElement, '.post-tag');
        expect(tags).toHaveLength(metisTags.length);
    });

    it('should be initialized correctly for users that are at least tutors in course', () => {
        component.posting = post;
        component.posting.answers = unsortedAnswerArray;
        metisServiceUserAuthorityStub.mockReturnValue(true);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).toBeTrue();
        expect(component.createdAnswerPost.resolvesPost).toBeTrue();
    });

    it('should be initialized correctly for users that are not at least tutors in course', () => {
        component.posting = post;
        component.posting.answers = unsortedAnswerArray;
        metisServiceUserAuthorityStub.mockReturnValue(false);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).toBeFalse();
        expect(component.createdAnswerPost.resolvesPost).toBeFalse();
    });

    it('should contain an answer post', () => {
        component.posting = post;
        component.posting.answers = unsortedAnswerArray;
        component.showAnswers = true;
        fixture.detectChanges();
        const answerPostComponent = fixture.debugElement.nativeElement.querySelector('jhi-answer-post');
        expect(answerPostComponent).not.toBeNull();
    });

    it('should not contain an answer post', () => {
        component.posting = post;
        component.posting.answers = unsortedAnswerArray;
        component.showAnswers = false;
        fixture.detectChanges();
        const answerPostComponent = fixture.debugElement.nativeElement.querySelector('jhi-answer-post');
        expect(answerPostComponent).toBeNull();
    });

    it('should sort answers', () => {
        component.posting = post;
        component.posting.answers = unsortedAnswerArray;
        component.sortAnswerPosts();
        expect(component.sortedAnswerPosts).toEqual(sortedAnswerArray);
    });

    it('should not sort empty array of answers', () => {
        component.posting = post;
        component.posting.answers = unsortedAnswerArray;
        component.posting.answers = undefined;
        component.sortAnswerPosts();
        expect(component.sortedAnswerPosts).toEqual([]);
    });

    it('should sort answers on changes', () => {
        component.posting = post;
        component.posting.answers = unsortedAnswerArray;
        component.ngOnChanges();
        expect(component.sortedAnswerPosts).toEqual(sortedAnswerArray);
    });

    it('should contain reference to container for rendering answerPostCreateEditModal component', () => {
        expect(component.containerRef).not.toBeNull();
    });

    it('should contain component to create a new answer post', () => {
        const answerPostCreateEditModal = fixture.debugElement.nativeElement.querySelector('jhi-answer-post-create-edit-modal');
        expect(answerPostCreateEditModal).not.toBeNull();
    });
});
