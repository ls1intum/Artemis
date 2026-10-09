import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommunicationService } from 'app/communication/service/communication.service';
import { MockCommunicationService } from 'test/helpers/mocks/service/mock-communication.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { AnswerPostCreateEditModalComponent } from 'app/communication/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { PostingMarkdownEditorComponent } from 'app/communication/posting-markdown-editor/posting-markdown-editor.component';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { ViewContainerRef } from '@angular/core';
import { MockViewContainerRef } from 'test/helpers/mocks/service/mock-view-container-ref.service';
import { communicationAnswerPostToCreateUser1, communicationAnswerPostUser2, communicationResolvingAnswerPostUser1 } from 'test/helpers/sample/communication-sample-data';

describe('AnswerPostCreateEditModalComponent', () => {
    let component: AnswerPostCreateEditModalComponent;
    let fixture: ComponentFixture<AnswerPostCreateEditModalComponent>;
    let communicationService: CommunicationService;
    let updatePostingMock: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                MockModule(FormsModule),
                MockModule(ReactiveFormsModule),
                AnswerPostCreateEditModalComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(PostingMarkdownEditorComponent),
                MockComponent(PostingButtonComponent),
                MockComponent(HelpIconComponent),
            ],
            providers: [FormBuilder, { provide: CommunicationService, useClass: MockCommunicationService }, { provide: ViewContainerRef, useClass: MockViewContainerRef }],
        });
        fixture = TestBed.createComponent(AnswerPostCreateEditModalComponent);
        component = fixture.componentInstance;
        communicationService = TestBed.inject(CommunicationService);
        updatePostingMock = vi.spyOn(component, 'updatePosting');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should init modal with correct content and title for answer post with id', { timeout: 30000 }, () => {
        component.posting.set(communicationResolvingAnswerPostUser1);
        component.ngOnInit();
        expect(component.modalTitle).toBe('artemisApp.communication.editPosting');
        expect(component.content).toEqual(communicationResolvingAnswerPostUser1.content);
    });

    it('should init modal with correct content and title for answer post without id', () => {
        component.posting.set(communicationAnswerPostToCreateUser1);
        component.ngOnInit();
        expect(component.modalTitle).toBe('artemisApp.communication.createModalTitleAnswer');
        expect(component.content).toEqual(communicationAnswerPostToCreateUser1.content);
    });

    it('should invoke create embedded view', () => {
        component.posting.set(communicationResolvingAnswerPostUser1);
        const mockClear = vi.fn();
        const mockCreateEmbeddedView = vi.fn();

        fixture.componentRef.setInput('createEditAnswerPostContainerRef', {
            clear: mockClear,
            createEmbeddedView: mockCreateEmbeddedView,
        } as unknown as ViewContainerRef);
        fixture.changeDetectorRef.detectChanges();
        component.open();
        expect(mockCreateEmbeddedView).toHaveBeenCalledOnce();
    });

    it('should invoke clear embedded view', () => {
        component.posting.set(communicationResolvingAnswerPostUser1);
        const mockClear = vi.fn();
        const mockCreateEmbeddedView = vi.fn();

        fixture.componentRef.setInput('createEditAnswerPostContainerRef', {
            clear: mockClear,
            createEmbeddedView: mockCreateEmbeddedView,
        } as unknown as ViewContainerRef);
        fixture.changeDetectorRef.detectChanges();
        component.close();
        expect(mockClear).toHaveBeenCalledOnce();
    });

    it('should invoke updatePosting when confirming', () => {
        component.posting.set(communicationResolvingAnswerPostUser1);
        fixture.detectChanges();
        component.confirm();
        expect(updatePostingMock).toHaveBeenCalledOnce();
    });

    it('should invoke createPosting when confirming without posting id', () => {
        const createPostingMock = vi.spyOn(component, 'createPosting');
        component.posting.set(communicationAnswerPostToCreateUser1);
        fixture.detectChanges();
        component.confirm();
        expect(createPostingMock).toHaveBeenCalledOnce();
    });

    it('should invoke communication service with created answer post', () => {
        const communicationServiceCreateSpy = vi.spyOn(communicationService, 'createAnswerPost');
        const onCreateSpy = vi.spyOn(component.onCreate, 'emit');
        component.posting.set(communicationAnswerPostToCreateUser1);
        fixture.detectChanges();
        const newContent = 'New Content';
        component.formGroup.setValue({
            content: newContent,
        });
        component.confirm();
        expect(communicationServiceCreateSpy).toHaveBeenCalledWith({ ...component.posting()!, content: newContent });
        expect(component.isLoading()).toBeFalsy();
        expect(onCreateSpy).toHaveBeenCalledOnce();
    });

    it('should invoke communication service with updated answer post', () => {
        const communicationServiceCreateSpy = vi.spyOn(communicationService, 'updateAnswerPost');
        component.posting.set(communicationAnswerPostUser2);
        fixture.detectChanges();
        const updatedContent = 'Updated Content';
        component.formGroup.setValue({
            content: updatedContent,
        });
        component.confirm();
        expect(communicationServiceCreateSpy).toHaveBeenCalledWith({ ...component.posting()!, content: updatedContent });
        expect(component.isLoading()).toBeFalsy();
    });

    it('should update content when posting content changed', () => {
        component.posting.set({ ...communicationAnswerPostUser2, content: 'New content' });
        fixture.detectChanges();
        expect(component.content).toEqual(component.posting()!.content);
    });
});
