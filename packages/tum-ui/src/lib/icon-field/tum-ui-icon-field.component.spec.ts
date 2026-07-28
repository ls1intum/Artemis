import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faSearch } from '@fortawesome/free-solid-svg-icons';
import { TumUiIconFieldComponent, TumUiIconFieldPosition } from './tum-ui-icon-field.component';
import { TumUiInputDirective } from '../input/tum-ui-input.directive';

@Component({
    template: `
        <tum-ui-icon-field [icon]="icon()" [iconPosition]="position()">
            <input tumUiInput type="text" class="w-full" />
        </tum-ui-icon-field>
    `,
    imports: [TumUiIconFieldComponent, TumUiInputDirective, FontAwesomeTestingModule],
})
class IconFieldHostComponent {
    icon = signal<IconProp | undefined>(faSearch);
    position = signal<TumUiIconFieldPosition>('left');
}

describe('TumUiIconFieldComponent', () => {
    let fixture: ComponentFixture<IconFieldHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [IconFieldHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(IconFieldHostComponent);
        fixture.detectChanges();
        await fixture.whenStable();
    });

    function wrapper(): HTMLElement {
        return fixture.debugElement.query(By.css('tum-ui-icon-field')).nativeElement;
    }

    function field(): HTMLInputElement {
        return fixture.debugElement.query(By.css('input')).nativeElement;
    }

    it('renders the projected input and the icon', () => {
        expect(field()).not.toBeNull();
        expect(fixture.debugElement.query(By.css('.tum-ui-input-icon'))).not.toBeNull();
    });

    it('reflects the icon side via data-position (drives the edge placement in the stylesheet)', () => {
        expect(wrapper().getAttribute('data-position')).toBe('left');
        fixture.componentInstance.position.set('right');
        fixture.detectChanges();
        expect(wrapper().getAttribute('data-position')).toBe('right');
    });

    it('pads the leading side of the field so text clears the icon', async () => {
        await fixture.whenStable();
        expect(field().style.getPropertyValue('padding-inline-start')).toBe('2.5rem');
        expect(field().style.getPropertyValue('padding-inline-end')).toBe('');
    });

    it('pads the trailing side instead when the icon is on the right', async () => {
        fixture.componentInstance.position.set('right');
        fixture.detectChanges();
        await fixture.whenStable();
        expect(field().style.getPropertyValue('padding-inline-end')).toBe('2.5rem');
        expect(field().style.getPropertyValue('padding-inline-start')).toBe('');
    });

    it('applies no padding and renders no icon when icon is unset', async () => {
        fixture.componentInstance.icon.set(undefined);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(fixture.debugElement.query(By.css('.tum-ui-input-icon'))).toBeNull();
        expect(field().style.getPropertyValue('padding-inline-start')).toBe('');
    });
});
