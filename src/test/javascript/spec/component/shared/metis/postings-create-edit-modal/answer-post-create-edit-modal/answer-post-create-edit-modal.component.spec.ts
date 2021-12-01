import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { MockNgbModalService } from '../../../../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { PostingMarkdownEditorComponent } from 'app/shared/metis/posting-markdown-editor/posting-markdown-editor.component';
import { PostingButtonComponent } from 'app/shared/metis/posting-button/posting-button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { metisAnswerPostToCreateUser1, metisResolvingAnswerPostUser1, metisAnswerPostUser2 } from '../../../../../helpers/sample/metis-sample-data';

describe('AnswerPostCreateEditModalComponent', () => {
    let component: AnswerPostCreateEditModalComponent;
    let fixture: ComponentFixture<AnswerPostCreateEditModalComponent>;
    let metisService: MetisService;
    let modal: MockNgbModalService;
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
            providers: [FormBuilder, { provide: MetisService, useClass: MockMetisService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AnswerPostCreateEditModalComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                modal = TestBed.inject(NgbModal);
                updatePostingMock = jest.spyOn(component, 'updatePosting');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should init modal with correct content and title for answer post with id', () => {
        component.posting = metisResolvingAnswerPostUser1;
        component.ngOnInit();
        expect(component.modalTitle).toEqual('artemisApp.metis.editPosting');
        expect(component.content).toEqual(metisResolvingAnswerPostUser1.content);
    });

    it('should init modal with correct content and title for answer post without id', () => {
        component.posting = metisAnswerPostToCreateUser1;
        component.ngOnInit();
        expect(component.modalTitle).toEqual('artemisApp.metis.createModalTitleAnswer');
        expect(component.content).toEqual(metisAnswerPostToCreateUser1.content);
    });

    it('should invoke the modalService', () => {
        const componentInstance = { title: String, content: String };
        const result = new Promise((resolve) => resolve(true));
        const modalServiceOpenMock = jest.spyOn(modal, 'open').mockReturnValue(<NgbModalRef>{
            componentInstance,
            result,
        });
        component.open();
        expect(modalServiceOpenMock).toHaveBeenCalled();
    });

    it('should invoke updatePosting when confirming', () => {
        component.posting = metisResolvingAnswerPostUser1;
        component.ngOnChanges();
        component.confirm();
        expect(updatePostingMock).toHaveBeenCalled;
    });

    it('should invoke createPosting when confirming without posting id', () => {
        component.posting = metisResolvingAnswerPostUser1;
        component.ngOnChanges();
        component.confirm();
        expect(updatePostingMock).toHaveBeenCalled;
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
