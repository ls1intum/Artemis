import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ModePickerComponent, ModePickerOption } from 'app/exercises/shared/mode-picker/mode-picker.component';
import { By } from '@angular/platform-browser';

describe('Mode Picker Component', () => {
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

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ModePickerComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModePickerComponent<string>);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();

        expect(comp.disabled).toBeFalse();
        expect(comp.valueChange).toBeDefined();
    });

    it('should set mode when new mode is different', () => {
        const newMode = 'new mode';
        comp.disabled = false;

        comp.value = 'old mode';

        let valueChangeCalledWith = undefined;
        comp.valueChange.subscribe((value) => (valueChangeCalledWith = value));

        comp.setMode(newMode);

        expect(valueChangeCalledWith).toBe(newMode);
    });

    it('should not set mode when new mode is the same', () => {
        const newMode = 'old mode';

        comp.disabled = false;
        comp.value = newMode;

        let valueChangeCalledWith = undefined;
        comp.valueChange.subscribe((value) => (valueChangeCalledWith = value));

        comp.setMode(newMode);

        expect(valueChangeCalledWith).toBeUndefined();
    });

    it('should not set mode when disabled', () => {
        const newMode = 'new mode';

        comp.disabled = true;
        comp.value = 'old mode';

        let valueChangeCalledWith = undefined;
        comp.valueChange.subscribe((value) => (valueChangeCalledWith = value));

        comp.setMode(newMode);

        expect(valueChangeCalledWith).toBeUndefined();
    });

    it('should set mode classes according to the chosen value', () => {
        comp.options = modePickerOptions;

        fixture.detectChanges();
        const modes = fixture.debugElement.queryAll(By.css('.btn'));

        const actualClassesForNodes = modes.map((node) => node.nativeNode.getAttribute('class').split(' '));

        // If no value is set, all options should have 'btn-default' class.
        actualClassesForNodes.forEach((actualClassesForNode) => expect(actualClassesForNode).toEqual(['btn', 'btn-default']));

        const chosenOption = modePickerOptions[0];
        comp.value = chosenOption.value;

        fixture.detectChanges();

        // If a value is chosen, chosen options should have its own btnClass class.
        expect(modes[0].nativeNode.getAttribute('class').split(' ')).toEqual(['btn', chosenOption.btnClass]);
    });
});
