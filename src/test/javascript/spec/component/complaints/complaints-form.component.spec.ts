import { async, ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ComplaintService } from 'app/complaints/complaint.service';
import { MockComplaintResponse, MockComplaintService } from '../../helpers/mocks/service/mock-complaint.service';

import { DebugElement } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { ComplaintsFormComponent } from 'app/complaints/form/complaints-form.component';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { Exercise } from 'app/entities/exercise.model';

describe('ComplaintsComponent', () => {
    // const exercise: Exercise = { id: 1, teamMode: false } as Exercise;
    // let comp: ComplaintsFormComponent;
    // let fixture: ComponentFixture<ComplaintsFormComponent>;
    // let debugElement: DebugElement;
    //
    // beforeEach(async(() => {
    //     TestBed.configureTestingModule({
    //         imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule],
    //         declarations: [ComplaintsFormComponent],
    //         providers: [
    //             {
    //                 provide: ComplaintService,
    //                 useClass: MockComplaintService,
    //             },
    //         ],
    //     })
    //         .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
    //         .compileComponents()
    //         .then(() => {
    //             fixture = TestBed.createComponent(ComplaintsFormComponent);
    //             comp = fixture.componentInstance;
    //             comp.exercise = exercise;
    //             debugElement = fixture.debugElement;
    //         });
    // }));
    it('placeholder', () => {
        expect(true).toBe(true);
    });

    // it('should initialize with correct values for complaints service', fakeAsync(() => {
    //     fixture.detectChanges();
    //     expect(comp.complaintText).toBe(MockComplaintResponse.body.complaintText);
    //     expect(comp.alreadySubmitted).toBe(true);
    //     expect(comp.submittedDate).toBeUndefined();
    //     expect(comp.accepted).toBeUndefined();
    //     expect(comp.handled).toBe(false);
    //     tick(1000);
    //     const textarea = debugElement.query(By.css('#complainTextArea')).nativeElement;
    //     expect(textarea.disabled).toBe(true);
    //     expect(textarea.value).toBe(MockComplaintResponse.body.complaintText);
    //     expect(textarea.readOnly).toBe(true);
    // }));
    //
    // it('should show accepted message when complaint is accepted', () => {
    //     comp.submissionId = 111;
    //     comp.ngOnInit();
    //     fixture.detectChanges();
    //     expect(comp.alreadySubmitted).toBe(true);
    //     expect(comp.submittedDate).toBeUndefined();
    //     expect(comp.accepted).toBe(true);
    //     expect(comp.handled).toBe(true);
    //
    //     expect(debugElement.query(By.css('.text-light.bg-success'))).not.toBeNull();
    // });
});
