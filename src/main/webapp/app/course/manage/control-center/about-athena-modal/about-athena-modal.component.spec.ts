import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { AboutAthenaModalComponent } from './about-athena-modal.component';
import { AthenaLogoComponent } from 'app/shared-ui/athena-logo/athena-logo.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('AboutAthenaModalComponent', () => {
    let component: AboutAthenaModalComponent;
    let fixture: ComponentFixture<AboutAthenaModalComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AboutAthenaModalComponent, MockComponent(AthenaLogoComponent), MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(AboutAthenaModalComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    const expectValidCards = (cards: { titleKey: string; descKey: string; icon: object }[]) => {
        for (const card of cards) {
            expect(card.titleKey).toBeDefined();
            expect(card.descKey).toBeDefined();
            expect(card.icon).toBeDefined();
        }
    };

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should default to hidden', () => {
        expect(component.visible()).toBe(false);
    });

    it('should hide itself when closed', () => {
        component.visible.set(true);

        component.close();

        expect(component.visible()).toBe(false);
    });

    it.each([
        ['whatAthenaCanDo', 3],
        ['whatToExpect', 3],
    ] as const)('should have %s valid cards in %i', (key, expectedLength) => {
        const cards = component[key];
        expect(cards).toHaveLength(expectedLength);
        expectValidCards(cards);
    });
});
