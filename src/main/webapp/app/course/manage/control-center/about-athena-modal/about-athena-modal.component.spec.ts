import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { AboutAthenaModalComponent } from './about-athena-modal.component';
import { AthenaLogoComponent } from 'app/shared-ui/athena-logo/athena-logo.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('AboutAthenaModalComponent', () => {
    let component: AboutAthenaModalComponent;
    let fixture: ComponentFixture<AboutAthenaModalComponent>;
    let dialogRef: { close: ReturnType<typeof vi.fn> };

    beforeEach(async () => {
        dialogRef = { close: vi.fn() };

        TestBed.configureTestingModule({
            imports: [AboutAthenaModalComponent, MockComponent(AthenaLogoComponent), MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [
                { provide: DynamicDialogRef, useValue: dialogRef },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });

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

    it('should expose faXmark', () => {
        expect(component['faXmark']).toBeDefined();
    });

    it.each([
        ['whatAthenaCanDo', 3],
        ['whatToExpect', 3],
    ] as const)('should have %s valid cards in %i', (key, expectedLength) => {
        const cards = component[key];
        expect(cards).toHaveLength(expectedLength);
        expectValidCards(cards);
    });

    it('close() should call dialogRef.close()', () => {
        component.close();
        expect(dialogRef.close).toHaveBeenCalledOnce();
    });

    it('.close-btn click should call close()', async () => {
        vi.spyOn(component, 'close');
        fixture.debugElement.query(By.css('.close-btn')).nativeElement.click();
        await fixture.whenStable();
        expect(component.close).toHaveBeenCalled();
    });
});
