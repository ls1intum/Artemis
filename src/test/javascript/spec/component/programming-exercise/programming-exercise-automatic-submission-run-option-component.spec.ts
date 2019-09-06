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
import { ProgrammingExerciseAutomaticSubmissionRunOptionComponent } from 'app/entities/programming-exercise/form';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseAutomaticSubmissionRunOption', () => {
    let comp: ProgrammingExerciseAutomaticSubmissionRunOptionComponent;
    let fixture: ComponentFixture<ProgrammingExerciseAutomaticSubmissionRunOptionComponent>;
    let debugElement: DebugElement;

    let programmingExerciseEmitSpy: SinonSpy;

    const checkboxId = '#field_automaticSubmissionRunAfterDueDate';

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, NgbModule, FormsModule],
            declarations: [ProgrammingExerciseAutomaticSubmissionRunOptionComponent],
        })
            .overrideModule(BrowserDynamicTestingModule, { set: { entryComponents: [FaIconComponent] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseAutomaticSubmissionRunOptionComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                programmingExerciseEmitSpy = spy(comp.onProgrammingExerciseUpdate, 'emit');
            });
    });

    const getCheckbox = () => {
        return debugElement.query(By.css(checkboxId));
    };

    it('should be disabled when the due date is not set', async () => {
        // @ts-ignore
        const programmingExercise = { id: 1, dueDate: null, automaticSubmissionRunAfterDueDate: null } as ProgrammingExercise;
        comp.programmingExercise = programmingExercise;

        fixture.detectChanges();
        await fixture.whenStable;

        const checkbox = getCheckbox();
        expect(checkbox).to.exist;
        expect(checkbox.nativeElement.checked).to.be.false;
        expect(checkbox.nativeElement.disabled).to.be.true;
    });

    it('should set itself to one hour after the due date when activated', async () => {
        const now = moment();
        const nowPlusOneHour = now.add(1, 'hours');
        // @ts-ignore
        const programmingExercise = { id: 1, dueDate: now, automaticSubmissionRunDate: null } as ProgrammingExercise;
        comp.programmingExercise = programmingExercise;

        fixture.detectChanges();
        await fixture.whenStable;

        const checkbox = getCheckbox();
        expect(checkbox.nativeElement.checked).to.be.false;
        expect(checkbox.nativeElement.disabled).to.be.false;

        checkbox.nativeElement.click();

        fixture.detectChanges();
        await fixture.whenStable;

        expect(programmingExerciseEmitSpy).to.have.been.calledOnceWithExactly({ ...programmingExercise, automaticSubmissionRunDate: nowPlusOneHour });
        expect(checkbox.nativeElement.checked).to.be.true;
    });

    it('should set itself to null when deactivated', async () => {
        const now = moment();
        const nowPlusOneHour = now.add(1, 'hours');
        // @ts-ignore
        const programmingExercise = { id: 1, dueDate: now, automaticSubmissionRunDate: nowPlusOneHour } as ProgrammingExercise;
        comp.programmingExercise = programmingExercise;

        fixture.detectChanges();
        await fixture.whenStable;

        const checkbox = getCheckbox();
        expect(checkbox.nativeElement.checked).to.be.true;
        expect(checkbox.nativeElement.disabled).to.be.false;

        checkbox.nativeElement.click();

        fixture.detectChanges();
        await fixture.whenStable;

        expect(programmingExerciseEmitSpy).to.have.been.calledOnceWithExactly({ ...programmingExercise, automaticSubmissionRunDate: null });
        expect(checkbox.nativeElement.checked).to.be.false;
    });
});
