import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { Component } from '@angular/core';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { TumUiMessageComponent } from './tum-ui-message.component';

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
    });

    it('is not a live region unless the consumer asks for one', () => {
        expect(host.getAttribute('role')).toBeNull();
        expect(host.getAttribute('data-live')).toBeNull();
    });

    it('announces politely once live, and assertively when the news is bad', () => {
        fixture.componentRef.setInput('live', true);
        fixture.detectChanges();
        expect(host.getAttribute('role')).toBe('status');
        expect(host.getAttribute('data-live')).toBe('true');

        fixture.componentRef.setInput('severity', 'danger');
        fixture.detectChanges();
        expect(host.getAttribute('role')).toBe('alert');
    });

    it('reflects the severity state', () => {
        fixture.componentRef.setInput('severity', 'danger');
        fixture.detectChanges();
        expect(host.getAttribute('data-severity')).toBe('danger');
    });

    it('normalises the deprecated error and warn spellings onto the package vocabulary', () => {
        fixture.componentRef.setInput('severity', 'error');
        fixture.detectChanges();
        expect(host.getAttribute('data-severity')).toBe('danger');

        fixture.componentRef.setInput('severity', 'warn');
        fixture.detectChanges();
        expect(host.getAttribute('data-severity')).toBe('warning');
    });

    it('renders text and projected content together rather than letting one suppress the other', () => {
        fixture.componentRef.setInput('text', 'Saved');
        fixture.detectChanges();
        expect(text().textContent?.trim()).toBe('Saved');
    });

    it('renders the text input', () => {
        fixture.componentRef.setInput('text', 'Something went wrong');
        fixture.detectChanges();
        expect(text().textContent?.trim()).toBe('Something went wrong');
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
