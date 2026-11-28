import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { LandingFaqComponent } from 'app/core/landing/landing-faq.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';

describe('LandingFaqComponent', () => {
    let fixture: ComponentFixture<LandingFaqComponent>;

    const toggles = () => Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button[aria-expanded]'));
    const expandedStates = () => toggles().map((toggle) => toggle.getAttribute('aria-expanded'));

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [LandingFaqComponent] })
            .overrideComponent(LandingFaqComponent, {
                remove: { imports: [ArtemisTranslatePipe] },
                add: { imports: [TranslatePipeMock] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(LandingFaqComponent);
        fixture.detectChanges();
    });

    it('lists every question collapsed', () => {
        const component = fixture.componentInstance;
        expect(toggles().map((toggle) => toggle.textContent?.trim())).toEqual(component.faqItems.map((item) => item.questionKey));
        expect(expandedStates().every((state) => state === 'false')).toBe(true);
    });

    it('keeps one answer open at a time', async () => {
        const toggle = async (index: number) => {
            toggles()[index].click();
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();
        };

        await toggle(1);
        expect(expandedStates().indexOf('true')).toBe(1);

        await toggle(3);
        expect(expandedStates().filter((state) => state === 'true')).toHaveLength(1);
        expect(expandedStates().indexOf('true')).toBe(3);

        await toggle(3);
        expect(expandedStates().every((state) => state === 'false')).toBe(true);
    });

    it('links the documentation in the documentation answer only', () => {
        const component = fixture.componentInstance;
        const linked = component.formatAnswer('landing.faq.items.docs.question', 'See docs.artemis.tum.de for details.');
        expect(linked).toContain('href="https://docs.artemis.tum.de"');
        expect(component.formatAnswer('landing.faq.items.free.question', 'See docs.artemis.tum.de for details.')).toBe('See docs.artemis.tum.de for details.');
    });
});
