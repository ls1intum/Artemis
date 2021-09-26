import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterTestingModule } from '@angular/router/testing';

import { ComplaintService } from 'app/complaints/complaint.service';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ListOfComplaintsComponent } from 'app/complaints/list-of-complaints/list-of-complaints.component';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

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
            imports: [ArtemisSharedModule, TranslateModule.forRoot(), ArtemisTestModule, RouterTestingModule.withRoutes([])],
            declarations: [ListOfComplaintsComponent],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ courseId: 123 }),
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
            .overrideModule(ArtemisTestModule, {
                remove: {
                    declarations: [MockComponent(FaIconComponent)],
                    exports: [MockComponent(FaIconComponent)],
                },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ListOfComplaintsComponent);
                comp = fixture.componentInstance;
                comp.ngOnInit();
            });
    });

    afterEach(() => {});

    it('should hide addressed complaints by default', () => {
        expect(comp.complaintsToShow.length).toEqual(1);
    });

    it('should show addressed complaints when the checkbox is selected', () => {
        comp.triggerAddressedComplaints();
        expect(comp.complaintsToShow.length).toEqual(3);
    });
});
