import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { PostingMarkdownEditorComponent } from 'app/shared/metis/posting-markdown-editor/posting-markdown-editor.component';
import { PostingButtonComponent } from 'app/shared/metis/posting-button/posting-button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ViewContainerRef } from '@angular/core';
import { MockViewContainerRef } from '../../../../../helpers/mocks/service/mock-view-container-ref.service';
import { metisAnswerPostToCreateUser1, metisAnswerPostUser2, metisResolvingAnswerPostUser1 } from '../../../../../helpers/sample/metis-sample-data';

describe('AnswerPostCreateEditModalComponent', () => {
    let component: AnswerPostCreateEditModalComponent;
    let fixture: ComponentFixture<AnswerPostCreateEditModalComponent>;
    let metisService: MetisService;
    let viewContainerRef: ViewContainerRef;
    let updatePostingMock: jest.SpyInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(FormsModule), MockModule(ReactiveFormsModule)],
            declarations: [
                AnswerPostCreateEditModalComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(PostingMarkdownEditorComponent),
                MockComponent(PostingButtonComponent),
                MockComponent(HelpIconComponent),
            ],
            providers: [FormBuilder, { provide: MetisService, useClass: MockMetisService }, { provide: ViewContainerRef, useClass: MockViewContainerRef }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AnswerPostCreateEditModalComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                viewContainerRef = TestBed.inject(ViewContainerRef);
                updatePostingMock = jest.spyOn(component, 'updatePosting');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should init modal with correct content and title for answer post with id', () => {
        component.posting = metisResolvingAnswerPostUser1;
        component.ngOnInit();
        expect(component.modalTitle).toBe('artemisApp.metis.editPosting');
        expect(component.content).toEqual(metisResolvingAnswerPostUser1.content);
    });

    it('should init modal with correct content and title for answer post without id', () => {
        component.posting = metisAnswerPostToCreateUser1;
        component.ngOnInit();
        expect(component.modalTitle).toBe('artemisApp.metis.createModalTitleAnswer');
        expect(component.content).toEqual(metisAnswerPostToCreateUser1.content);
    });

    it('should invoke create embedded view', () => {
        component.posting = metisResolvingAnswerPostUser1;
        const viewContainerRefCreateEmbeddedView = jest.spyOn(viewContainerRef, 'createEmbeddedView');
        component.createEditAnswerPostContainerRef = viewContainerRef;
        fixture.detectChanges();
        component.open();
        expect(viewContainerRefCreateEmbeddedView).toHaveBeenCalled();
    });

    it('should invoke clear embedded view', () => {
        component.posting = metisResolvingAnswerPostUser1;
        const viewContainerRefClear = jest.spyOn(viewContainerRef, 'clear');
        component.createEditAnswerPostContainerRef = viewContainerRef;
        fixture.detectChanges();
        component.close();
        expect(viewContainerRefClear).toHaveBeenCalled();
    });

    it('should invoke updatePosting when confirming', () => {
        component.posting = metisResolvingAnswerPostUser1;
        component.ngOnChanges();
        component.confirm();
        expect(updatePostingMock).toHaveBeenCalledOnce();
    });

    it('should invoke createPosting when confirming without posting id', () => {
        component.posting = metisResolvingAnswerPostUser1;
        component.ngOnChanges();
        component.confirm();
        expect(updatePostingMock).toHaveBeenCalledOnce();
    });

    it('should invoke metis service with created answer post', fakeAsync(() => {
        const metisServiceCreateSpy = jest.spyOn(metisService, 'createAnswerPost');
        const onCreateSpy = jest.spyOn(component.onCreate, 'emit');
        component.posting = metisAnswerPostToCreateUser1;
        component.ngOnChanges();
        const newContent = 'New Content';
        component.formGroup.setValue({
            content: newContent,
        });
        component.confirm();
        expect(metisServiceCreateSpy).toHaveBeenCalledWith({ ...component.posting, content: newContent });
        tick();
        expect(component.isLoading).toBeFalsy();
        expect(onCreateSpy).toHaveBeenCalled();
    }));

    it('should invoke metis service with updated answer post', fakeAsync(() => {
        const metisServiceCreateSpy = jest.spyOn(metisService, 'updateAnswerPost');
        component.posting = metisAnswerPostUser2;
        component.ngOnChanges();
        const updatedContent = 'Updated Content';
        component.formGroup.setValue({
            content: updatedContent,
        });
        component.confirm();
        expect(metisServiceCreateSpy).toHaveBeenCalledWith({ ...component.posting, content: updatedContent });
        tick();
        expect(component.isLoading).toBeFalsy();
    }));

    it('should update content when posting content changed', () => {
        component.posting = metisAnswerPostUser2;
        component.posting.content = 'New content';
        component.ngOnChanges();
        expect(component.content).toEqual(component.posting.content);
    });
});
