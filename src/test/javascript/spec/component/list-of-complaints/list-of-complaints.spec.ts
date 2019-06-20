import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ListOfComplaintsComponent } from 'app/list-of-complaints';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { ArTEMiSTestModule } from '../../test.module';
import { ArTEMiSSharedModule } from 'app/shared';
import { TranslateModule } from '@ngx-translate/core';
import { RouterTestingModule } from '@angular/router/testing';

const complaints = [{ accepted: undefined }, { accepted: true }, { accepted: false }];

const subscribe = {
    subscribe: (fn: (value: any) => void) => {
        fn({
            body: complaints,
        });
    },
};

describe('ListOfComplaintsComponent', () => {
    let comp: ListOfComplaintsComponent;
    let fixture: ComponentFixture<ListOfComplaintsComponent>;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArTEMiSTestModule, RouterTestingModule.withRoutes([]), ArTEMiSSharedModule],
            declarations: [ListOfComplaintsComponent],
            providers: [
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
            });
    });

    it('should hide addressed complaints by default', () => {
        expect(comp.complaintsToShow.length).toEqual(1);
    });

    it('should show addressed complaints when the checkbox is selected', () => {
        expect(comp.complaintsToShow.length).toEqual(3);
    });
});
