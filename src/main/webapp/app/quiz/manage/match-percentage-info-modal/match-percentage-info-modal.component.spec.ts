import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MatchPercentageInfoModalComponent } from './match-percentage-info-modal.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('MatchPercentageInfoModalComponent', () => {
    let component: MatchPercentageInfoModalComponent;
    let fixture: ComponentFixture<MatchPercentageInfoModalComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(MatchPercentageInfoModalComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should have question circle icon defined', () => {
        expect(component.farQuestionCircle).toBeDefined();
    });

    it('should be hidden by default', () => {
        expect(component.isVisible()).toBe(false);
    });

    it('should show the dialog when opened', () => {
        component.open();

        expect(component.isVisible()).toBe(true);
    });

    it('should render the explanation in a labelled dialog and close it with the close button', async () => {
        component.open();
        fixture.detectChanges();
        await fixture.whenStable();

        const dialog = document.querySelector('[role="dialog"]');
        expect(dialog).not.toBeNull();
        expect(dialog!.textContent).toContain('artemisApp.quizExercise.matchLetterCase.title');

        const closeButton = Array.from(dialog!.querySelectorAll('button')).find((button) => button.textContent?.includes('entity.action.close'));
        closeButton!.click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.isVisible()).toBe(false);
        expect(document.querySelector('[role="dialog"]')).toBeNull();
    });
});
