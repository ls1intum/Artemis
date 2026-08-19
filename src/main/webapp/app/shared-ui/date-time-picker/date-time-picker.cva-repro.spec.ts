import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { DateTimePickerType, FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

// Mimics the tutorial free-period form: OnPush parent, reactive form, a TIMER picker that is
// conditionally rendered (added/removed when switching "time frame" tabs), bound via formControlName.
// The picker must DISPLAY the control's value in all of these cases (PR #13009 review — WoH).
@Component({
    template: `
        <form [formGroup]="form">
            @if (show()) {
                <jhi-date-time-picker formControlName="startTime" [pickerType]="TIMER" [shouldDisplayTimeZoneWarning]="false" />
            }
        </form>
    `,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ReactiveFormsModule, FormDateTimePickerComponent],
})
class HostComponent {
    form = new FormGroup({ startTime: new FormControl<Date | null>(null) });
    show = signal(false);
    readonly TIMER = DateTimePickerType.TIMER;
}

describe('date-time-picker as formControlName CVA in an OnPush parent (free-period repro)', () => {
    let fixture: ComponentFixture<HostComponent>;
    let host: HostComponent;

    const time = new Date('2026-06-03T13:34:00');

    function inputValue(): string {
        return (fixture.nativeElement.querySelector('input.p-datepicker-input') as HTMLInputElement | null)?.value ?? '<no-input>';
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HostComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        host = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('displays the control value when the picker is created with an already-set control (edit / tab into populated entry)', () => {
        host.form.controls.startTime.setValue(time);
        host.show.set(true);
        fixture.detectChanges();

        expect(inputValue()).toContain('13:34');
    });

    it('displays the control value when patched after the picker is already rendered', () => {
        host.show.set(true);
        fixture.detectChanges();
        expect(inputValue()).toBe('');

        host.form.controls.startTime.setValue(time);
        fixture.detectChanges();

        expect(inputValue()).toContain('13:34');
    });

    it('shows the retained value again after the picker is removed and re-added (tab away and back)', () => {
        host.form.controls.startTime.setValue(time);
        host.show.set(true);
        fixture.detectChanges();
        expect(inputValue()).toContain('13:34');

        host.show.set(false);
        fixture.detectChanges();

        host.show.set(true);
        fixture.detectChanges();
        expect(inputValue()).toContain('13:34');
    });

    it('clears the display when the control is reset (tab switch resets the control)', () => {
        host.form.controls.startTime.setValue(time);
        host.show.set(true);
        fixture.detectChanges();
        expect(inputValue()).toContain('13:34');

        host.form.controls.startTime.reset();
        fixture.detectChanges();

        expect(inputValue()).toBe('');
    });
});
