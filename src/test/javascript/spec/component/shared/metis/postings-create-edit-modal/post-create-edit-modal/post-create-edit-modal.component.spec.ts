import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { PostingMarkdownEditorComponent } from 'app/shared/metis/posting-markdown-editor/posting-markdown-editor.component';
import { PostingButtonComponent } from 'app/shared/metis/posting-button/posting-button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { PostTagSelectorComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-tag-selector/post-tag-selector.component';
import { CourseWideContext, PageType } from 'app/shared/metis/metis.util';
import { NgbAccordion, NgbModal, NgbModalRef, NgbPanel } from '@ng-bootstrap/ng-bootstrap';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ArtemisTestModule } from '../../../../../test.module';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { metisCourse, metisCoursePosts, metisExercise, metisLecture, metisPostLectureUser1, metisPostToCreateUser1 } from '../../../../../helpers/sample/metis-sample-data';
import { MockNgbModalService } from '../../../../../helpers/mocks/service/mock-ngb-modal.service';

describe('PostCreateEditModalComponent', () => {
    let component: PostCreateEditModalComponent;
    let fixture: ComponentFixture<PostCreateEditModalComponent>;
    let metisService: MetisService;
    let modal: NgbModal;
    let metisServiceGetPageTypeMock: jest.SpyInstance;
    let metisServiceIsAtLeastInstructorStub: jest.SpyInstance;
    let metisServiceCreateStub: jest.SpyInstance;
    let metisServiceUpdateStub: jest.SpyInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule, MockModule(FormsModule), MockModule(ReactiveFormsModule)],
            declarations: [
                PostCreateEditModalComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(PostComponent),
                MockComponent(PostingMarkdownEditorComponent),
                MockComponent(PostingButtonComponent),
                MockComponent(HelpIconComponent),
                MockComponent(PostTagSelectorComponent),
                MockComponent(NgbAccordion),
                MockDirective(NgbPanel),
            ],
            providers: [FormBuilder, { provide: MetisService, useClass: MockMetisService }, { provide: NgbModal, useClass: MockNgbModalService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostCreateEditModalComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                modal = TestBed.inject(NgbModal);
                metisServiceGetPageTypeMock = jest.spyOn(metisService, 'getPageType');
                metisServiceIsAtLeastInstructorStub = jest.spyOn(metisService, 'metisUserIsAtLeastInstructorInCourse');
                metisServiceIsAtLeastInstructorStub.mockReturnValue(false);
                metisServiceCreateStub = jest.spyOn(metisService, 'createPost');
                metisServiceUpdateStub = jest.spyOn(metisService, 'updatePost');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should init modal with correct context, title and content for post without id', () => {
        metisServiceGetPageTypeMock.mockReturnValue(PageType.OVERVIEW);
        component.posting = { ...metisPostToCreateUser1, courseWideContext: CourseWideContext.TECH_SUPPORT };
        component.ngOnInit();
        component.ngOnChanges();
        expect(component.pageType).toEqual(PageType.OVERVIEW);
        expect(component.modalTitle).toBe('artemisApp.metis.createModalTitlePost');

        // mock metis service will return a course with a default exercise as well as a default lecture
        expect(component.course).not.toBeNull();
        expect(component.lectures).toHaveLength(metisCourse.lectures!.length);
        expect(component.exercises).toHaveLength(metisCourse.exercises!.length);
        expect(component.similarPosts).toHaveLength(0);
        // currently the default selection when opening the model in the overview for creating a new post is the course-wide context TECH_SUPPORT
        expect(component.currentContextSelectorOption).toEqual({
            courseWideContext: CourseWideContext.TECH_SUPPORT,
            exercise: undefined,
            lecture: undefined,
        });
        expect(component.tags).toEqual([]);
    });

    it('should reset context selection on changes', () => {
        metisServiceGetPageTypeMock.mockReturnValue(PageType.OVERVIEW);
        component.posting = { ...metisPostToCreateUser1, courseWideContext: CourseWideContext.TECH_SUPPORT };
        component.ngOnInit();
        component.currentContextSelectorOption.courseWideContext = CourseWideContext.ORGANIZATION;
        component.ngOnChanges();
        // change to Organization as course-wide topic should be reset to Tech Support
        expect(component.currentContextSelectorOption).toEqual({
            courseWideContext: CourseWideContext.TECH_SUPPORT,
            exercise: undefined,
            lecture: undefined,
        });
        expect(component.tags).toEqual([]);
    });

    it('should invoke metis service with created post in overview', fakeAsync(() => {
        metisServiceGetPageTypeMock.mockReturnValue(PageType.OVERVIEW);
        component.posting = metisPostToCreateUser1;
        component.ngOnChanges();
        // provide some input before creating the post
        const newContent = 'New Content';
        const newTitle = 'New Title';
        const onCreateSpy = jest.spyOn(component.onCreate, 'emit');
        component.formGroup.setValue({
            title: newTitle,
            content: newContent,
            context: { courseWideContext: undefined, exercise: undefined, metisLecture },
        });
        // debounce time of title input field
        tick(800);
        expect(component.similarPosts).toEqual(metisCoursePosts.slice(0, 5));
        // trigger the method that is called on clicking the save button
        component.confirm();
        expect(metisServiceCreateStub).toHaveBeenCalledWith({
            ...component.posting,
            content: newContent,
            title: newTitle,
            courseWideContext: undefined,
            exercise: undefined,
            metisLecture,
        });
        tick();
        expect(component.isLoading).toBeFalse();
        expect(onCreateSpy).toHaveBeenCalledOnce();
    }));

    it('should invoke metis service with created announcement in overview', fakeAsync(() => {
        metisServiceIsAtLeastInstructorStub.mockReturnValue(true);
        metisServiceGetPageTypeMock.mockReturnValue(PageType.OVERVIEW);
        component.posting = metisPostToCreateUser1;
        component.ngOnInit();
        component.ngOnChanges();
        // provide some input before creating the post
        const newContent = 'New Content';
        const newTitle = 'New Title';
        const onCreateSpy = jest.spyOn(component.onCreate, 'emit');
        component.formGroup.setValue({
            title: newTitle,
            content: newContent,
            context: { courseWideContext: CourseWideContext.ANNOUNCEMENT, exercise: undefined, undefined },
        });
        // trigger the method that is called on clicking the save button
        component.confirm();
        expect(metisServiceCreateStub).toHaveBeenCalledWith({
            ...component.posting,
            content: newContent,
            title: newTitle,
            courseWideContext: CourseWideContext.ANNOUNCEMENT,
            exercise: undefined,
            lecture: undefined,
        });
        // debounce time of title input field
        tick(800);
        expect(component.isLoading).toBeFalse();
        expect(onCreateSpy).toHaveBeenCalledOnce();
    }));

    it('should invoke metis service with updated post in page section', fakeAsync(() => {
        metisServiceGetPageTypeMock.mockReturnValue(PageType.PAGE_SECTION);
        component.posting = metisPostLectureUser1;
        component.ngOnChanges();
        expect(component.pageType).toEqual(PageType.PAGE_SECTION);
        expect(component.modalTitle).toBe('artemisApp.metis.editPosting');
        // provide some updated input before creating the post
        const updatedContent = 'Updated Content';
        const updatedTitle = 'Updated Title';
        component.formGroup.setValue({
            content: updatedContent,
            title: updatedTitle,
            context: { exerciseId: metisExercise.id },
        });
        // debounce time of title input field
        tick(800);
        component.confirm();
        expect(metisServiceUpdateStub).toHaveBeenCalledWith({
            ...component.posting,
            content: updatedContent,
            title: updatedTitle,
        });
        tick();
        expect(component.isLoading).toBeFalse();
    }));

    it('should invoke the modalService', () => {
        const componentInstance = { title: String, content: String };
        const result = new Promise((resolve) => resolve(true));
        const modalServiceOpenMock = jest.spyOn(modal, 'open').mockReturnValue(<NgbModalRef>{
            componentInstance,
            result,
        });

        component.open();
        expect(modalServiceOpenMock).toHaveBeenCalledOnce();
    });
});
