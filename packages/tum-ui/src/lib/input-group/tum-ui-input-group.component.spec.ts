import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { By } from '@angular/platform-browser';
import { TumUiInputGroupComponent } from './tum-ui-input-group.component';
import { TumUiInputGroupAddonComponent } from './tum-ui-input-group-addon.component';
import { TumUiInputDirective } from '../input/tum-ui-input.directive';

@Component({
    template: `
        <tum-ui-input-group class="w-auto">
            <tum-ui-input-group-addon>From</tum-ui-input-group-addon>
            <input tumUiInput type="text" />
        </tum-ui-input-group>
    `,
    imports: [TumUiInputGroupComponent, TumUiInputGroupAddonComponent, TumUiInputDirective],
})
class InputGroupHostComponent {}

describe('TumUiInputGroupComponent', () => {
    let fixture: ComponentFixture<InputGroupHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [InputGroupHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(InputGroupHostComponent);
        fixture.detectChanges();
    });

    function group(): HTMLElement {
        return fixture.debugElement.query(By.css('tum-ui-input-group')).nativeElement;
    }

    it('keeps consumer width classes (width is consumer-controlled, unlike p-inputgroup)', () => {
        expect(group().className).toContain('tum-ui-input-group');
        expect(group().className).toContain('w-auto');
    });

    it('projects the addon and the field in order', () => {
        const children = Array.from(group().children).map((c) => c.tagName.toLowerCase());
        expect(children).toEqual(['tum-ui-input-group-addon', 'input']);
    });
});
