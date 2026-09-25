import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import {
    TumAetUiEmptyContentComponent,
    TumAetUiEmptyDescriptionComponent,
    TumAetUiEmptyHeaderComponent,
    TumAetUiEmptyMediaComponent,
    TumAetUiEmptyTitleComponent,
} from './tumaet-ui-empty-parts.component';
import { TumAetUiEmptyComponent } from './tumaet-ui-empty.component';

@Component({
    imports: [TumAetUiEmptyComponent, TumAetUiEmptyHeaderComponent, TumAetUiEmptyMediaComponent, TumAetUiEmptyTitleComponent, TumAetUiEmptyDescriptionComponent, TumAetUiEmptyContentComponent],
    template: `
        <tumaet-ui-empty size="small">
            <tumaet-ui-empty-header>
                <tumaet-ui-empty-media variant="icon"><span class="glyph">★</span></tumaet-ui-empty-media>
                <tumaet-ui-empty-title>No files yet</tumaet-ui-empty-title>
                <tumaet-ui-empty-description>Files the agent writes appear here.</tumaet-ui-empty-description>
            </tumaet-ui-empty-header>
            <tumaet-ui-empty-content><button type="button">Start a run</button></tumaet-ui-empty-content>
        </tumaet-ui-empty>
    `,
})
class HostComponent {}

describe('TumAetUiEmptyComponent', () => {
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
        expect(element('tumaet-ui-empty').getAttribute('data-slot')).toBe('empty');
        expect(element('tumaet-ui-empty-header').getAttribute('data-slot')).toBe('empty-header');
        expect(element('tumaet-ui-empty-media').getAttribute('data-slot')).toBe('empty-media');
        expect(element('tumaet-ui-empty-title').getAttribute('data-slot')).toBe('empty-title');
        expect(element('tumaet-ui-empty-description').getAttribute('data-slot')).toBe('empty-description');
        expect(element('tumaet-ui-empty-content').getAttribute('data-slot')).toBe('empty-content');
    });

    it('reflects the size on the root', () => {
        expect(element('tumaet-ui-empty').getAttribute('data-size')).toBe('small');
    });

    it('adds nothing to the accessibility tree of its own', () => {
        // An empty state is what a region looks like, not an event that just happened: no role, and above all no
        // live region announcing that nothing is there.
        const root = element('tumaet-ui-empty');
        expect(root.getAttribute('role')).toBeNull();
        expect(root.getAttribute('aria-live')).toBeNull();
    });

    it('hides the media, which only restates the title', () => {
        expect(element('tumaet-ui-empty-media').getAttribute('aria-hidden')).toBe('true');
        expect(element('tumaet-ui-empty-media').getAttribute('data-variant')).toBe('icon');
    });

    it('renders the title as text and not as a heading, so a list of empty regions does not flood the outline', () => {
        const title = element('tumaet-ui-empty-title');
        expect(title.getAttribute('role')).toBeNull();
        expect(title.textContent?.trim()).toBe('No files yet');
        expect(fixture.debugElement.query(By.css('tumaet-ui-empty h1, tumaet-ui-empty h2, tumaet-ui-empty h3'))).toBeNull();
    });

    it('projects the action that resolves the emptiness', () => {
        expect(element('tumaet-ui-empty-content button').textContent?.trim()).toBe('Start a run');
    });
});

describe('TumAetUiEmptyComponent (defaults)', () => {
    it('defaults to the medium size', async () => {
        await TestBed.configureTestingModule({ imports: [TumAetUiEmptyComponent] }).compileComponents();
        const fixture = TestBed.createComponent(TumAetUiEmptyComponent);
        const host = fixture.nativeElement as HTMLElement;
        fixture.detectChanges();
        expect(host.getAttribute('data-size')).toBe('medium');
    });
});
