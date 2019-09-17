import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisTestModule } from '../test.module';
import { DeleteDialogData, DeleteDialogService } from 'app/shared/delete-dialog/delete-dialog.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import * as sinon from 'sinon';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('Delete Dialog Service', () => {
    let service: DeleteDialogService;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, HttpClientTestingModule],
            providers: [DeleteDialogService],
        });

        service = TestBed.get(DeleteDialogService);
        modalService = TestBed.get(NgbModal);
    });

    it('should open delete dialog', () => {
        expect(service.modalRef).to.be.undefined;
        const data: DeleteDialogData = {
            entityTitle: 'title',
            deleteQuestion: 'artemisApp.exercise.delete.question',
            deleteConfirmationText: 'artemisApp.exercise.delete.typeNameToConfirm',
        };
        const modalSpy = sinon.spy(modalService, 'open');
        service.openDeleteDialog(data).subscribe(() => {
            expect(service.modalRef).to.be.null;
        });
        expect(modalSpy.callCount).to.be.equal(1);
        let call = modalSpy.getCall(0);
        let args = call.args;
        expect(args[0].name).to.be.equal('DeleteDialogComponent');
        expect(args[1]).to.be.not.null;
    });
});
