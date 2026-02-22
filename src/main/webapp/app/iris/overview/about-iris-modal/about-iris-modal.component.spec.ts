import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { AboutIrisModalComponent } from './about-iris-modal.component';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('AboutIrisModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: AboutIrisModalComponent;
    let fixture: ComponentFixture<AboutIrisModalComponent>;
    let dialogRef: { close: ReturnType<typeof vi.fn> };
    let chatService: { clearChat: ReturnType<typeof vi.fn> };

    beforeEach(async () => {
        vi.spyOn(console, 'warn').mockImplementation(() => {});

        dialogRef = { close: vi.fn() };
        chatService = { clearChat: vi.fn() };

        TestBed.configureTestingModule({
            imports: [AboutIrisModalComponent, MockComponent(IrisLogoComponent), MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [
                { provide: DynamicDialogRef, useValue: dialogRef },
                { provide: IrisChatService, useValue: chatService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });

        fixture = TestBed.createComponent(AboutIrisModalComponent);
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

    it('should expose faXmark and faShield icons', () => {
        expect(component['faXmark']).toBeDefined();
        expect(component['faShield']).toBeDefined();
    });

    it.each([
        ['whatIrisCanDo', 3],
        ['whatToExpect', 3],
    ] as const)('should have %s valid cards in %i', (key, expectedLength) => {
        const cards = component[key];
        expect(cards).toHaveLength(expectedLength);
        expectValidCards(cards);
    });

    it('close() should call dialogRef.close() without clearing chat', () => {
        component.close();
        expect(dialogRef.close).toHaveBeenCalledOnce();
        expect(chatService.clearChat).not.toHaveBeenCalled();
    });

    it('tryIris() should clear chat and close dialog', () => {
        component.tryIris();
        expect(chatService.clearChat).toHaveBeenCalledOnce();
        expect(dialogRef.close).toHaveBeenCalledOnce();
    });

    it.each([
        ['.close-btn', 'close'],
        ['.try-iris-btn', 'tryIris'],
    ] as const)('%s click should call %s()', async (selector, method) => {
        vi.spyOn(component, method);
        fixture.debugElement.query(By.css(selector)).nativeElement.click();
        await fixture.whenStable();
        expect(component[method]).toHaveBeenCalled();
    });
});
