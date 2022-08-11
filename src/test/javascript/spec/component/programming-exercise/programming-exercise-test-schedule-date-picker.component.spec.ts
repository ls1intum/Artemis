import dayjs from 'dayjs/esm';
import { NgModel } from '@angular/forms';
import { ProgrammingExerciseTestScheduleDatePickerComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-test-schedule-date-picker.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockDirective, MockPipe, MockModule } from 'ng-mocks';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('ProgrammingExerciseTestScheduleDatePickerComponent', () => {
    let comp: ProgrammingExerciseTestScheduleDatePickerComponent;
    let fixture: ComponentFixture<ProgrammingExerciseTestScheduleDatePickerComponent>;

    const selectedDate = dayjs().add(5, 'days').toDate();
    const startAt = dayjs().add(6, 'days');
    const min = dayjs();
    const max = dayjs().add(7, 'days');
    const label = '';
    const tooltipText = '';
    const readOnly = false;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(OwlDateTimeModule)],
            declarations: [
                ProgrammingExerciseTestScheduleDatePickerComponent,
                MockDirective(NgModel),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTranslatePipe),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseTestScheduleDatePickerComponent);
                comp = fixture.componentInstance;

                comp.selectedDate = selectedDate;
                comp.startAt = startAt;
                comp.min = min;
                comp.max = max;
                comp.label = label;
                comp.tooltipText = tooltipText;
                comp.readOnly = readOnly;
                fixture.detectChanges();
            });
    });

    it('should not change date when set date is undefined', () => {
        comp.selectedDate = selectedDate;
        const spy = jest.spyOn(comp, '_onChange');
        comp.writeValue(undefined);

        expect(comp.selectedDate).toEqual(selectedDate);
        expect(spy).toHaveBeenCalledTimes(0);
    });

    it('should not change date when value is reference-equal to selected date', () => {
        const spy = jest.spyOn(comp, '_onChange');
        comp.writeValue(selectedDate);

        expect(spy).toHaveBeenCalledTimes(0);
    });

    it('should update date with dayjs object and invoke change', () => {
        const updatedDayJsTime = dayjs(4, 'days');
        const updatedDateNumber = updatedDayJsTime.toDate().getDate();
        const spy = jest.spyOn(comp, '_onChange');
        comp.writeValue(updatedDayJsTime);

        expect(comp.selectedDate?.getDate()).toBe(updatedDateNumber);
        expect(spy).toHaveBeenCalledOnce();
    });

    it('should update date with date object and invoke change', () => {
        const updatedDayJsTime = dayjs(4, 'days');
        const updatedDateNumber = updatedDayJsTime.toDate().getDate();
        const spy = jest.spyOn(comp, '_onChange');
        comp.writeValue(updatedDayJsTime);

        expect(comp.selectedDate?.getDate()).toBe(updatedDateNumber);
        expect(spy).toHaveBeenCalledOnce();
    });

    it('should reset date and emit reset event', () => {
        const spy = jest.spyOn(comp.onDateReset, 'emit');
        comp.resetDate();

        expect(comp.selectedDate).toBeNull();
        expect(spy).toHaveBeenCalledTimes(1);
    });
});
