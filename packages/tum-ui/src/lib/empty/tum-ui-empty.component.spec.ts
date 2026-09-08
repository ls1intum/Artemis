import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import {
    TumUiEmptyContentComponent,
    TumUiEmptyDescriptionComponent,
    TumUiEmptyHeaderComponent,
    TumUiEmptyMediaComponent,
    TumUiEmptyTitleComponent,
} from './tum-ui-empty-parts.component';
import { TumUiEmptyComponent } from './tum-ui-empty.component';

@Component({
    imports: [TumUiEmptyComponent, TumUiEmptyHeaderComponent, TumUiEmptyMediaComponent, TumUiEmptyTitleComponent, TumUiEmptyDescriptionComponent, TumUiEmptyContentComponent],
    template: `
        <tum-ui-empty size="small">
            <tum-ui-empty-header>
                <tum-ui-empty-media variant="icon"><span class="glyph">★</span></tum-ui-empty-media>
                <tum-ui-empty-title>No files yet</tum-ui-empty-title>
                <tum-ui-empty-description>Files the agent writes appear here.</tum-ui-empty-description>
            </tum-ui-empty-header>
            <tum-ui-empty-content><button type="button">Start a run</button></tum-ui-empty-content>
        </tum-ui-empty>
    `,
})
class HostComponent {}

describe('TumUiEmptyComponent', () => {
    let fixture: ComponentFixture<HostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        fixture.detectChanges();
    });

    function element(selector: string): HTMLElement {
        return fixture.debugElement.query(By.css(selector)).nativeElement as HTMLElement;
    }

    it('publishes a slot for every part', () => {
        expect(element('tum-ui-empty').getAttribute('data-slot')).toBe('empty');
        expect(element('tum-ui-empty-header').getAttribute('data-slot')).toBe('empty-header');
        expect(element('tum-ui-empty-media').getAttribute('data-slot')).toBe('empty-media');
        expect(element('tum-ui-empty-title').getAttribute('data-slot')).toBe('empty-title');
        expect(element('tum-ui-empty-description').getAttribute('data-slot')).toBe('empty-description');
        expect(element('tum-ui-empty-content').getAttribute('data-slot')).toBe('empty-content');
    });

    it('reflects the size on the root', () => {
        expect(element('tum-ui-empty').getAttribute('data-size')).toBe('small');
    });

    it('adds nothing to the accessibility tree of its own', () => {
        // An empty state is what a region looks like, not an event that just happened: no role, and above all no
        // live region announcing that nothing is there.
        const root = element('tum-ui-empty');
        expect(root.getAttribute('role')).toBeNull();
        expect(root.getAttribute('aria-live')).toBeNull();
    });

    it('hides the media, which only restates the title', () => {
        expect(element('tum-ui-empty-media').getAttribute('aria-hidden')).toBe('true');
        expect(element('tum-ui-empty-media').getAttribute('data-variant')).toBe('icon');
    });

    it('renders the title as text and not as a heading, so a list of empty regions does not flood the outline', () => {
        const title = element('tum-ui-empty-title');
        expect(title.getAttribute('role')).toBeNull();
        expect(title.textContent?.trim()).toBe('No files yet');
        expect(fixture.debugElement.query(By.css('tum-ui-empty h1, tum-ui-empty h2, tum-ui-empty h3'))).toBeNull();
    });

    it('projects the action that resolves the emptiness', () => {
        expect(element('tum-ui-empty-content button').textContent?.trim()).toBe('Start a run');
    });
});

describe('TumUiEmptyComponent (defaults)', () => {
    it('defaults to the medium size and normalises the deprecated middle spellings', async () => {
        await TestBed.configureTestingModule({ imports: [TumUiEmptyComponent] }).compileComponents();
        const fixture = TestBed.createComponent(TumUiEmptyComponent);
        const host = fixture.nativeElement as HTMLElement;
        fixture.detectChanges();
        expect(host.getAttribute('data-size')).toBe('medium');

        fixture.componentRef.setInput('size', 'default');
        fixture.detectChanges();
        expect(host.getAttribute('data-size')).toBe('medium');
    });
});
