import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ArTEMiSTestModule } from '../../test.module';
import { ArTEMiSSharedModule } from 'app/shared';
import { RouterTestingModule } from '@angular/router/testing';
import { SortByModule } from 'app/components/pipes';
import { JhiAlertService } from 'ng-jhipster';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { DifferencePipe } from 'ngx-moment';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from '../../helpers/mock-route.service';
import { ListOfMoreFeedbackRequestsComponent } from 'app/list-of-more-feedback-requests';

describe('ListOfMoreFeedbackRequestsComponent', () => {
    let comp: ListOfMoreFeedbackRequestsComponent;
    let fixture: ComponentFixture<ListOfMoreFeedbackRequestsComponent>;

    const complaints = [{ accepted: undefined }, { accepted: true }, { accepted: false }];

    const subscribe = {
        subscribe: (fn: (value: any) => void) => {
            fn({
                body: complaints,
            });
        },
    };

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArTEMiSSharedModule, TranslateModule.forRoot(), ArTEMiSTestModule, RouterTestingModule.withRoutes([]), SortByModule],
            declarations: [ListOfMoreFeedbackRequestsComponent],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ courseId: 123 }),
                },
                DifferencePipe,
                {
                    provide: JhiAlertService,
                    useValue: MockAlertService,
                },
                {
                    provide: ComplaintService,
                    useValue: {
                        findAllByTutorIdForCourseId() {
                            return subscribe;
                        },
                        findAllByTutorIdForExerciseId() {
                            return subscribe;
                        },
                        findAllByCourseId() {
                            return subscribe;
                        },
                        findAllByExerciseId() {
                            return subscribe;
                        },
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ListOfMoreFeedbackRequestsComponent);
                comp = fixture.componentInstance;

                comp.ngOnInit();
            });
    });

    it('should hide addressed requests by default', () => {
        expect(comp.complaintsToShow.length).toEqual(1);
    });

    it('should show addressed requests when the checkbox is selected', () => {
        comp.triggerAddressedComplaints();
        expect(comp.complaintsToShow.length).toEqual(3);
    });
});
