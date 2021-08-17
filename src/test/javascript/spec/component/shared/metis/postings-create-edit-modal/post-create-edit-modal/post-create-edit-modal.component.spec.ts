import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import * as sinon from 'sinon';
import { spy } from 'sinon';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { User } from 'app/core/user/user.model';
import { Post } from 'app/entities/metis/post.model';
import { PostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { PostingsMarkdownEditorComponent } from 'app/shared/metis/postings-markdown-editor/postings-markdown-editor.component';
import { PostingsButtonComponent } from 'app/shared/metis/postings-button/postings-button.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('PostCreateEditModalComponent', () => {
    let component: PostCreateEditModalComponent;
    let fixture: ComponentFixture<PostCreateEditModalComponent>;
    let metisService: MetisService;
    let post: Post;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [FormsModule, ReactiveFormsModule],
            providers: [FormBuilder, { provide: MetisService, useClass: MockMetisService }],
            declarations: [PostCreateEditModalComponent, MockPipe(ArtemisTranslatePipe), MockComponent(PostingsMarkdownEditorComponent), MockComponent(PostingsButtonComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostCreateEditModalComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);

                const user = { id: 1, name: 'username', login: 'login' } as User;

                post = {
                    id: 1,
                    author: user,
                    content: 'Post Content',
                } as Post;

                component.posting = post;
                component.ngOnInit();
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should init modal with correct content and title for post without id', () => {
        component.posting.id = undefined;
        component.posting.content = undefined;
        component.ngOnInit();
        expect(component.modalTitle).to.be.equal('artemisApp.metis.createModalTitlePost');
        expect(component.content).to.be.equal('');
    });

    it('should invoke metis service with created post', fakeAsync(() => {
        const metisServiceCreateSpy = spy(metisService, 'createPost');
        const onCreateSpy = spy(component.onCreate, 'emit');
        component.posting.id = undefined;
        const newContent = 'New Content';
        const newTitle = 'New Title';
        component.formGroup.setValue({
            title: newTitle,
            content: newContent,
        });
        component.confirm();
        expect(metisServiceCreateSpy).to.be.have.been.calledWith({
            ...component.posting,
            content: newContent,
            title: newTitle,
        });
        expect(component.posting.creationDate).to.not.be.undefined;
        tick();
        expect(component.isLoading).to.equal(false);
        expect(onCreateSpy).to.have.been.called;
    }));

    it('should invoke metis service with updated post', fakeAsync(() => {
        const metisServiceCreateSpy = spy(metisService, 'updatePost');
        const updatedContent = 'Updated Content';
        const updatedTitle = 'Updated Title';
        component.formGroup.setValue({
            content: updatedContent,
            title: updatedTitle,
        });
        component.confirm();
        expect(metisServiceCreateSpy).to.be.have.been.calledWith({
            ...component.posting,
            content: updatedContent,
            title: updatedTitle,
        });
        tick();
        expect(component.isLoading).to.equal(false);
    }));
});
