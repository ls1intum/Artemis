import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';

describe('ColorSelectorComponent', () => {
    let component: ColorSelectorComponent;
    let fixture: ComponentFixture<ColorSelectorComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ColorSelectorComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ColorSelectorComponent);
                component = fixture.componentInstance;
            });
    });

    it('should set the correct coordinates on init', () => {
        component.ngOnInit();

        expect(component.colorSelectorPosition).toEqual({ left: 0, top: 0 });
    });

    it('should register click event listener on init', () => {
        //@ts-ignore spying on private method
        const addEventListenerSpy = jest.spyOn(component, 'addEventListenerToCloseComponentOnClickOutside');

        component.ngOnInit();

        expect(addEventListenerSpy).toHaveBeenCalledOnce();
    });

    it('should stop event propagation on openSelector', () => {
        const event = { stopPropagation: jest.fn(), target: { closest: jest.fn() } };
        const eventStopPropagationSpy = jest.spyOn(event, 'stopPropagation');
        component.colorSelectorPosition = { left: 0, top: 0 };
        component.openColorSelector(event as unknown as MouseEvent, 0, 0);

        expect(eventStopPropagationSpy).toHaveBeenCalledOnce();
    });

    it('should position the color selector after opening correctly', () => {
        const target = document.createElement('div');
        const event = {
            target,
            stopPropagation: jest.fn(),
        } as unknown as MouseEvent;
        component.ngOnInit();

        component.openColorSelector(event, 10, 7);

        expect(component.colorSelectorPosition).toEqual({ left: 0, top: 10 });
        expect(component.height).toBe(7);
        expect(component.showColorSelector).toBeTrue();

        component.showColorSelector = false;

        component.openColorSelector(event);

        expect(component.colorSelectorPosition).toEqual({ left: 0, top: 65 });
        expect(component.height).toBe(7);
        expect(component.showColorSelector).toBeTrue();
    });

    it('should set the tag colors correctly', () => {
        const emitMock = jest.spyOn(component.selectedColor, 'emit').mockImplementation();
        // copy of the colors declared in the component since the array is not exported
        const DEFAULT_COLORS = [
            ARTEMIS_DEFAULT_COLOR,
            '#1b97ca',
            '#0d3cc2',
            '#009999',
            '#0ab84f',
            '#94a11c',
            '#9dca53',
            '#ffd014',
            '#c6aa1c',
            '#ffa500',
            '#ffb2b2',
            '#ca94bd',
            '#a95292',
            '#691b0b',
            '#ad5658',
            '#ff1a35',
        ];

        DEFAULT_COLORS.forEach((color) => {
            component.showColorSelector = true;
            component.selectColorForTag(color);
            expect(component.showColorSelector).toBeFalse();
            expect(emitMock).toHaveBeenCalledWith(color);
        });
    });

    it('should cancel the color selector correctly', () => {
        component.showColorSelector = true;
        component.cancelColorSelector();

        expect(component.showColorSelector).toBeFalse();
    });

    describe('should handle close actions properly', () => {
        beforeEach(() => {
            // Make sure that the event Listener is registered
            component.ngOnInit();
            component.showColorSelector = true;
        });

        it('and close when clicked outside', () => {
            // Simulate a click event outside the component by dispatching a custom event
            const clickEvent = new Event('click');
            document.dispatchEvent(clickEvent);

            expect(component.showColorSelector).toBeFalse();
        });

        it('and stay open when clicked inside but not clicking a color', () => {
            expect(component.showColorSelector).toBeTrue();

            const insideDummyElement = document.createElement('div');
            fixture.nativeElement.appendChild(insideDummyElement);

            const clickEvent = new MouseEvent('click', { bubbles: true });
            insideDummyElement.dispatchEvent(clickEvent);

            expect(component.showColorSelector).toBeTrue();
        });
    });
});
