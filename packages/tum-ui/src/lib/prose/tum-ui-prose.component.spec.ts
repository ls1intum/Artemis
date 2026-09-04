import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TumUiProseComponent } from './tum-ui-prose.component';

@Component({
    imports: [TumUiProseComponent],
    template: `
        <tum-ui-prose [density]="density()">
            <h2>Heading</h2>
            <p>Body</p>
        </tum-ui-prose>
        <article tumUiProse [innerHTML]="html"></article>
    `,
})
class HostComponent {
    readonly density = signal<'comfortable' | 'compact'>('comfortable');
    readonly html = '<h2>From markdown</h2><pre><code>value</code></pre>';
}

describe('TumUiProseComponent', () => {
    let fixture: ComponentFixture<HostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        fixture.detectChanges();
    });

    function element(selector: string): HTMLElement {
        return fixture.debugElement.query(By.css(selector)).nativeElement as HTMLElement;
    }

    it('projects the document untouched and adds no markup of its own', () => {
        const prose = element('tum-ui-prose');
        expect(prose.querySelector('h2')?.textContent).toBe('Heading');
        expect(prose.querySelector('p')?.textContent).toBe('Body');
        expect(prose.children).toHaveLength(2);
    });

    it('exposes its slot and density on the host', () => {
        expect(element('tum-ui-prose').getAttribute('data-slot')).toBe('prose');
        expect(element('tum-ui-prose').getAttribute('data-density')).toBe('comfortable');

        fixture.componentInstance.density.set('compact');
        fixture.detectChanges();
        expect(element('tum-ui-prose').getAttribute('data-density')).toBe('compact');
    });

    it('styles an element the consumer chose, so a document keeps its own semantics', () => {
        const article = element('article[tumUiProse]');
        expect(article.tagName).toBe('ARTICLE');
        expect(article.classList).toContain('tum-ui-prose');
        expect(article.getAttribute('data-slot')).toBe('prose');
    });

    it('reaches content assigned through innerHTML, which emulated encapsulation cannot', () => {
        // The point of ViewEncapsulation.None: this subtree carries no `_ngcontent` attribute at all, so a scoped
        // stylesheet would miss every element of it.
        const article = element('article[tumUiProse]');
        const heading = article.querySelector('h2')!;
        expect(heading.textContent).toBe('From markdown');
        expect(article.querySelector('pre code')?.textContent).toBe('value');
        expect([...heading.attributes].some((attribute) => attribute.name.startsWith('_ngcontent'))).toBe(false);
    });

    describe('cascade contract', () => {
        // The component's whole justification is that `.tum-ui-prose h2` (0,1,1) outranks a host page's bare `h2`
        // rule (0,0,1) in the same unlayered origin. That is a property of the emitted stylesheet, so it is asserted
        // on the stylesheet. The rendered consequence — real computed sizes against real Bootstrap rules — is
        // measured in a browser by the `CascadeRegression` story.
        function proseStylesheet(): string {
            return [...document.querySelectorAll('style')].map((style) => style.textContent ?? '').find((text) => text.includes('.tum-ui-prose')) ?? '';
        }

        it('is emitted at all', () => {
            expect(proseStylesheet()).not.toBe('');
        });

        it('scopes every heading rule behind the class, so it outranks a bare element rule', () => {
            const css = proseStylesheet();
            for (const heading of ['h1', 'h2', 'h3', 'h4', 'h5', 'h6']) {
                expect(css).toContain(`.tum-ui-prose ${heading}`);
            }
        });

        it('never lowers its own specificity with :where(), and never reaches for !important', () => {
            const css = proseStylesheet();
            expect(css).not.toContain(':where(');
            expect(css).not.toContain('!important');
        });

        it('stays unlayered, because a layered rule loses to an unlayered host page rule at any specificity', () => {
            expect(proseStylesheet()).not.toContain('@layer');
        });
    });
});
