import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PdfViewerComponent } from './pdf-viewer.component';
import { TranslateService } from '@ngx-translate/core';
import { MockDirective } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

// Mock pdfjs-dist
vi.mock('pdfjs-dist/legacy/build/pdf.mjs', () => {
    return {
        __esModule: true,
        GlobalWorkerOptions: {},
        getDocument: vi.fn(),
    };
});

describe('PdfViewerComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<PdfViewerComponent>;
    let component: PdfViewerComponent;
    let mockTranslateService: any;

    beforeEach(async () => {
        mockTranslateService = new MockTranslateService();

        await TestBed.configureTestingModule({
            imports: [PdfViewerComponent, MockDirective(TranslateDirective)],
            providers: [{ provide: TranslateService, useValue: mockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfViewerComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('pdfUrl', 'blob:http://localhost/test.pdf');
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('should create component', () => {
        expect(component).toBeTruthy();
    });

    it('should have required inputs', () => {
        expect(component.pdfUrl()).toBe('blob:http://localhost/test.pdf');
    });

    it('should initialize with loading state', () => {
        expect(component.isLoading()).toBe(true);
        expect(component.error()).toBeUndefined();
        expect(component.totalPages()).toBe(0);
    });

    it('should have view child references', () => {
        fixture.detectChanges();
        expect(component.pdfContainer).toBeDefined();
        expect(component.pdfViewerBox).toBeDefined();
    });

    it('should cleanup on destroy', () => {
        fixture.detectChanges();
        expect(() => fixture.destroy()).not.toThrow();
    });
});
