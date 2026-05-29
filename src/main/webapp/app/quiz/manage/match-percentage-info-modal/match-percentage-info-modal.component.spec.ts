import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { MatchPercentageInfoModalComponent } from './match-percentage-info-modal.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('MatchPercentageInfoModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MatchPercentageInfoModalComponent;
    let fixture: ComponentFixture<MatchPercentageInfoModalComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideTemplate(MatchPercentageInfoModalComponent, '')
            .compileComponents();

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
});
