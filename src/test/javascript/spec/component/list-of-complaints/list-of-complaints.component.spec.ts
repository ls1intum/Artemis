import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ComplaintService } from 'app/complaints/complaint.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ListOfComplaintsComponent } from 'app/complaints/list-of-complaints/list-of-complaints.component';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { NgModel } from '@angular/forms';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { SortService } from 'app/shared/service/sort.service';
import { MockRouter } from '../../helpers/mocks/mock-router';

describe('ListOfComplaintsComponent', () => {
    let comp: ListOfComplaintsComponent;
    let fixture: ComponentFixture<ListOfComplaintsComponent>;

    const complaints = [{ accepted: undefined }, { accepted: true }, { accepted: false }];

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ListOfComplaintsComponent,
                MockComponent(AlertComponent),
                MockDirective(NgModel),
                MockDirective(SortDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ courseId: 123 }) },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: Router, useClass: MockRouter },
                MockProvider(ComplaintService),
                MockProvider(ArtemisDatePipe),
                MockProvider(SortService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ListOfComplaintsComponent);
                comp = fixture.componentInstance;

                const complaintService = TestBed.inject(ComplaintService);
                jest.spyOn(complaintService, 'findAllByTutorIdForCourseId').mockReturnValue(of({ body: complaints } as any));
                jest.spyOn(complaintService, 'findAllByTutorIdForExerciseId').mockReturnValue(of({ body: complaints } as any));
                jest.spyOn(complaintService, 'findAllByCourseId').mockReturnValue(of({ body: complaints } as any));
                jest.spyOn(complaintService, 'findAllByExerciseId').mockReturnValue(of({ body: complaints } as any));

                comp.ngOnInit();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should hide addressed complaints by default', () => {
        expect(comp.complaintsToShow.length).toEqual(1);
    });

    it('should show addressed complaints when the checkbox is selected', () => {
        comp.triggerAddressedComplaints();
        expect(comp.complaintsToShow.length).toEqual(3);
    });
});
