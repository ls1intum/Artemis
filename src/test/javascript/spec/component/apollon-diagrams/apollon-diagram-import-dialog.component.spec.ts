import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ApollonDiagramImportDialogComponent } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram-import-dialog.component';
import { MockNgbActiveModalService } from '../../helpers/mocks/service/mock-ngb-active-modal.service';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';

describe('ApollonDiagramImportDialog Component', () => {
    let fixture: ComponentFixture<ApollonDiagramImportDialogComponent>;
    let activeModal: NgbActiveModal;
    const apollonDiagramId = 5;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [ApollonDiagramImportDialogComponent],
            providers: [{ provide: NgbActiveModal, useClass: MockNgbActiveModalService }],
            schemas: [],
        })
            .overrideTemplate(ApollonDiagramImportDialogComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ApollonDiagramImportDialogComponent);
                const injector = fixture.debugElement.injector;
                activeModal = injector.get(NgbActiveModal);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('handleDetailOpen', () => {
        fixture.componentInstance.handleDetailOpen(apollonDiagramId);
        expect(fixture.componentInstance.isInEditView).toBeTruthy();
        expect(fixture.componentInstance.apollonDiagramDetailId).toBe(5);
    });

    it('handleDetailClose', () => {
        const modalCloseSpy = jest.spyOn(activeModal, 'close');
        const newDnDQuestion = new DragAndDropQuestion();
        fixture.componentInstance.handleDetailClose(newDnDQuestion);
        expect(modalCloseSpy).toHaveBeenCalledWith(newDnDQuestion);

        fixture.componentInstance.handleDetailClose();
        expect(fixture.componentInstance.isInEditView).toBeFalsy();
    });

    it('closeModal', () => {
        const modalDismissSpy = jest.spyOn(activeModal, 'dismiss');
        fixture.componentInstance.closeModal();
        expect(modalDismissSpy).toHaveBeenCalledOnce();
    });
});
