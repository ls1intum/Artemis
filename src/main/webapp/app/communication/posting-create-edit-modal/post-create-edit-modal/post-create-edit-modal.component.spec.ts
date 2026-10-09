import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommunicationService } from 'app/communication/service/communication.service';
import { MockCommunicationService } from 'test/helpers/mocks/service/mock-communication.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { PostCreateEditModalComponent } from 'app/communication/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { PostingMarkdownEditorComponent } from 'app/communication/posting-markdown-editor/posting-markdown-editor.component';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { PageType } from 'app/communication/communication.util';

import { provideHttpClientTesting } from '@angular/common/http/testing';
import { PostComponent } from 'app/communication/post/post.component';
import {
    communicationCourse,
    communicationExercise,
    communicationPostLectureUser1,
    communicationPostTechSupport,
    communicationPostToCreateUser1,
} from 'test/helpers/sample/communication-sample-data';
import { Channel } from 'app/communication/shared/entities/conversation/channel.model';
import { provideHttpClient } from '@angular/common/http';

describe('PostCreateEditModalComponent', () => {
    let component: PostCreateEditModalComponent;
    let fixture: ComponentFixture<PostCreateEditModalComponent>;
    let communicationService: CommunicationService;
    let communicationServiceGetPageTypeMock: ReturnType<typeof vi.spyOn>;
    let communicationServiceIsAtLeastInstructorStub: ReturnType<typeof vi.spyOn>;
    let communicationServiceCreateStub: ReturnType<typeof vi.spyOn>;
    let communicationServiceUpdateStub: ReturnType<typeof vi.spyOn>;

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
            providers: [provideHttpClient(), provideHttpClientTesting(), FormBuilder, { provide: CommunicationService, useClass: MockCommunicationService }],
        });
        fixture = TestBed.createComponent(PostCreateEditModalComponent);
        component = fixture.componentInstance;
        communicationService = TestBed.inject(CommunicationService);
        communicationServiceGetPageTypeMock = vi.spyOn(communicationService, 'getPageType');
        communicationServiceIsAtLeastInstructorStub = vi.spyOn(communicationService, 'currentUserIsAtLeastInstructorInCourse');
        communicationServiceIsAtLeastInstructorStub.mockReturnValue(false);
        communicationServiceCreateStub = vi.spyOn(communicationService, 'createPost');
        communicationServiceUpdateStub = vi.spyOn(communicationService, 'updatePost');
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('should init modal with correct context, title and content for post without id', () => {
        communicationServiceGetPageTypeMock.mockReturnValue(PageType.OVERVIEW);
        component.posting.set({ ...communicationPostToCreateUser1 });
        fixture.detectChanges();
        expect(component.pageType).toEqual(PageType.OVERVIEW);
        expect(component.modalTitle).toBe('artemisApp.communication.createModalTitlePost');

        // mock communication service will return a course with a default exercise as well as a default lecture
        expect(component.course).not.toBeNull();
        expect(component.lectures).toHaveLength(communicationCourse.lectures!.length);
        expect(component.exercises).toHaveLength(communicationCourse.exercises!.length);
        expect(component.similarPosts).toHaveLength(0);
        // currently the default selection when opening the model in the overview for creating a new post is the course-wide context TECH_SUPPORT
        expect(component.currentContextSelectorOption).toEqual({});
    });

    it('should reset context selection on changes', () => {
        communicationServiceGetPageTypeMock.mockReturnValue(PageType.OVERVIEW);
        component.posting.set({ ...communicationPostTechSupport });
        fixture.detectChanges();
        component.currentContextSelectorOption.conversation = { id: 1 } as Channel;
        // Trigger a posting change to reset context
        component.posting.set({ ...communicationPostTechSupport });
        fixture.detectChanges();
        // change to Organization as course-wide topic should be reset to Tech Support
        expect(component.currentContextSelectorOption).toEqual({ conversation: communicationPostTechSupport.conversation });
    });

    it('should invoke communication service with created post in overview', () => {
        vi.useFakeTimers();
        communicationServiceGetPageTypeMock.mockReturnValue(PageType.OVERVIEW);
        component.posting.set(communicationPostToCreateUser1);
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
        expect(communicationServiceCreateStub).toHaveBeenCalledWith({
            ...component.posting()!,
            content: newContent,
            title: newTitle,
        });
        vi.advanceTimersByTime(0);
        expect(component.isLoading()).toBe(false);
        expect(onCreateSpy).toHaveBeenCalledOnce();
        vi.useRealTimers();
    });

    it('should invoke communication service with created announcement in overview', () => {
        vi.useFakeTimers();
        communicationServiceIsAtLeastInstructorStub.mockReturnValue(true);
        communicationServiceGetPageTypeMock.mockReturnValue(PageType.OVERVIEW);
        component.posting.set(communicationPostToCreateUser1);
        fixture.detectChanges();
        const newContent = 'New Content';
        const newTitle = 'New Title';
        const onCreateSpy = vi.spyOn(component.onCreate, 'emit');
        component.formGroup.setValue({
            title: newTitle,
            content: newContent,
            context: { conversationId: communicationPostToCreateUser1.conversation?.id, exercise: undefined },
        });
        component.confirm();
        expect(communicationServiceCreateStub).toHaveBeenCalledWith({
            ...component.posting()!,
            content: newContent,
            title: newTitle,
        });
        vi.advanceTimersByTime(800);
        expect(component.isLoading()).toBe(false);
        expect(onCreateSpy).toHaveBeenCalledOnce();
        vi.useRealTimers();
    });

    it('should invoke communication service with updated post in page section', () => {
        vi.useFakeTimers();
        communicationServiceGetPageTypeMock.mockReturnValue(PageType.PAGE_SECTION);
        component.posting.set(communicationPostLectureUser1);
        fixture.detectChanges();
        expect(component.pageType).toEqual(PageType.PAGE_SECTION);
        expect(component.modalTitle).toBe('artemisApp.communication.editPosting');
        const updatedContent = 'Updated Content';
        const updatedTitle = 'Updated Title';
        component.formGroup.setValue({
            content: updatedContent,
            title: updatedTitle,
            context: { exerciseId: communicationExercise.id },
        });
        vi.advanceTimersByTime(800);
        component.confirm();
        expect(communicationServiceUpdateStub).toHaveBeenCalledWith({
            ...component.posting()!,
            content: updatedContent,
            title: updatedTitle,
        });
        vi.advanceTimersByTime(0);
        expect(component.isLoading()).toBe(false);
        vi.useRealTimers();
    });

    it('should set isDialogVisible to true when open is called', () => {
        expect(component.isDialogVisible()).toBe(false);
        component.open();
        expect(component.isDialogVisible()).toBe(true);
    });
});
