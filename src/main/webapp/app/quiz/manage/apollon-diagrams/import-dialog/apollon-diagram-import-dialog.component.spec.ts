import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ApollonDiagramImportDialogComponent } from 'app/quiz/manage/apollon-diagrams/import-dialog/apollon-diagram-import-dialog.component';
import { MockNgbActiveModalService } from 'test/helpers/mocks/service/mock-ngb-active-modal.service';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';

describe('ApollonDiagramImportDialog Component', () => {
    let fixture: ComponentFixture<ApollonDiagramImportDialogComponent>;
    let activeModal: NgbActiveModal;
    const apollonDiagramId = 5;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ApollonDiagramImportDialogComponent],
            providers: [{ provide: NgbActiveModal, useClass: MockNgbActiveModalService }],
        })
            .overrideTemplate(ApollonDiagramImportDialogComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ApollonDiagramImportDialogComponent);
                activeModal = TestBed.inject(NgbActiveModal);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('handleDetailOpen', () => {
        fixture.componentInstance.handleDetailOpen(apollonDiagramId);
        expect(fixture.componentInstance.isInEditView()).toBeTruthy();
        expect(fixture.componentInstance.apollonDiagramDetailId()).toBe(5);
    });

    it('handleDetailClose', () => {
        const modalCloseSpy = jest.spyOn(activeModal, 'close');
        const newDnDQuestion = new DragAndDropQuestion();
        fixture.componentInstance.handleDetailClose(newDnDQuestion);
        expect(modalCloseSpy).toHaveBeenCalledWith(newDnDQuestion);

        fixture.componentInstance.handleDetailClose();
        expect(fixture.componentInstance.isInEditView()).toBeFalsy();
    });

    it('closeModal', () => {
        const modalDismissSpy = jest.spyOn(activeModal, 'dismiss');
        fixture.componentInstance.closeModal();
        expect(modalDismissSpy).toHaveBeenCalledOnce();
    });
});
