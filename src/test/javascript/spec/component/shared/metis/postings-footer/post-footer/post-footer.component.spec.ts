import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PostFooterComponent } from 'app/shared/metis/posting-footer/post-footer/post-footer.component';
import { MockComponent, MockModule } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/post-reactions-bar/post-reactions-bar.component';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { MetisService } from 'app/shared/metis/metis.service';
import { PostService } from 'app/shared/metis/post.service';
import { MockPostService } from '../../../../../helpers/mocks/service/mock-post.service';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { MockAnswerPostService } from '../../../../../helpers/mocks/service/mock-answer-post.service';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { AnswerPostComponent } from 'app/shared/metis/answer-post/answer-post.component';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { TranslatePipeMock } from '../../../../../helpers/mocks/service/mock-translate.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { metisPostExerciseUser1, post, unsortedAnswerArray } from '../../../../../helpers/sample/metis-sample-data';

describe('PostFooterComponent', () => {
    let component: PostFooterComponent;
    let fixture: ComponentFixture<PostFooterComponent>;
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
        component.posting.answers = component.sortedAnswerPosts = unsortedAnswerArray;
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

    it('should contain reference to container for rendering answerPostCreateEditModal component', () => {
        expect(component.containerRef).not.toBeNull();
    });

    it('should contain component to create a new answer post', () => {
        const answerPostCreateEditModal = fixture.debugElement.nativeElement.querySelector('jhi-answer-post-create-edit-modal');
        expect(answerPostCreateEditModal).not.toBeNull();
    });

    it('should open create answer post modal', () => {
        component.posting = metisPostExerciseUser1;
        component.ngOnInit();
        fixture.detectChanges();
        const createAnswerPostModalOpen = jest.spyOn(component.createAnswerPostModalComponent, 'open');
        component.openCreateAnswerPostModal();
        expect(createAnswerPostModalOpen).toHaveBeenCalledOnce();
    });
});
