import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
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
    let compiled: DebugElement;
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
        compiled = fixture.debugElement;
        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should expose correct icons', () => {
        expect(component['faXmark']).toBeDefined();
        expect(component['faShield']).toBeDefined();
    });

    it('should have 3 cards in whatIrisCanDo', () => {
        const cards = component['whatIrisCanDo'];
        expect(cards).toHaveLength(3);
        for (const card of cards) {
            expect(card.titleKey).toBeDefined();
            expect(card.descKey).toBeDefined();
            expect(card.icon).toBeDefined();
        }
    });

    it('should have 3 cards in whatToExpect', () => {
        const cards = component['whatToExpect'];
        expect(cards).toHaveLength(3);
        for (const card of cards) {
            expect(card.titleKey).toBeDefined();
            expect(card.descKey).toBeDefined();
            expect(card.icon).toBeDefined();
        }
    });

    it('close() should call dialogRef.close() and not clearChat()', () => {
        component.close();
        expect(dialogRef.close).toHaveBeenCalledOnce();
        expect(chatService.clearChat).not.toHaveBeenCalled();
    });

    it('tryIris() should call clearChat() and dialogRef.close()', () => {
        component.tryIris();
        expect(chatService.clearChat).toHaveBeenCalledOnce();
        expect(dialogRef.close).toHaveBeenCalledOnce();
    });

    it('close button click should call close()', async () => {
        vi.spyOn(component, 'close');
        const closeBtn = compiled.query(By.css('.close-btn'));
        closeBtn.nativeElement.click();
        await fixture.whenStable();
        expect(component.close).toHaveBeenCalled();
    });

    it('Try Iris button click should call tryIris()', async () => {
        vi.spyOn(component, 'tryIris');
        const tryIrisBtn = compiled.query(By.css('.try-iris-btn'));
        tryIrisBtn.nativeElement.click();
        await fixture.whenStable();
        expect(component.tryIris).toHaveBeenCalled();
    });
});
