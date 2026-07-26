import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { Component } from '@angular/core';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { TumUiMessageComponent } from 'app/shared-ui/tum-ui/message/tum-ui-message.component';

describe('TumUiMessageComponent', () => {
    let fixture: ComponentFixture<TumUiMessageComponent>;
    let host: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiMessageComponent, FontAwesomeTestingModule],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiMessageComponent);
        host = fixture.nativeElement as HTMLElement;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function text(): HTMLElement {
        return fixture.debugElement.query(By.css('.tum-ui-message-text')).nativeElement;
    }

    it('defaults to the info severity', () => {
        expect(host.getAttribute('data-severity')).toBe('info');
        expect(host.className).toContain('tum-ui-message');
    });

    it('reflects the severity via data-severity (drives the p-message-matched colors in the stylesheet)', () => {
        fixture.componentRef.setInput('severity', 'error');
        fixture.detectChanges();
        expect(host.getAttribute('data-severity')).toBe('error');
    });

    it('rides the surface ramp for secondary/contrast', () => {
        fixture.componentRef.setInput('severity', 'secondary');
        fixture.detectChanges();
        expect(host.className).toContain('bg-surface-100');
        fixture.componentRef.setInput('severity', 'contrast');
        fixture.detectChanges();
        expect(host.className).toContain('bg-surface-900');
    });

    it('renders the text input', () => {
        fixture.componentRef.setInput('text', 'Something went wrong');
        fixture.detectChanges();
        expect(text().textContent?.trim()).toBe('Something went wrong');
    });

    it('forwards styleClass onto the message box', () => {
        fixture.componentRef.setInput('styleClass', 'mb-3 w-full');
        fixture.detectChanges();
        expect(host.className).toContain('mb-3');
        expect(host.className).toContain('w-full');
    });

    it('renders a leading icon only when one is provided', () => {
        expect(fixture.debugElement.query(By.css('.tum-ui-message-icon'))).toBeNull();
        fixture.componentRef.setInput('icon', faCircleInfo);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('.tum-ui-message-icon'))).not.toBeNull();
    });
});

@Component({
    template: `<tum-ui-message><span class="projected">Docs link</span></tum-ui-message>`,
    imports: [TumUiMessageComponent],
})
class MessageHostComponent {}

describe('TumUiMessageComponent (content projection)', () => {
    it('projects content when no text input is set', async () => {
        await TestBed.configureTestingModule({
            imports: [MessageHostComponent, FontAwesomeTestingModule],
        }).compileComponents();
        const fixture = TestBed.createComponent(MessageHostComponent);
        fixture.detectChanges();
        const projected = fixture.debugElement.query(By.css('.projected'));
        expect(projected).not.toBeNull();
        expect(projected.nativeElement.textContent.trim()).toBe('Docs link');
    });
});
