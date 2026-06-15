import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it } from 'vitest';
import { ModePickerComponent, ModePickerOption } from 'app/exercise/mode-picker/mode-picker.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('Mode Picker Component', () => {
    setupTestBed({ zoneless: true });

    let comp: ModePickerComponent<string>;
    let fixture: ComponentFixture<ModePickerComponent<string>>;

    const modePickerOptions: ModePickerOption<string>[] = [
        {
            value: 'Option 0',
            labelKey: 'labelKey 0',
            btnClass: 'btn-secondary',
        },
        {
            value: 'Option 1',
            labelKey: 'labelKey 1',
            btnClass: 'btn-info',
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
        let valueChangeCalledWith: string | undefined;
        comp.valueChange.subscribe((value) => (valueChangeCalledWith = value));

        comp.setMode('new mode');

        expect(valueChangeCalledWith).toBeUndefined();
    });

    it('should set mode classes according to the chosen value', () => {
        fixture.componentRef.setInput('options', modePickerOptions);
        fixture.detectChanges();
        let modes = fixture.debugElement.queryAll(By.css('.btn'));
        let actualClassesForNodes = modes.map((node) => node.nativeNode.getAttribute('class').split(' '));

        actualClassesForNodes.forEach((actualClassesForNode) => expect(actualClassesForNode).toEqual(['btn', 'btn-default']));

        const chosenOption = modePickerOptions[0];
        fixture.componentRef.setInput('value', chosenOption.value);
        fixture.detectChanges();
        modes = fixture.debugElement.queryAll(By.css('.btn'));
        actualClassesForNodes = modes.map((node) => node.nativeNode.getAttribute('class').split(' '));

        expect(actualClassesForNodes[0]).toEqual(['btn', chosenOption.btnClass]);
    });
});
