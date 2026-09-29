import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faSearch } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiIconFieldComponent, TumAetUiIconFieldPosition } from './tumaet-ui-icon-field.component';
import { TumAetUiInputDirective } from '../input/tumaet-ui-input.directive';

@Component({
    template: `
        <tumaet-ui-icon-field [icon]="icon()" [iconPosition]="position()">
            <input tumAetUiInput type="text" class="w-full" />
        </tumaet-ui-icon-field>
    `,
    imports: [TumAetUiIconFieldComponent, TumAetUiInputDirective, FontAwesomeTestingModule],
})
class IconFieldHostComponent {
    icon = signal<IconProp | undefined>(faSearch);
    position = signal<TumAetUiIconFieldPosition>('left');
}

describe('TumAetUiIconFieldComponent', () => {
    let fixture: ComponentFixture<IconFieldHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [IconFieldHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(IconFieldHostComponent);
        fixture.detectChanges();
        await fixture.whenStable();
    });

    function field(): HTMLInputElement {
        return fixture.debugElement.query(By.css('input')).nativeElement;
    }

    it('renders the projected input and the icon', () => {
        expect(field()).not.toBeNull();
        expect(fixture.debugElement.query(By.css('.tumaet-ui-input-icon'))).not.toBeNull();
    });

    it('removes the icon when the input is cleared', async () => {
        fixture.componentInstance.icon.set(undefined);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(fixture.debugElement.query(By.css('.tumaet-ui-input-icon'))).toBeNull();
    });
});
