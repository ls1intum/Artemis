import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PostingThreadComponent } from 'app/shared/metis/posting-thread/posting-thread.component';
import { PostService } from 'app/shared/metis/post.service';
import { MockPostService } from '../../../../helpers/mocks/service/mock-post.service';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { MockAnswerPostService } from '../../../../helpers/mocks/service/mock-answer-post.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { MockComponent } from 'ng-mocks';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { AnswerPostComponent } from 'app/shared/metis/answer-post/answer-post.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { post } from '../../../../helpers/sample/metis-sample-data';

describe('PostingThreadComponent', () => {
    let component: PostingThreadComponent;
    let fixture: ComponentFixture<PostingThreadComponent>;

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
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should contain a post', () => {
        component.post = post;
        const postComponent = fixture.debugElement.nativeElement.querySelector('jhi-post');
        expect(postComponent).not.toBeNull();
    });
});
