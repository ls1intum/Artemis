import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { Component } from '@angular/core';
import { TumUiCardComponent } from './tum-ui-card.component';

describe('TumUiCardComponent', () => {
    let fixture: ComponentFixture<TumUiCardComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiCardComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiCardComponent);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('renders the card body', () => {
        expect(fixture.debugElement.query(By.css('.tum-ui-card-body'))).not.toBeNull();
    });

    it('has no caption when neither header nor subheader is set', () => {
        expect(fixture.debugElement.query(By.css('.tum-ui-card-caption'))).toBeNull();
    });

    it('renders header and subheader in the caption', () => {
        fixture.componentRef.setInput('header', 'Total tokens');
        fixture.componentRef.setInput('subheader', 'last 30 days');
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('.tum-ui-card-title')).nativeElement.textContent.trim()).toBe('Total tokens');
        const subtitle = fixture.debugElement.query(By.css('.tum-ui-card-subtitle')).nativeElement as HTMLElement;
        expect(subtitle.textContent?.trim()).toBe('last 30 days');
    });
});

@Component({
    template: `
        <tum-ui-card>
            <div tumUiCardHeader class="header-slot">Header</div>
            <span class="body-slot">Body content</span>
            <div tumUiCardFooter class="footer-slot">Footer</div>
        </tum-ui-card>
    `,
    imports: [TumUiCardComponent],
})
class CardHostComponent {}

describe('TumUiCardComponent (projection)', () => {
    let fixture: ComponentFixture<CardHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CardHostComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(CardHostComponent);
        fixture.detectChanges();
    });

    it('projects default content into the card body', () => {
        const body = fixture.debugElement.query(By.css('.tum-ui-card-content .body-slot'));
        expect(body).not.toBeNull();
        expect(body.nativeElement.textContent.trim()).toBe('Body content');
    });

    it('projects header and footer into their dedicated slots', () => {
        expect(fixture.debugElement.query(By.css('.header-slot'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('.footer-slot'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('.tum-ui-card-body .header-slot'))).toBeNull();
    });
});
