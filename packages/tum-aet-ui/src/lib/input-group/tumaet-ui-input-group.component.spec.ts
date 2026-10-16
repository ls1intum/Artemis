import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { By } from '@angular/platform-browser';
import { TumAetUiInputGroupComponent } from './tumaet-ui-input-group.component';
import { TumAetUiInputGroupAddonComponent } from './tumaet-ui-input-group-addon.component';
import { TumAetUiInputDirective } from '../input/tumaet-ui-input.directive';

@Component({
    template: `
        <tumaet-ui-input-group>
            <tumaet-ui-input-group-addon>From</tumaet-ui-input-group-addon>
            <input tumAetUiInput type="text" />
        </tumaet-ui-input-group>
    `,
    imports: [TumAetUiInputGroupComponent, TumAetUiInputGroupAddonComponent, TumAetUiInputDirective],
})
class InputGroupHostComponent {}

describe('TumAetUiInputGroupComponent', () => {
    let fixture: ComponentFixture<InputGroupHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [InputGroupHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(InputGroupHostComponent);
        fixture.detectChanges();
    });

    function group(): HTMLElement {
        return fixture.debugElement.query(By.css('tumaet-ui-input-group')).nativeElement;
    }

    it('projects the addon and the field in order', () => {
        const children = Array.from(group().children).map((c) => c.tagName.toLowerCase());
        expect(children).toEqual(['tumaet-ui-input-group-addon', 'input']);
    });
});
