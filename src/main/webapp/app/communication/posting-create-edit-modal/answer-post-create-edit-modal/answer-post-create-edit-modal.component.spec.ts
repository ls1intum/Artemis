import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/communication/service/metis.service';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { AnswerPostCreateEditModalComponent } from 'app/communication/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { PostingMarkdownEditorComponent } from 'app/communication/posting-markdown-editor/posting-markdown-editor.component';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { ViewContainerRef } from '@angular/core';
import { MockViewContainerRef } from 'test/helpers/mocks/service/mock-view-container-ref.service';
import { metisAnswerPostToCreateUser1, metisAnswerPostUser2, metisResolvingAnswerPostUser1 } from 'test/helpers/sample/metis-sample-data';

describe('AnswerPostCreateEditModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: AnswerPostCreateEditModalComponent;
    let fixture: ComponentFixture<AnswerPostCreateEditModalComponent>;
    let metisService: MetisService;
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
            providers: [FormBuilder, { provide: MetisService, useClass: MockMetisService }, { provide: ViewContainerRef, useClass: MockViewContainerRef }],
        });
        fixture = TestBed.createComponent(AnswerPostCreateEditModalComponent);
        component = fixture.componentInstance;
        metisService = TestBed.inject(MetisService);
        updatePostingMock = vi.spyOn(component, 'updatePosting');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should init modal with correct content and title for answer post with id', { timeout: 30000 }, () => {
        component.posting.set(metisResolvingAnswerPostUser1);
        component.ngOnInit();
        expect(component.modalTitle).toBe('artemisApp.metis.editPosting');
        expect(component.content).toEqual(metisResolvingAnswerPostUser1.content);
    });

    it('should init modal with correct content and title for answer post without id', () => {
        component.posting.set(metisAnswerPostToCreateUser1);
        component.ngOnInit();
        expect(component.modalTitle).toBe('artemisApp.metis.createModalTitleAnswer');
        expect(component.content).toEqual(metisAnswerPostToCreateUser1.content);
    });

    it('should invoke create embedded view', () => {
        component.posting.set(metisResolvingAnswerPostUser1);
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
        component.posting.set(metisResolvingAnswerPostUser1);
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
        component.posting.set(metisResolvingAnswerPostUser1);
        fixture.detectChanges();
        component.confirm();
        expect(updatePostingMock).toHaveBeenCalledOnce();
    });

    it('should invoke createPosting when confirming without posting id', () => {
        const createPostingMock = vi.spyOn(component, 'createPosting');
        component.posting.set(metisAnswerPostToCreateUser1);
        fixture.detectChanges();
        component.confirm();
        expect(createPostingMock).toHaveBeenCalledOnce();
    });

    it('should invoke metis service with created answer post', () => {
        const metisServiceCreateSpy = vi.spyOn(metisService, 'createAnswerPost');
        const onCreateSpy = vi.spyOn(component.onCreate, 'emit');
        component.posting.set(metisAnswerPostToCreateUser1);
        fixture.detectChanges();
        const newContent = 'New Content';
        component.formGroup.setValue({
            content: newContent,
        });
        component.confirm();
        expect(metisServiceCreateSpy).toHaveBeenCalledWith({ ...component.posting()!, content: newContent });
        expect(component.isLoading).toBeFalsy();
        expect(onCreateSpy).toHaveBeenCalledOnce();
    });

    it('should invoke metis service with updated answer post', () => {
        const metisServiceCreateSpy = vi.spyOn(metisService, 'updateAnswerPost');
        component.posting.set(metisAnswerPostUser2);
        fixture.detectChanges();
        const updatedContent = 'Updated Content';
        component.formGroup.setValue({
            content: updatedContent,
        });
        component.confirm();
        expect(metisServiceCreateSpy).toHaveBeenCalledWith({ ...component.posting()!, content: updatedContent });
        expect(component.isLoading).toBeFalsy();
    });

    it('should update content when posting content changed', () => {
        component.posting.set({ ...metisAnswerPostUser2, content: 'New content' });
        fixture.detectChanges();
        expect(component.content).toEqual(component.posting()!.content);
    });
});
