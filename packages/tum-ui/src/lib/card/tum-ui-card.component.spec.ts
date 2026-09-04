import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { vi } from 'vitest';
import {
    TumUiCardActionComponent,
    TumUiCardContentComponent,
    TumUiCardDescriptionComponent,
    TumUiCardFooterComponent,
    TumUiCardHeaderComponent,
    TumUiCardTitleComponent,
} from './tum-ui-card-parts.component';
import { TumUiCardComponent } from './tum-ui-card.component';

@Component({
    imports: [
        TumUiCardComponent,
        TumUiCardHeaderComponent,
        TumUiCardTitleComponent,
        TumUiCardDescriptionComponent,
        TumUiCardActionComponent,
        TumUiCardContentComponent,
        TumUiCardFooterComponent,
    ],
    template: `
        <tum-ui-card [size]="size()" [variant]="variant()">
            <tum-ui-card-header>
                <tum-ui-card-title [level]="level()">Progress</tum-ui-card-title>
                <tum-ui-card-description>Five stages, in order.</tum-ui-card-description>
                <tum-ui-card-action><button type="button" class="action">Cancel</button></tum-ui-card-action>
            </tum-ui-card-header>
            <tum-ui-card-content><p class="body">Two of five done.</p></tum-ui-card-content>
            <tum-ui-card-footer>Updated a moment ago</tum-ui-card-footer>
        </tum-ui-card>
    `,
})
class CompoundHostComponent {
    readonly level = signal<1 | 2 | 3 | 4 | 5 | 6 | undefined>(2);
    readonly size = signal<'small' | 'medium' | 'large'>('medium');
    readonly variant = signal<'elevated' | 'outline' | 'flat'>('elevated');
}

describe('TumUiCardComponent (compound)', () => {
    let fixture: ComponentFixture<CompoundHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [CompoundHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(CompoundHostComponent);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function element(selector: string): HTMLElement {
        return fixture.debugElement.query(By.css(selector)).nativeElement as HTMLElement;
    }

    it('publishes a slot for every part, and its axes on the root', () => {
        expect(element('tum-ui-card').getAttribute('data-slot')).toBe('card');
        expect(element('tum-ui-card').getAttribute('data-size')).toBe('medium');
        expect(element('tum-ui-card').getAttribute('data-variant')).toBe('elevated');
        expect(element('tum-ui-card-header').getAttribute('data-slot')).toBe('card-header');
        expect(element('tum-ui-card-title').getAttribute('data-slot')).toBe('card-title');
        expect(element('tum-ui-card-description').getAttribute('data-slot')).toBe('card-description');
        expect(element('tum-ui-card-action').getAttribute('data-slot')).toBe('card-action');
        expect(element('tum-ui-card-content').getAttribute('data-slot')).toBe('card-content');
        expect(element('tum-ui-card-footer').getAttribute('data-slot')).toBe('card-footer');
    });

    it('makes the title a real heading at the level the consumer chose', () => {
        const title = element('tum-ui-card-title');
        expect(title.getAttribute('role')).toBe('heading');
        expect(title.getAttribute('aria-level')).toBe('2');

        fixture.componentInstance.level.set(3);
        fixture.detectChanges();
        expect(title.getAttribute('aria-level')).toBe('3');
    });

    it('drops the heading semantics when the card is not a section', () => {
        fixture.componentInstance.level.set(undefined);
        fixture.detectChanges();
        const title = element('tum-ui-card-title');
        expect(title.getAttribute('role')).toBeNull();
        expect(title.getAttribute('aria-level')).toBeNull();
    });

    it('puts the action beside the title rather than forcing the consumer to abandon the card', () => {
        const header = element('tum-ui-card-header');
        const action = element('tum-ui-card-action');
        expect(header.contains(action)).toBe(true);
        expect(action.querySelector('.action')?.textContent?.trim()).toBe('Cancel');
    });

    it('projects header, content and footer into one padded body, on one code path', () => {
        const body = element('.tum-ui-card-body');
        for (const selector of ['tum-ui-card-header', 'tum-ui-card-content', 'tum-ui-card-footer']) {
            expect(body.contains(element(selector))).toBe(true);
        }
    });

    it('renders no deprecated caption when the string inputs are unused', () => {
        expect(fixture.debugElement.query(By.css('.tum-ui-card-caption'))).toBeNull();
    });
});

describe('TumUiCardComponent (deprecated string API)', () => {
    let fixture: ComponentFixture<TumUiCardComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TumUiCardComponent] }).compileComponents();
        fixture = TestBed.createComponent(TumUiCardComponent);
        fixture.detectChanges();
    });

    it('keeps rendering header and subheader, so no existing call site breaks', () => {
        fixture.componentRef.setInput('header', 'Total tokens');
        fixture.componentRef.setInput('subheader', 'last 30 days');
        fixture.detectChanges();
        const host = fixture.nativeElement as HTMLElement;
        expect(host.querySelector('.tum-ui-card-title')?.textContent?.trim()).toBe('Total tokens');
        expect(host.querySelector('.tum-ui-card-subtitle')?.textContent?.trim()).toBe('last 30 days');
    });

    it('has no caption when neither is set', () => {
        expect(fixture.debugElement.query(By.css('.tum-ui-card-caption'))).toBeNull();
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
class LegacyProjectionHostComponent {}

describe('TumUiCardComponent (deprecated attribute slots)', () => {
    let fixture: ComponentFixture<LegacyProjectionHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [LegacyProjectionHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(LegacyProjectionHostComponent);
        fixture.detectChanges();
    });

    it('projects default content into the card body', () => {
        const body = fixture.debugElement.query(By.css('.tum-ui-card-content .body-slot'));
        expect(body).not.toBeNull();
        expect(body.nativeElement.textContent.trim()).toBe('Body content');
    });

    it('keeps the historical asymmetry the sub-components exist to replace', () => {
        // `[tumUiCardHeader]` lands outside the padded body and `[tumUiCardFooter]` inside it. Asserted rather
        // than fixed, because changing it would move every existing call site's header by the card's padding.
        expect(fixture.debugElement.query(By.css('.header-slot'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('.tum-ui-card-body .header-slot'))).toBeNull();
        expect(fixture.debugElement.query(By.css('.tum-ui-card-body .footer-slot'))).not.toBeNull();
    });
});
