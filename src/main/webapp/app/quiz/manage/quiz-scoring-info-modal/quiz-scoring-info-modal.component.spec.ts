import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MockProvider } from 'ng-mocks';
import { QuizScoringInfoModalComponent } from './quiz-scoring-info-modal.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('QuizScoringInfoModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: QuizScoringInfoModalComponent;
    let fixture: ComponentFixture<QuizScoringInfoModalComponent>;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MockProvider(NgbModal), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideTemplate(QuizScoringInfoModalComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(QuizScoringInfoModalComponent);
        component = fixture.componentInstance;
        modalService = TestBed.inject(NgbModal);
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

    it('should open modal with large size', () => {
        const mockContent = { template: 'test-content' };
        const mockModalRef = { componentInstance: {} } as NgbModalRef;
        const openSpy = vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);

        component.open(mockContent);

        expect(openSpy).toHaveBeenCalledWith(mockContent, { size: 'lg' });
    });

    it('should pass content to modal service', () => {
        const mockTemplateRef = document.createElement('ng-template');
        const mockModalRef = { componentInstance: {} } as NgbModalRef;
        const openSpy = vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);

        component.open(mockTemplateRef);

        expect(openSpy).toHaveBeenCalledWith(mockTemplateRef, { size: 'lg' });
    });

    it('should handle different content types', () => {
        const mockModalRef = { componentInstance: {} } as NgbModalRef;
        const openSpy = vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);

        // Test with string content
        component.open('string-content');
        expect(openSpy).toHaveBeenCalledWith('string-content', { size: 'lg' });

        // Test with object content
        const objectContent = { data: 'test' };
        component.open(objectContent);
        expect(openSpy).toHaveBeenCalledWith(objectContent, { size: 'lg' });
    });
});
