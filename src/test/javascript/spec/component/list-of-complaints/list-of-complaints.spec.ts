import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared';
import { RouterTestingModule } from '@angular/router/testing';
import { SortByModule } from 'app/components/pipes';
import { JhiAlertService } from 'ng-jhipster';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { ListOfComplaintsComponent } from 'app/list-of-complaints';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { DifferencePipe } from 'ngx-moment';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from '../../helpers/mock-route.service';

describe('ListOfComplaintsComponent', () => {
    let comp: ListOfComplaintsComponent;
    let fixture: ComponentFixture<ListOfComplaintsComponent>;

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
            imports: [ArtemisSharedModule, TranslateModule.forRoot(), ArtemisTestModule, RouterTestingModule.withRoutes([]), SortByModule],
            declarations: [ListOfComplaintsComponent],
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
                fixture = TestBed.createComponent(ListOfComplaintsComponent);
                comp = fixture.componentInstance;

                comp.ngOnInit();
            });
    });

    it('should hide addressed complaints by default', () => {
        expect(comp.complaintsToShow.length).toEqual(1);
    });

    it('should show addressed complaints when the checkbox is selected', () => {
        comp.triggerAddressedComplaints();
        expect(comp.complaintsToShow.length).toEqual(3);
    });
});
