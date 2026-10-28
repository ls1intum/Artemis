import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { ModePickerComponent, ModePickerOption } from 'app/exercise/mode-picker/mode-picker.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('Mode Picker Component', () => {
    let comp: ModePickerComponent<string>;
    let fixture: ComponentFixture<ModePickerComponent<string>>;

    const modePickerOptions: ModePickerOption<string>[] = [
        {
            value: 'Option 0',
            labelKey: 'labelKey 0',
            severity: 'secondary',
        },
        {
            value: 'Option 1',
            labelKey: 'labelKey 1',
            severity: 'info',
        },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ModePickerComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ModePickerComponent<string>);
        comp = fixture.componentInstance;
    });

    it('should initialize', () => {
        fixture.detectChanges();

        expect(comp.disabled()).toBe(false);
        expect(comp.valueChange).toBeDefined();
    });

    it('should set mode when new mode is different', () => {
        fixture.componentRef.setInput('disabled', false);
        fixture.componentRef.setInput('value', 'old mode');
        let valueChangeCalledWith: string | undefined;
        comp.valueChange.subscribe((value) => (valueChangeCalledWith = value));

        comp.setMode('new mode');

        expect(valueChangeCalledWith).toBe('new mode');
    });

    it('should not set mode when new mode is the same', () => {
        fixture.componentRef.setInput('disabled', false);
        fixture.componentRef.setInput('value', 'old mode');
        let valueChangeCalledWith: string | undefined;
        comp.valueChange.subscribe((value) => (valueChangeCalledWith = value));

        comp.setMode('old mode');

        expect(valueChangeCalledWith).toBeUndefined();
    });

    it('should not set mode when disabled', () => {
        fixture.componentRef.setInput('disabled', true);
        fixture.componentRef.setInput('value', 'old mode');
        fixture.componentRef.setInput('options', modePickerOptions);
        let valueChangeCalledWith: string | undefined;
        comp.valueChange.subscribe((value) => (valueChangeCalledWith = value));

        fixture.detectChanges();
        const option = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
        expect(option.disabled).toBe(true);
        option.click();

        expect(valueChangeCalledWith).toBeUndefined();
    });

    it('exposes selection and emits from the rendered buttons', () => {
        fixture.componentRef.setInput('options', modePickerOptions);
        fixture.componentRef.setInput('value', modePickerOptions[0].value);
        fixture.detectChanges();
        const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
        expect(buttons).toHaveLength(2);
        expect(buttons[0].getAttribute('aria-pressed')).toBe('true');
        expect(buttons[1].getAttribute('aria-pressed')).toBe('false');
        let nextValue: string | undefined;
        comp.valueChange.subscribe((value) => (nextValue = value));
        buttons[1].click();
        expect(nextValue).toBe(modePickerOptions[1].value);
    });
});
