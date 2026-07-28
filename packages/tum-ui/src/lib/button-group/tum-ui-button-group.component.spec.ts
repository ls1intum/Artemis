import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { Component } from '@angular/core';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TumUiButtonGroupComponent } from './tum-ui-button-group.component';
import { TumUiButtonComponent } from '../button/tum-ui-button.component';

describe('TumUiButtonGroupComponent', () => {
    let fixture: ComponentFixture<TumUiButtonGroupComponent>;
    let host: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiButtonGroupComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiButtonGroupComponent);
        host = fixture.nativeElement as HTMLElement;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('renders as an inline-flex group', () => {
        expect(host.className).toContain('tum-ui-button-group');
    });

    it('forwards styleClass onto the group', () => {
        fixture.componentRef.setInput('styleClass', 'ms-1');
        fixture.detectChanges();
        expect(host.className).toContain('ms-1');
    });
});

@Component({
    template: `
        <tum-ui-button-group>
            <tum-ui-button size="small">One</tum-ui-button>
            <tum-ui-button size="small">Two</tum-ui-button>
            <tum-ui-button size="small">Three</tum-ui-button>
        </tum-ui-button-group>
    `,
    imports: [TumUiButtonGroupComponent, TumUiButtonComponent],
})
class ButtonGroupHostComponent {}

describe('TumUiButtonGroupComponent (projection)', () => {
    it('projects and joins its tum-ui-button children', async () => {
        await TestBed.configureTestingModule({
            imports: [ButtonGroupHostComponent, FontAwesomeTestingModule],
        }).compileComponents();
        const fixture = TestBed.createComponent(ButtonGroupHostComponent);
        fixture.detectChanges();
        const buttons = fixture.debugElement.queryAll(By.css('tum-ui-button-group tum-ui-button .tum-ui-btn'));
        expect(buttons.length).toBe(3);
        expect(buttons.map((b) => b.nativeElement.textContent.trim())).toEqual(['One', 'Two', 'Three']);
    });
});

@Component({
    template: `
        <tum-ui-button-group>
            <button type="button">A</button>
            <button type="button">B</button>
        </tum-ui-button-group>
    `,
    imports: [TumUiButtonGroupComponent],
})
class NativeButtonGroupHostComponent {}

describe('TumUiButtonGroupComponent (native button projection)', () => {
    it('projects raw native buttons', async () => {
        await TestBed.configureTestingModule({
            imports: [NativeButtonGroupHostComponent],
        }).compileComponents();
        const fixture = TestBed.createComponent(NativeButtonGroupHostComponent);
        fixture.detectChanges();
        const buttons = fixture.debugElement.queryAll(By.css('tum-ui-button-group > button'));
        expect(buttons.length).toBe(2);
    });
});
