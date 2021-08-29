import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import * as sinon from 'sinon';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { User } from 'app/core/user/user.model';
import { Post } from 'app/entities/metis/post.model';
import { ContextSelectorOption, PostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { PostingsMarkdownEditorComponent } from 'app/shared/metis/postings-markdown-editor/postings-markdown-editor.component';
import { PostingsButtonComponent } from 'app/shared/metis/postings-button/postings-button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { PostTagSelectorComponent } from 'app/shared/metis/postings-create-edit-modal/post-create-edit-modal/post-tag-selector.component';
import { CourseWideContext, PageType } from 'app/shared/metis/metis.util';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('PostCreateEditModalComponent', () => {
    let component: PostCreateEditModalComponent;
    let fixture: ComponentFixture<PostCreateEditModalComponent>;
    let metisService: MetisService;
    let post: Post;
    let emptyPost: Post;
    let tags: string[];
    let exercise: Exercise;
    let lecture: Lecture;
    let currentContextSelectorOption: ContextSelectorOption;
    let metisServiceGetPageTypeStub: SinonStub;
    let metisServiceCreateSpy: SinonSpy;
    let metisServiceUpdateSpy: SinonSpy;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(FormsModule), MockModule(ReactiveFormsModule)],
            providers: [FormBuilder, { provide: MetisService, useClass: MockMetisService }],
            declarations: [
                PostCreateEditModalComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(PostingsMarkdownEditorComponent),
                MockComponent(PostingsButtonComponent),
                MockComponent(HelpIconComponent),
                MockComponent(PostTagSelectorComponent),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostCreateEditModalComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceGetPageTypeStub = stub(metisService, 'getPageType');
                metisServiceCreateSpy = spy(metisService, 'createPost');
                metisServiceUpdateSpy = spy(metisService, 'updatePost');

                const user = { id: 1, name: 'username', login: 'login' } as User;
                tags = ['tag1', 'tag2'];
                // matches what is returned by mock metis service
                exercise = { id: 1, type: ExerciseType.TEXT, title: 'default exercise' } as Exercise;
                lecture = { id: 1, title: 'default lecture' } as Exercise;

                emptyPost = new Post();

                post = {
                    id: 1,
                    author: user,
                    content: 'Post Content',
                    lecture,
                    tags,
                } as Post;

                currentContextSelectorOption = {
                    courseWideContext: undefined,
                    exercise: undefined,
                    lecture: undefined,
                } as ContextSelectorOption;
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should init modal with correct context, title and content for post without id', () => {
        metisServiceGetPageTypeStub.returns(PageType.OVERVIEW);
        component.posting = { ...emptyPost, courseWideContext: CourseWideContext.TECH_SUPPORT };
        component.ngOnInit();
        expect(component.pageType).to.be.equal(PageType.OVERVIEW);
        expect(component.modalTitle).to.be.equal('artemisApp.metis.createModalTitlePost');

        // mock metis service will return a course with a default exercise as well as a default lecture
        expect(component.course).to.not.be.undefined;
        expect(component.lectures).to.have.length(1);
        expect(component.exercises).to.have.length(1);
        // currently the default selection when opening the model in the overview for creating a new post is the course-wide context TECH_SUPPORT
        expect(component.currentContextSelectorOption).to.be.deep.equal({
            ...currentContextSelectorOption,
            courseWideContext: CourseWideContext.TECH_SUPPORT,
        });
        expect(component.tags).to.be.deep.equal([]);
    });

    it('should invoke metis service with created post', fakeAsync(() => {
        component.posting = { ...emptyPost, courseWideContext: CourseWideContext.TECH_SUPPORT };
        component.ngOnInit();
        // provide some input before creating the post
        component.formGroup.controls.context.setValue({ ...currentContextSelectorOption, lecture });
        const newContent = 'New Content';
        const newTitle = 'New Title';
        const onCreateSpy = spy(component.onCreate, 'emit');
        component.formGroup.setValue({
            title: newTitle,
            content: newContent,
            context: { ...currentContextSelectorOption, lecture },
        });
        // trigger the method that is called on clicking the save button
        component.confirm();
        expect(metisServiceCreateSpy).to.be.have.been.calledWith({
            ...component.posting,
            content: newContent,
            title: newTitle,
            courseWideContext: undefined,
            exercise: undefined,
            lecture,
        });
        expect(component.posting.creationDate).to.not.be.undefined;
        tick();
        expect(component.isLoading).to.equal(false);
        expect(onCreateSpy).to.have.been.called;
    }));

    it('should invoke metis service with updated post in page section', fakeAsync(() => {
        metisServiceGetPageTypeStub.returns(PageType.PAGE_SECTION);
        component.posting = post;
        component.ngOnInit();
        expect(component.pageType).to.be.equal(PageType.PAGE_SECTION);
        expect(component.modalTitle).to.be.equal('artemisApp.metis.editPosting');
        // provide some updated input before creating the post
        component.formGroup.controls.context.setValue({ ...currentContextSelectorOption, lecture });
        const updatedContent = 'Updated Content';
        const updatedTitle = 'Updated Title';
        component.formGroup.setValue({
            content: updatedContent,
            title: updatedTitle,
            context: { exerciseId: exercise.id },
        });
        component.confirm();
        expect(metisServiceUpdateSpy).to.be.have.been.calledWith({
            ...component.posting,
            content: updatedContent,
            title: updatedTitle,
        });
        tick();
        expect(component.isLoading).to.equal(false);
    }));
});
