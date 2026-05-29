import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { QuizScoringInfoModalComponent } from './quiz-scoring-info-modal.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('QuizScoringInfoModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: QuizScoringInfoModalComponent;
    let fixture: ComponentFixture<QuizScoringInfoModalComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideTemplate(QuizScoringInfoModalComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(QuizScoringInfoModalComponent);
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
