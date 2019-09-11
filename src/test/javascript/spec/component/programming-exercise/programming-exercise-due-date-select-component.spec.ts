import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import * as moment from 'moment';
import { By } from '@angular/platform-browser';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { BrowserDynamicTestingModule } from '@angular/platform-browser-dynamic/testing';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { SinonSpy, spy } from 'sinon';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExercise } from 'src/main/webapp/app/entities/programming-exercise';
import { ProgrammingExerciseDueDateSelectComponent } from 'app/entities/programming-exercise/form';
import { FormDateTimePickerModule } from '../../../../../main/webapp/app/shared/date-time-picker/date-time-picker.module';
import { ArtemisSharedModule } from 'app/shared';
import { triggerChanges } from '../../utils/general.utils';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseDueDateSelectComponent', () => {
    let comp: ProgrammingExerciseDueDateSelectComponent;
    let fixture: ComponentFixture<ProgrammingExerciseDueDateSelectComponent>;
    let debugElement: DebugElement;

    let programmingExerciseEmitSpy: SinonSpy;
    let validDueDateSpy: SinonSpy;

    const containerId = '#build-and-test-date-container';
    const checkboxId = '#field_buildAndTestStudentSubmissionsAfterDueDate';

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule, NgbModule, FormsModule, FormDateTimePickerModule],
            declarations: [ProgrammingExerciseDueDateSelectComponent],
        })
            .overrideModule(BrowserDynamicTestingModule, { set: { entryComponents: [FaIconComponent] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseDueDateSelectComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                programmingExerciseEmitSpy = spy(comp.onProgrammingExerciseUpdate, 'emit');
                validDueDateSpy = spy(comp.onDueDateValidationChange, 'emit');
            });
    });

    afterEach(() => {
        programmingExerciseEmitSpy.restore();
    });

    const getAutomaticSubmissionContainer = () => {
        return debugElement.query(By.css(containerId));
    };

    const getCheckbox = () => {
        return debugElement.query(By.css(checkboxId));
    };

    it('automatic buildAndTest date container should be removed when the due date is null', async () => {
        // @ts-ignore
        const programmingExercise = { id: 1, dueDate: null, buildAndTestStudentSubmissionsAfterDueDate: null } as ProgrammingExercise;
        comp.exercise = programmingExercise;

        fixture.detectChanges();
        await fixture.whenStable;

        const container = getAutomaticSubmissionContainer();
        expect(container).not.to.exist;
    });

    it('automatic buildAndTest date checkbox should be checked when the container is provided with an exercise that has a buildAndTestDate', async () => {
        // @ts-ignore
        const programmingExercise = { id: 1, dueDate: moment(), buildAndTestStudentSubmissionsAfterDueDate: moment() } as ProgrammingExercise;
        comp.exercise = programmingExercise;

        triggerChanges(comp, { property: 'exercise', newObj: programmingExercise });

        fixture.detectChanges();
        await fixture.whenStable;

        const container = getAutomaticSubmissionContainer();
        expect(container).to.exist;

        const checkbox = getCheckbox();
        expect(checkbox).to.exist;
        expect(checkbox.nativeElement.checked).to.be.true;
    });

    it('should set the buildAndTest date to the due date when the checkbox is activated and the due date is set', async () => {
        const now = moment();
        // @ts-ignore
        const programmingExercise = { id: 1, dueDate: now, buildAndTestStudentSubmissionsAfterDueDate: null } as ProgrammingExercise;
        comp.exercise = programmingExercise;

        triggerChanges(comp, { property: 'exercise', newObj: programmingExercise });

        fixture.detectChanges();
        await fixture.whenStable;

        const checkboxContainer = getAutomaticSubmissionContainer();
        expect(checkboxContainer).to.exist;
        const buildAndTestCheckbox = getCheckbox();
        expect(buildAndTestCheckbox.nativeElement.checked).to.be.false;
        expect(buildAndTestCheckbox.nativeElement.disabled).to.be.false;

        buildAndTestCheckbox.nativeElement.click();

        fixture.detectChanges();
        await fixture.whenStable;

        expect(programmingExerciseEmitSpy).to.have.been.calledOnceWithExactly({ ...programmingExercise, buildAndTestStudentSubmissionsAfterDueDate: now });
        expect(buildAndTestCheckbox.nativeElement.checked).to.be.true;
        expect(validDueDateSpy).to.have.been.calledOnceWithExactly(true);
    });

    it('should set the buildAndTest date to null when the checkbox is deactivated', async () => {
        const now = moment();
        const nowPlusOneHour = now.clone().add(1, 'hours');
        // @ts-ignore
        const programmingExercise = { id: 1, dueDate: now, buildAndTestStudentSubmissionsAfterDueDate: nowPlusOneHour } as ProgrammingExercise;
        comp.exercise = programmingExercise;

        triggerChanges(comp, { property: 'exercise', newObj: programmingExercise });

        fixture.detectChanges();
        await fixture.whenStable;

        const checkbox = getCheckbox();
        expect(checkbox.nativeElement.checked).to.be.true;
        expect(checkbox.nativeElement.disabled).to.be.false;

        checkbox.nativeElement.click();

        fixture.detectChanges();
        await fixture.whenStable;

        expect(programmingExerciseEmitSpy).to.have.been.calledOnceWithExactly({ ...programmingExercise, buildAndTestStudentSubmissionsAfterDueDate: null });
        expect(checkbox.nativeElement.checked).to.be.false;
    });

    it('should not update the buildAndTest date when active and the due date is changed to a date in the future of the buildAndTest date, but emit an error', async () => {
        const now = moment();
        const nowPlusOneHour = now.clone().add(1, 'hours');
        // @ts-ignore
        const programmingExercise = { id: 1, dueDate: now, buildAndTestStudentSubmissionsAfterDueDate: nowPlusOneHour } as ProgrammingExercise;
        comp.exercise = programmingExercise;

        triggerChanges(comp, { property: 'exercise', newObj: programmingExercise });

        fixture.detectChanges();
        await fixture.whenStable;

        const checkbox = getCheckbox();
        expect(checkbox.nativeElement.checked).to.be.true;

        // Now set the due date 5 hours into the future.
        const newDueDate = now.clone().add(5, 'hours');
        comp.dateTimePicker.updateField(newDueDate);

        fixture.detectChanges();
        await fixture.whenStable;

        expect(programmingExerciseEmitSpy).to.have.been.calledOnceWithExactly({
            ...programmingExercise,
            dueDate: newDueDate,
        });
        expect(checkbox.nativeElement.checked).to.be.true;
        expect(validDueDateSpy).to.have.been.calledOnceWithExactly(false);
    });

    it('should not set the buildAndTest date to null when the due date is set to null, but emit an error', async () => {
        const now = moment();
        const nowPlusOneHour = now.clone().add(1, 'hours');
        // @ts-ignore
        const programmingExercise = { id: 1, dueDate: now, buildAndTestStudentSubmissionsAfterDueDate: nowPlusOneHour } as ProgrammingExercise;
        comp.exercise = programmingExercise;

        triggerChanges(comp, { property: 'exercise', newObj: programmingExercise });

        fixture.detectChanges();
        await fixture.whenStable;

        const checkbox = getCheckbox();
        expect(checkbox.nativeElement.checked).to.be.true;

        // Now set the due date to null.
        comp.dateTimePicker.updateField(moment(''));

        fixture.detectChanges();
        await fixture.whenStable;

        expect(programmingExerciseEmitSpy).to.have.been.calledOnceWithExactly({ ...programmingExercise, dueDate: null });
        expect(checkbox.nativeElement.checked).to.be.true;
        expect(validDueDateSpy).to.have.been.calledOnceWithExactly(false);
    });
});
