import { ComponentFixture, fakeAsync, inject, TestBed, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArtemisTestModule } from '../../test.module';
import { ParticipationSubmissionDeleteDialogComponent } from 'app/exercises/shared/participation-submission/participation-submission-delete-dialog.component';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';

describe('ParticipationSubmissionDeleteDialogComponent', () => {
    describe('Manage delete of submissions in participation-submission view', () => {
        let comp: ParticipationSubmissionDeleteDialogComponent;
        let fixture: ComponentFixture<ParticipationSubmissionDeleteDialogComponent>;
        let service: SubmissionService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [ParticipationSubmissionDeleteDialogComponent],
            })
                .overrideTemplate(ParticipationSubmissionDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ParticipationSubmissionDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(SubmissionService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('confirmDelete', () => {
            it('Should call delete service on confirmDelete', inject(
                [],
                fakeAsync(() => {
                    // GIVEN
                    spyOn(service, 'delete').and.returnValue(of({}));

                    // WHEN
                    comp.confirmDelete(123);
                    tick();

                    // THEN
                    expect(service.delete).toHaveBeenCalledWith(123);
                    expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    expect(mockEventManager.broadcastSpy).toHaveBeenCalled();
                }),
            ));
        });
    });
});
