import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { Component } from '@angular/core';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiMessageComponent } from './tumaet-ui-message.component';

describe('TumAetUiMessageComponent', () => {
    let fixture: ComponentFixture<TumAetUiMessageComponent>;
    let host: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumAetUiMessageComponent, FontAwesomeTestingModule],
        }).compileComponents();
        fixture = TestBed.createComponent(TumAetUiMessageComponent);
        host = fixture.nativeElement as HTMLElement;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function text(): HTMLElement {
        return fixture.debugElement.query(By.css('.tumaet-ui-message-text')).nativeElement;
    }

    it('defaults to the info severity', () => {
        expect(host.getAttribute('data-severity')).toBe('info');
    });

    it('announces informational messages without interrupting', () => {
        expect(host.getAttribute('role')).toBe('status');
    });

    it('reflects the severity state', () => {
        fixture.componentRef.setInput('severity', 'error');
        fixture.detectChanges();
        expect(host.getAttribute('data-severity')).toBe('error');
        expect(host.getAttribute('role')).toBe('alert');
    });

    it('renders the text input', () => {
        fixture.componentRef.setInput('text', 'Something went wrong');
        fixture.detectChanges();
        expect(text().textContent?.trim()).toBe('Something went wrong');
    });
    it('renders a leading icon only when one is provided', () => {
        expect(fixture.debugElement.query(By.css('.tumaet-ui-message-icon'))).toBeNull();
        fixture.componentRef.setInput('icon', faCircleInfo);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('.tumaet-ui-message-icon'))).not.toBeNull();
    });

    it('offers no dismiss button unless it is dismissible', () => {
        expect(fixture.debugElement.query(By.css('.tumaet-ui-message-dismiss'))).toBeNull();
    });

    it('emits dismissed from a labelled dismiss button', () => {
        fixture.componentRef.setInput('dismissible', true);
        fixture.detectChanges();
        const dismissed = vi.fn();
        fixture.componentInstance.dismissed.subscribe(dismissed);

        const button: HTMLButtonElement = fixture.debugElement.query(By.css('.tumaet-ui-message-dismiss')).nativeElement;
        expect(button.type).toBe('button');
        expect(button.getAttribute('aria-label')).toBe('Dismiss');
        button.click();

        expect(dismissed).toHaveBeenCalledOnce();
    });

    it('uses a given accessible name for the dismiss button', () => {
        fixture.componentRef.setInput('dismissible', true);
        fixture.componentRef.setInput('dismissButtonAriaLabel', 'Hide confirmation');
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('.tumaet-ui-message-dismiss')).nativeElement.getAttribute('aria-label')).toBe('Hide confirmation');
    });
});

@Component({
    template: `<tumaet-ui-message><span class="projected">Docs link</span></tumaet-ui-message>`,
    imports: [TumAetUiMessageComponent],
})
class MessageHostComponent {}

describe('TumAetUiMessageComponent (content projection)', () => {
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
