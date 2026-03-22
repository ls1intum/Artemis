import { Component, input, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { of } from 'rxjs';
import { NgxExtendedPdfViewerModule } from 'ngx-extended-pdf-viewer';
import { PdfViewerComponent } from './pdf-viewer.component';

@Component({
    selector: 'ngx-extended-pdf-viewer',
    standalone: true,
    template: '',
})
class MockNgxExtendedPdfViewerComponent {
    src = input<string>();
    page = input<number>();
    customToolbar = input<unknown>();
    pageChange = output<number>();
    pagesLoaded = output<unknown>();
    showToolbar = input<boolean>();
    showSidebarButton = input<boolean>();
    showFindButton = input<boolean>();
    showOpenFileButton = input<boolean>();
    showPrintButton = input<boolean>();
    showDownloadButton = input<boolean>();
    showSecondaryToolbarButton = input<boolean>();
    showPresentationModeButton = input<boolean>();
    showRotateButton = input<boolean>();
    showRotateCwButton = input<boolean>();
    showRotateCcwButton = input<boolean>();
    showHandToolButton = input<boolean>();
    showSpreadButton = input<boolean>();
    showPropertiesButton = input<boolean>();
    showSinglePageModeButton = input<boolean>();
    showVerticalScrollButton = input<boolean>();
    showHorizontalScrollButton = input<boolean>();
    showWrappedScrollButton = input<boolean>();
    showInfiniteScrollButton = input<boolean>();
    showBookModeButton = input<boolean>();
    showTextEditor = input<boolean>();
    showStampEditor = input<boolean>();
    showCommentEditor = input<boolean>();
    showDrawEditor = input<boolean>();
    showHighlightEditor = input<boolean>();
    showSignatureEditor = input<boolean>();
    showMovePageButton = input<boolean>();
    showPagingButtons = input<boolean>();
    showFirstAndLastPageButtons = input<boolean>();
    showPreviousAndNextPageButtons = input<boolean>();
    showPageNumber = input<boolean>();
    showPageLabel = input<boolean>();
    showZoomButtons = input<boolean>();
    showZoomDropdown = input<boolean>();
    showScrollingButtons = input<boolean>();
}

@Component({
    selector: 'pdf-shy-button',
    standalone: true,
    template: '',
})
class MockPdfShyButtonComponent {
    primaryToolbarId = input<string>();
    cssClass = input<string>();
    eventBusName = input<string>();
    l10nId = input<string>();
    l10nLabel = input<string>();
    title = input<string>();
    order = input<number>();
    image = input<string>();
    disabled = input<boolean>();
}

describe('PdfViewerComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<PdfViewerComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PdfViewerComponent],
            providers: [
                {
                    provide: TranslateService,
                    useValue: {
                        get: (key: string) => of(key),
                        instant: (key: string) => key,
                        onLangChange: of({}),
                        onTranslationChange: of({}),
                        onDefaultLangChange: of({}),
                        getCurrentLang: () => 'en',
                    },
                },
            ],
        })
            .overrideComponent(PdfViewerComponent, {
                remove: {
                    imports: [NgxExtendedPdfViewerModule],
                },
                add: {
                    imports: [MockNgxExtendedPdfViewerComponent, MockPdfShyButtonComponent],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(PdfViewerComponent);
        fixture.componentRef.setInput('pdfUrl', 'blob:http://localhost/test.pdf');
        fixture.detectChanges();
    });

    it('should create', () => {
        const component = fixture.componentInstance;
        expect(component).toBeTruthy();
    });

    it('should pass src to the viewer', () => {
        const viewer = fixture.debugElement.query(By.directive(MockNgxExtendedPdfViewerComponent));
        const viewerInstance = viewer.componentInstance as MockNgxExtendedPdfViewerComponent;
        expect(viewerInstance.src()).toBe('blob:http://localhost/test.pdf');
    });

    it('should pass the initial page when provided', () => {
        fixture.componentRef.setInput('initialPage', 3);
        fixture.detectChanges();

        const viewer = fixture.debugElement.query(By.directive(MockNgxExtendedPdfViewerComponent));
        const viewerInstance = viewer.componentInstance as MockNgxExtendedPdfViewerComponent;
        expect(viewerInstance.page()).toBe(3);
    });
});
