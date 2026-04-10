import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/communication/service/metis.service';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { PostCreateEditModalComponent } from 'app/communication/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { PostingMarkdownEditorComponent } from 'app/communication/posting-markdown-editor/posting-markdown-editor.component';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { PageType } from 'app/communication/metis.util';

import { provideHttpClientTesting } from '@angular/common/http/testing';
import { PostComponent } from 'app/communication/post/post.component';
import { metisCourse, metisExercise, metisPostLectureUser1, metisPostTechSupport, metisPostToCreateUser1 } from 'test/helpers/sample/metis-sample-data';
import { Channel } from 'app/communication/shared/entities/conversation/channel.model';
import { provideHttpClient } from '@angular/common/http';

describe('PostCreateEditModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: PostCreateEditModalComponent;
    let fixture: ComponentFixture<PostCreateEditModalComponent>;
    let metisService: MetisService;
    let metisServiceGetPageTypeMock: ReturnType<typeof vi.spyOn>;
    let metisServiceIsAtLeastInstructorStub: ReturnType<typeof vi.spyOn>;
    let metisServiceCreateStub: ReturnType<typeof vi.spyOn>;
    let metisServiceUpdateStub: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                MockModule(FormsModule),
                MockModule(ReactiveFormsModule),
                PostCreateEditModalComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(PostComponent),
                MockComponent(PostingMarkdownEditorComponent),
                MockComponent(PostingButtonComponent),
                MockComponent(HelpIconComponent),
            ],
            providers: [provideHttpClient(), provideHttpClientTesting(), FormBuilder, { provide: MetisService, useClass: MockMetisService }],
        });
        fixture = TestBed.createComponent(PostCreateEditModalComponent);
        component = fixture.componentInstance;
        metisService = TestBed.inject(MetisService);
        metisServiceGetPageTypeMock = vi.spyOn(metisService, 'getPageType');
        metisServiceIsAtLeastInstructorStub = vi.spyOn(metisService, 'metisUserIsAtLeastInstructorInCourse');
        metisServiceIsAtLeastInstructorStub.mockReturnValue(false);
        metisServiceCreateStub = vi.spyOn(metisService, 'createPost');
        metisServiceUpdateStub = vi.spyOn(metisService, 'updatePost');
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('should init modal with correct context, title and content for post without id', () => {
        metisServiceGetPageTypeMock.mockReturnValue(PageType.OVERVIEW);
        component.posting.set({ ...metisPostToCreateUser1 });
        fixture.detectChanges();
        expect(component.pageType).toEqual(PageType.OVERVIEW);
        expect(component.modalTitle).toBe('artemisApp.metis.createModalTitlePost');

        // mock metis service will return a course with a default exercise as well as a default lecture
        expect(component.course).not.toBeNull();
        expect(component.lectures).toHaveLength(metisCourse.lectures!.length);
        expect(component.exercises).toHaveLength(metisCourse.exercises!.length);
        expect(component.similarPosts).toHaveLength(0);
        // currently the default selection when opening the model in the overview for creating a new post is the course-wide context TECH_SUPPORT
        expect(component.currentContextSelectorOption).toEqual({});
    });

    it('should reset context selection on changes', () => {
        metisServiceGetPageTypeMock.mockReturnValue(PageType.OVERVIEW);
        component.posting.set({ ...metisPostTechSupport });
        fixture.detectChanges();
        component.currentContextSelectorOption.conversation = { id: 1 } as Channel;
        // Trigger a posting change to reset context
        component.posting.set({ ...metisPostTechSupport });
        fixture.detectChanges();
        // change to Organization as course-wide topic should be reset to Tech Support
        expect(component.currentContextSelectorOption).toEqual({ conversation: metisPostTechSupport.conversation });
    });

    it('should invoke metis service with created post in overview', () => {
        vi.useFakeTimers();
        metisServiceGetPageTypeMock.mockReturnValue(PageType.OVERVIEW);
        component.posting.set(metisPostToCreateUser1);
        fixture.detectChanges();
        const newContent = 'New Content';
        const newTitle = 'New Title';
        const onCreateSpy = vi.spyOn(component.onCreate, 'emit');
        component.formGroup.setValue({
            title: newTitle,
            content: newContent,
            context: {},
        });
        vi.advanceTimersByTime(800);
        expect(component.similarPosts).toEqual([]);
        component.confirm();
        expect(metisServiceCreateStub).toHaveBeenCalledWith({
            ...component.posting()!,
            content: newContent,
            title: newTitle,
        });
        vi.advanceTimersByTime(0);
        expect(component.isLoading).toBe(false);
        expect(onCreateSpy).toHaveBeenCalledOnce();
        vi.useRealTimers();
    });

    it('should invoke metis service with created announcement in overview', () => {
        vi.useFakeTimers();
        metisServiceIsAtLeastInstructorStub.mockReturnValue(true);
        metisServiceGetPageTypeMock.mockReturnValue(PageType.OVERVIEW);
        component.posting.set(metisPostToCreateUser1);
        fixture.detectChanges();
        const newContent = 'New Content';
        const newTitle = 'New Title';
        const onCreateSpy = vi.spyOn(component.onCreate, 'emit');
        component.formGroup.setValue({
            title: newTitle,
            content: newContent,
            context: { conversationId: metisPostToCreateUser1.conversation?.id, exercise: undefined },
        });
        component.confirm();
        expect(metisServiceCreateStub).toHaveBeenCalledWith({
            ...component.posting()!,
            content: newContent,
            title: newTitle,
        });
        vi.advanceTimersByTime(800);
        expect(component.isLoading).toBe(false);
        expect(onCreateSpy).toHaveBeenCalledOnce();
        vi.useRealTimers();
    });

    it('should invoke metis service with updated post in page section', () => {
        vi.useFakeTimers();
        metisServiceGetPageTypeMock.mockReturnValue(PageType.PAGE_SECTION);
        component.posting.set(metisPostLectureUser1);
        fixture.detectChanges();
        expect(component.pageType).toEqual(PageType.PAGE_SECTION);
        expect(component.modalTitle).toBe('artemisApp.metis.editPosting');
        const updatedContent = 'Updated Content';
        const updatedTitle = 'Updated Title';
        component.formGroup.setValue({
            content: updatedContent,
            title: updatedTitle,
            context: { exerciseId: metisExercise.id },
        });
        vi.advanceTimersByTime(800);
        component.confirm();
        expect(metisServiceUpdateStub).toHaveBeenCalledWith({
            ...component.posting()!,
            content: updatedContent,
            title: updatedTitle,
        });
        vi.advanceTimersByTime(0);
        expect(component.isLoading).toBe(false);
        vi.useRealTimers();
    });

    it('should set isDialogVisible to true when open is called', () => {
        expect(component.isDialogVisible()).toBe(false);
        component.open();
        expect(component.isDialogVisible()).toBe(true);
    });
});
