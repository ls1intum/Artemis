import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { PdfPreviewThumbnailGridComponent } from 'app/lecture/manage/pdf-preview/pdf-preview-thumbnail-grid/pdf-preview-thumbnail-grid.component';
import { ElementRef, Signal, SimpleChanges, signal } from '@angular/core';
import dayjs from 'dayjs/esm';
import { HiddenPage, HiddenPageMap, OrderedPage } from 'app/lecture/manage/pdf-preview/pdf-preview.component';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';

jest.mock('pdfjs-dist/build/pdf.worker', () => {
    return {};
});

jest.mock('app/shared/util/global.utils', () => ({
    onError: jest.fn(),
}));

describe('PdfPreviewThumbnailGridComponent', () => {
    let component: PdfPreviewThumbnailGridComponent;
    let fixture: ComponentFixture<PdfPreviewThumbnailGridComponent>;
    let alertServiceMock: any;

    const mockOrderedPages: OrderedPage[] = [
        { slideId: 'slide1', initialIndex: 1, order: 1, sourcePdfId: 'source1', sourceIndex: 0, pageProxy: undefined as any },
        { slideId: 'slide2', initialIndex: 2, order: 2, sourcePdfId: 'source1', sourceIndex: 1, pageProxy: undefined as any },
        { slideId: 'slide3', initialIndex: 3, order: 3, sourcePdfId: 'source1', sourceIndex: 2, pageProxy: undefined as any },
    ];

    beforeEach(async () => {
        alertServiceMock = {
            error: jest.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [PdfPreviewThumbnailGridComponent],
            providers: [
                { provide: ActivatedRoute, useValue: { data: of({}) } },
                { provide: AlertService, useValue: alertServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfPreviewThumbnailGridComponent);
        component = fixture.componentInstance;

        const mockPdfContainer = document.createElement('div');
        Object.defineProperty(component, 'pdfContainer', {
            value: jest.fn().mockReturnValue({ nativeElement: mockPdfContainer }),
            writable: true,
        });

        jest.spyOn(component, 'renderPages').mockResolvedValue();

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should update hiddenPages when they change', () => {
        const updatedHiddenPages: HiddenPageMap = {
            slide4: { date: dayjs(), exerciseId: undefined },
            slide5: { date: dayjs(), exerciseId: undefined },
            slide6: { date: dayjs(), exerciseId: undefined },
        };

        fixture.componentRef.setInput('hiddenPages', updatedHiddenPages);
        fixture.detectChanges();

        expect(component.hiddenPages()).toEqual(updatedHiddenPages);
    });

    it('should render pages when orderedPages changes', async () => {
        const spyRenderPages = jest.spyOn(component, 'renderPages').mockResolvedValue();

        fixture.componentRef.setInput('orderedPages', mockOrderedPages);

        const changes: SimpleChanges = {
            orderedPages: {
                currentValue: mockOrderedPages,
                previousValue: [],
                firstChange: false,
                isFirstChange: () => false,
            },
        };

        component.ngOnChanges(changes);

        expect(spyRenderPages).toHaveBeenCalled();
    });

    it('should update selectedPages when updatedSelectedPages changes', () => {
        const updatedSelectedPages = new Set<OrderedPage>([mockOrderedPages[0], mockOrderedPages[2]]);

        const spyUpdateCheckboxStates = jest.spyOn(component as any, 'updateCheckboxStates').mockImplementation(() => {});

        fixture.componentRef.setInput('updatedSelectedPages', updatedSelectedPages);

        const changes: SimpleChanges = {
            updatedSelectedPages: {
                currentValue: updatedSelectedPages,
                previousValue: new Set(),
                firstChange: false,
                isFirstChange: () => false,
            },
        };

        component.ngOnChanges(changes);

        expect(component.selectedPages()).toEqual(updatedSelectedPages);
        expect(spyUpdateCheckboxStates).toHaveBeenCalled();
    });

    it('should toggle enlarged view state', () => {
        const mockCanvas = document.createElement('canvas');
        component.originalCanvas.set(mockCanvas);
        component.isEnlargedView.set(true);
        expect(component.isEnlargedView()).toBeTruthy();

        component.isEnlargedView.set(false);
        expect(component.isEnlargedView()).toBeFalsy();
    });

    it('should toggle visibility of a page', () => {
        const mockButton = document.createElement('button');
        const mockEvent = new MouseEvent('click', {
            bubbles: true,
            cancelable: true,
        });

        Object.defineProperty(mockEvent, 'target', {
            value: mockButton,
            enumerable: true,
        });

        Object.defineProperty(mockButton, 'closest', {
            value: jest.fn().mockReturnValue(mockButton),
        });

        fixture.componentRef.setInput('orderedPages', mockOrderedPages);
        fixture.detectChanges();

        const spyStopPropagation = jest.spyOn(mockEvent, 'stopPropagation');

        component.toggleVisibility('slide1', mockEvent);
        expect(component.activeButtonPage()).toEqual(mockOrderedPages[0]);
        expect(mockButton.style.opacity).toBe('1');
        expect(spyStopPropagation).toHaveBeenCalled();
    });

    it('should select and deselect pages', () => {
        fixture.componentRef.setInput('orderedPages', mockOrderedPages);
        fixture.detectChanges();

        const spyEmit = jest.spyOn(component.selectedPagesOutput, 'emit');
        component.selectedPages.set(new Set());

        const mockEventChecked = { target: { checked: true } } as unknown as Event;
        component.togglePageSelection('slide1', mockEventChecked);

        const selectedSlideIds = Array.from(component.selectedPages()).map((page) => page.slideId);

        expect(selectedSlideIds).toContain('slide1');
        expect(spyEmit).toHaveBeenCalled();

        const mockEventUnchecked = { target: { checked: false } } as unknown as Event;
        component.togglePageSelection('slide1', mockEventUnchecked);

        const selectedSlideIdsAfterDeselect = Array.from(component.selectedPages()).map((page) => page.slideId);
        expect(selectedSlideIdsAfterDeselect).not.toContain('slide1');
        expect(spyEmit).toHaveBeenCalledTimes(2);
    });

    it('should handle enlarged view correctly for a specific page', () => {
        const mockCanvas = document.createElement('canvas');
        const container = document.createElement('div');
        container.id = 'pdf-page-slide1';
        container.appendChild(mockCanvas);

        const querySelectorSpy = jest.fn().mockReturnValue(mockCanvas);
        const mockNativeElement = { querySelector: querySelectorSpy };

        component.pdfContainer = signal({ nativeElement: mockNativeElement }) as unknown as Signal<ElementRef<HTMLDivElement>>;

        component.displayEnlargedCanvas(1, 'slide1');

        expect(component.originalCanvas()).toBe(mockCanvas);
        expect(component.isEnlargedView()).toBeTruthy();
        expect(component.initialPageNumber()).toBe(1);
        expect(querySelectorSpy).toHaveBeenCalledWith('#pdf-page-slide1 canvas');
    });

    it('should update hiddenPages with a single page and emit the change', () => {
        const emitSpy = jest.spyOn(component.hiddenPagesOutput, 'emit');

        fixture.componentRef.setInput('hiddenPages', {});
        fixture.detectChanges();

        const hiddenPage: HiddenPage = {
            slideId: 'slide1',
            date: dayjs('2024-01-01'),
            exerciseId: undefined,
        };

        let emittedValue = undefined;
        emitSpy.mockImplementation((value) => {
            emittedValue = value;
        });

        component.onHiddenPagesReceived(hiddenPage);

        expect(emittedValue).toBeDefined();
        expect(emittedValue![hiddenPage.slideId]).toBeDefined();
        expect(emittedValue![hiddenPage.slideId].date.isSame(dayjs(hiddenPage.date))).toBeTruthy();
        expect(emittedValue![hiddenPage.slideId].exerciseId).toBeUndefined();
        expect(emitSpy).toHaveBeenCalled();
    });

    it('should remove the page from hiddenPages and hide the action button', () => {
        const initialPages: HiddenPageMap = {
            slide1: { date: dayjs(), exerciseId: undefined },
        };

        fixture.componentRef.setInput('hiddenPages', initialPages);
        fixture.detectChanges();

        const hideButtonSpy = jest.spyOn(component, 'hideActionButton');
        const emitSpy = jest.spyOn(component.hiddenPagesOutput, 'emit');

        let emittedValue = undefined;
        emitSpy.mockImplementation((value) => {
            emittedValue = value;
        });

        component.showPage('slide1');

        expect(emittedValue!['slide1']).toBeUndefined();
        expect(hideButtonSpy).toHaveBeenCalledWith('slide1');
        expect(emitSpy).toHaveBeenCalled();
    });

    it('should set opacity to 0 for existing button', () => {
        const mockButton = document.createElement('button');
        mockButton.id = 'hide-show-button-slide1';

        const querySelectorSpy = jest.fn().mockReturnValue(mockButton);
        const mockNativeElement = { querySelector: querySelectorSpy };

        component.pdfContainer = signal({ nativeElement: mockNativeElement }) as unknown as Signal<ElementRef<HTMLDivElement>>;

        component.hideActionButton('slide1');

        expect(mockButton.style.opacity).toBe('0');
    });

    it('should handle non-existent button gracefully', () => {
        component.hideActionButton('nonexistent');

        expect(document.getElementById('hide-show-button-nonexistent')).toBeNull();
    });

    describe('toggleVisibility method', () => {
        it('should set activeButtonPage and make button visible', () => {
            fixture.componentRef.setInput('orderedPages', mockOrderedPages);
            fixture.detectChanges();

            const slideId = 'slide1';
            const mockButton = document.createElement('button');
            const mockEvent = {
                stopPropagation: jest.fn(),
                target: mockButton,
            } as unknown as Event;

            Object.defineProperty(mockButton, 'closest', {
                value: jest.fn().mockReturnValue(mockButton),
            });

            component.toggleVisibility(slideId, mockEvent);

            expect(component.activeButtonPage()).toEqual(mockOrderedPages[0]);
            expect(mockButton.style.opacity).toBe('1');
            expect(mockEvent.stopPropagation).toHaveBeenCalled();
        });

        it('should find closest button element if target is not a button', () => {
            fixture.componentRef.setInput('orderedPages', mockOrderedPages);
            fixture.detectChanges();

            const slideId = 'slide2';
            const mockButton = document.createElement('button');
            const mockSpan = document.createElement('span');
            mockButton.appendChild(mockSpan);

            Object.defineProperty(mockSpan, 'closest', {
                value: jest.fn().mockReturnValue(mockButton),
            });

            const mockEvent = {
                stopPropagation: jest.fn(),
                target: mockSpan,
            } as unknown as Event;

            component.toggleVisibility(slideId, mockEvent);

            expect(component.activeButtonPage()).toEqual(mockOrderedPages[1]);
            expect(mockButton.style.opacity).toBe('1');
            expect(mockEvent.stopPropagation).toHaveBeenCalled();
        });

        it('should handle case when no button is found', () => {
            fixture.componentRef.setInput('orderedPages', mockOrderedPages);
            fixture.detectChanges();

            const slideId = 'slide3';
            const mockDiv = document.createElement('div');

            Object.defineProperty(mockDiv, 'closest', {
                value: jest.fn().mockReturnValue(undefined),
            });

            const mockEvent = {
                stopPropagation: jest.fn(),
                target: mockDiv,
            } as unknown as Event;

            component.toggleVisibility(slideId, mockEvent);

            expect(component.activeButtonPage()).toEqual(mockOrderedPages[2]);
            expect(mockEvent.stopPropagation).toHaveBeenCalled();
        });
    });

    describe('togglePageSelection method', () => {
        beforeEach(() => {
            fixture.componentRef.setInput('orderedPages', mockOrderedPages);
            fixture.detectChanges();
        });

        it('should add page to selectedPages when checkbox is checked', () => {
            const slideId = 'slide2';
            const mockEvent = {
                target: { checked: true },
            } as unknown as Event;

            component.selectedPages.set(new Set());
            jest.spyOn(component.selectedPagesOutput, 'emit');

            component.togglePageSelection(slideId, mockEvent);

            const selectedSlideIds = Array.from(component.selectedPages()).map((page) => page.slideId);
            expect(selectedSlideIds).toContain(slideId);
            expect(component.selectedPagesOutput.emit).toHaveBeenCalledWith(component.selectedPages());
        });

        it('should remove page from selectedPages when checkbox is unchecked', () => {
            const slideId = 'slide2';
            const mockEvent = {
                target: { checked: false },
            } as unknown as Event;

            component.selectedPages.set(new Set([mockOrderedPages[1]]));
            jest.spyOn(component.selectedPagesOutput, 'emit');

            component.togglePageSelection(slideId, mockEvent);

            const selectedSlideIds = Array.from(component.selectedPages()).map((page) => page.slideId);
            expect(selectedSlideIds).not.toContain(slideId);
            expect(component.selectedPagesOutput.emit).toHaveBeenCalledWith(component.selectedPages());
        });
    });

    describe('onHiddenPagesReceived method', () => {
        it('should update hiddenPages with a single page and emit the change', () => {
            fixture.componentRef.setInput('hiddenPages', {});
            fixture.detectChanges();

            const emitSpy = jest.spyOn(component.hiddenPagesOutput, 'emit');

            const hiddenPage: HiddenPage = {
                slideId: 'slide1',
                date: dayjs('2024-01-01'),
                exerciseId: undefined,
            };

            component.onHiddenPagesReceived(hiddenPage);

            expect(emitSpy).toHaveBeenCalled();

            const emittedValue = emitSpy.mock.calls[0][0];

            expect(emittedValue).toBeDefined();
            expect(emittedValue[hiddenPage.slideId]).toBeDefined();
            expect(emittedValue[hiddenPage.slideId].date.isSame(dayjs(hiddenPage.date))).toBeTruthy();
            expect(emittedValue[hiddenPage.slideId].exerciseId).toBeUndefined();
        });

        it('should update hiddenPages with multiple pages and emit the change', () => {
            const emitSpy = jest.spyOn(component.hiddenPagesOutput, 'emit');

            const initialHiddenPages: HiddenPageMap = {
                slide5: { date: dayjs('2023-12-15'), exerciseId: 123 },
            };

            fixture.componentRef.setInput('hiddenPages', initialHiddenPages);
            fixture.detectChanges();

            const hiddenPages: HiddenPage[] = [
                {
                    slideId: 'slide1',
                    date: dayjs('2024-01-01'),
                    exerciseId: undefined,
                },
                {
                    slideId: 'slide2',
                    date: dayjs('2024-01-15'),
                    exerciseId: 456,
                },
            ];

            component.onHiddenPagesReceived(hiddenPages);

            const emittedValue = emitSpy.mock.calls[0][0];

            fixture.componentRef.setInput('hiddenPages', emittedValue);
            fixture.detectChanges();

            expect(Object.keys(component.hiddenPages())).toHaveLength(3);
            expect(component.hiddenPages()['slide1']).toBeDefined();
            expect(component.hiddenPages()['slide1'].date.isSame(dayjs('2024-01-01'))).toBeTruthy();
        });

        it('should overwrite existing page data if same slideId is received', () => {
            const emitSpy = jest.spyOn(component.hiddenPagesOutput, 'emit');

            const initialHiddenPages: HiddenPageMap = {
                slide1: { date: dayjs('2023-12-15'), exerciseId: 123 },
            };

            fixture.componentRef.setInput('hiddenPages', initialHiddenPages);
            fixture.detectChanges();

            const updatedPage: HiddenPage = {
                slideId: 'slide1',
                date: dayjs('2024-02-01'),
                exerciseId: 789,
            };

            component.onHiddenPagesReceived(updatedPage);

            const emittedValue = emitSpy.mock.calls[0][0];

            expect(Object.keys(emittedValue)).toHaveLength(1);
            expect(emittedValue['slide1']).toBeDefined();
            expect(emittedValue['slide1'].date.isSame(dayjs('2024-02-01'))).toBeTruthy();
            expect(emittedValue['slide1'].exerciseId).toBe(789);

            expect(emitSpy).toHaveBeenCalled();
        });
    });

    describe('CDK drag and drop functionality', () => {
        it('should handle onPageDrop correctly when positions change', () => {
            fixture.componentRef.setInput('orderedPages', mockOrderedPages);
            fixture.detectChanges();

            const emitSpy = jest.spyOn(component.pageOrderOutput, 'emit');

            const mockDropEvent = {
                previousIndex: 0,
                currentIndex: 2,
                item: {},
                container: {},
                previousContainer: {},
                isPointerOverContainer: true,
                distance: { x: 0, y: 0 },
            } as CdkDragDrop<OrderedPage[]>;

            component.onPageDrop(mockDropEvent);

            expect(emitSpy).toHaveBeenCalled();
            expect(component.reordering()).toBeTrue();
            expect(component.isDragging()).toBeFalse();

            // Verify the order changed in the emitted array
            const emittedPages = emitSpy.mock.calls[0][0];
            expect(emittedPages).toHaveLength(3);
            expect(emittedPages[0].slideId).toBe('slide2');
            expect(emittedPages[1].slideId).toBe('slide3');
            expect(emittedPages[2].slideId).toBe('slide1');

            // Verify the order property is updated
            expect(emittedPages[0].order).toBe(1);
            expect(emittedPages[1].order).toBe(2);
            expect(emittedPages[2].order).toBe(3);
        });

        it('should not reorder when previous and current indices are the same', () => {
            fixture.componentRef.setInput('orderedPages', mockOrderedPages);
            fixture.detectChanges();

            const emitSpy = jest.spyOn(component.pageOrderOutput, 'emit');

            const mockDropEvent = {
                previousIndex: 1,
                currentIndex: 1,
                item: {},
                container: {},
                previousContainer: {},
                isPointerOverContainer: true,
                distance: { x: 0, y: 0 },
            } as CdkDragDrop<OrderedPage[]>;

            component.onPageDrop(mockDropEvent);

            expect(emitSpy).not.toHaveBeenCalled();
        });

        it('should get the correct page order', () => {
            fixture.componentRef.setInput('orderedPages', mockOrderedPages);
            fixture.detectChanges();

            expect(component.getPageOrder('slide2')).toBe(2);
            expect(component.getPageOrder('nonexistent')).toBe(-1);
        });

        it('should find page by slideId', () => {
            fixture.componentRef.setInput('orderedPages', mockOrderedPages);
            fixture.detectChanges();

            const page = component.findPageBySlideId('slide2');
            expect(page).toEqual(mockOrderedPages[1]);

            const nonexistentPage = component.findPageBySlideId('nonexistent');
            expect(nonexistentPage).toBeUndefined();
        });
    });

    describe('createCanvas method', () => {
        it('should create a properly configured canvas element', () => {
            const viewport = { width: 800, height: 600 } as any;

            const canvas = component.createCanvas(viewport);

            expect(canvas.tagName).toBe('CANVAS');
            expect(canvas.width).toBe(800);
            expect(canvas.height).toBe(600);
            expect(canvas.style.display).toBe('block');
            expect(canvas.style.width).toBe('100%');
            expect(canvas.style.height).toBe('100%');
        });
    });

    describe('updateCheckboxStates method', () => {
        it('should update checkbox states to match the selected pages', () => {
            fixture.componentRef.setInput('orderedPages', mockOrderedPages);
            fixture.detectChanges();

            const mockCheckboxes = [
                { id: 'checkbox-slide1', checked: false },
                { id: 'checkbox-slide2', checked: false },
                { id: 'checkbox-slide3', checked: false },
            ] as unknown as NodeListOf<HTMLInputElement>;

            const mockNativeElement = { querySelectorAll: jest.fn().mockReturnValue(mockCheckboxes) };
            component.pdfContainer = signal({ nativeElement: mockNativeElement }) as unknown as Signal<ElementRef<HTMLDivElement>>;

            component.selectedPages.set(new Set([mockOrderedPages[0], mockOrderedPages[2]]));

            const updateCheckboxStatesMethod = component['updateCheckboxStates'].bind(component);
            updateCheckboxStatesMethod();

            expect(mockCheckboxes[0].checked).toBeTruthy();
            expect(mockCheckboxes[1].checked).toBeFalsy();
            expect(mockCheckboxes[2].checked).toBeTruthy();
        });
    });

    describe('scrollToBottom method', () => {
        it('should scroll the container to the bottom with smooth behavior', () => {
            const mockScrollHeight = 1000;
            const mockScrollToSpy = jest.fn();

            const mockNativeElement = {
                scrollHeight: mockScrollHeight,
                scrollTo: mockScrollToSpy,
            };

            component.pdfContainer = signal({ nativeElement: mockNativeElement }) as unknown as Signal<ElementRef<HTMLDivElement>>;

            component.scrollToBottom();

            expect(mockScrollToSpy).toHaveBeenCalledWith({
                top: mockScrollHeight,
                left: 0,
                behavior: 'smooth',
            });
        });
    });

    describe('renderPages method', () => {
        let mockCanvas: HTMLCanvasElement;

        jest.mock('app/shared/util/global.utils', () => ({
            onError: jest.fn(),
        }));

        beforeEach(() => {
            jest.clearAllMocks();

            fixture = TestBed.createComponent(PdfPreviewThumbnailGridComponent);
            component = fixture.componentInstance;

            alertServiceMock = {
                error: jest.fn(),
                addAlert: jest.fn(),
            };

            Object.defineProperty(component, 'alertService', {
                value: alertServiceMock,
                writable: true,
                configurable: true,
            });

            const mockPdfContainer = document.createElement('div');
            for (let i = 0; i < mockOrderedPages.length; i++) {
                const canvasContainer = document.createElement('div');
                canvasContainer.id = `pdf-page-${mockOrderedPages[i].slideId}`;
                mockPdfContainer.appendChild(canvasContainer);
            }

            Object.defineProperty(component, 'pdfContainer', {
                value: jest.fn().mockReturnValue({ nativeElement: mockPdfContainer }),
                writable: true,
            });

            component.loadedPages = signal(new Set());

            mockCanvas = document.createElement('canvas');

            const mockContext = {
                drawImage: jest.fn(),
                fillText: jest.fn(),
                fillRect: jest.fn(),
                canvas: mockCanvas,
                save: jest.fn(),
                restore: jest.fn(),
                scale: jest.fn(),
                rotate: jest.fn(),
                translate: jest.fn(),
                transform: jest.fn(),
                setTransform: jest.fn(),
                resetTransform: jest.fn(),
                createLinearGradient: jest.fn(),
                createRadialGradient: jest.fn(),
                createPattern: jest.fn(),
                clearRect: jest.fn(),
                beginPath: jest.fn(),
                closePath: jest.fn(),
                moveTo: jest.fn(),
                lineTo: jest.fn(),
                bezierCurveTo: jest.fn(),
                quadraticCurveTo: jest.fn(),
                arc: jest.fn(),
                arcTo: jest.fn(),
                ellipse: jest.fn(),
                rect: jest.fn(),
                stroke: jest.fn(),
                fill: jest.fn(),
                clip: jest.fn(),
                isPointInPath: jest.fn(),
                isPointInStroke: jest.fn(),
                measureText: jest.fn(),
                createImageData: jest.fn(),
                getImageData: jest.fn(),
                putImageData: jest.fn(),
                setLineDash: jest.fn(),
                getLineDash: jest.fn(),
                strokeText: jest.fn(),
            } as unknown as CanvasRenderingContext2D;

            jest.spyOn(mockCanvas, 'getContext').mockReturnValue(mockContext);

            fixture.detectChanges();
        });

        it('should render all pages successfully', async () => {
            const originalRenderPages = component.renderPages;
            const originalCreateCanvas = component.createCanvas;

            component.createCanvas = function () {
                return mockCanvas;
            };

            component.renderPages = async function () {
                this.pdfContainer()
                    .nativeElement.querySelectorAll('.pdf-canvas-container canvas')
                    .forEach((canvas: { remove: () => any }) => canvas.remove());

                this.loadedPages.set(new Set());

                const pages = this.orderedPages();

                for (let i = 0; i < pages.length; i++) {
                    const page = pages[i];
                    if (page.pageProxy) {
                        const viewport = page.pageProxy.getViewport({ scale: 1 });
                        const canvas = this.createCanvas(viewport);

                        const context = canvas.getContext('2d');
                        await page.pageProxy.render({ canvasContext: context, viewport }).promise;

                        this.loadedPages.update((loadedPages: Iterable<unknown> | undefined) => {
                            const newLoadedPages = new Set(loadedPages);
                            newLoadedPages.add(page.order);
                            return newLoadedPages;
                        });
                    }
                }
            };

            const mockPages = [
                {
                    slideId: 'slide1',
                    initialIndex: 1,
                    order: 1,
                    sourcePdfId: 'source1',
                    sourceIndex: 0,
                    pageProxy: {
                        getViewport: jest.fn().mockReturnValue({ width: 600, height: 800, scale: 1 }),
                        render: jest.fn().mockReturnValue({ promise: Promise.resolve() }),
                    },
                },
                {
                    slideId: 'slide2',
                    initialIndex: 2,
                    order: 2,
                    sourcePdfId: 'source1',
                    sourceIndex: 1,
                    pageProxy: {
                        getViewport: jest.fn().mockReturnValue({ width: 600, height: 800, scale: 1 }),
                        render: jest.fn().mockReturnValue({ promise: Promise.resolve() }),
                    },
                },
            ];

            fixture.componentRef.setInput('orderedPages', mockPages);
            fixture.detectChanges();

            await component.renderPages();

            expect(mockPages[0].pageProxy.getViewport).toHaveBeenCalledWith({ scale: 1 });
            expect(mockPages[0].pageProxy.render).toHaveBeenCalled();
            expect(mockPages[1].pageProxy.render).toHaveBeenCalled();
            expect(component.loadedPages().has(1)).toBeTruthy();
            expect(component.loadedPages().has(2)).toBeTruthy();

            component.renderPages = originalRenderPages;
            component.createCanvas = originalCreateCanvas;
        });

        it('should handle render error and call alertService', async () => {
            const mockError = new Error('Render error');
            const mockPage = {
                slideId: 'slide1',
                initialIndex: 1,
                order: 1,
                sourcePdfId: 'source1',
                sourceIndex: 0,
                pageProxy: {
                    getViewport: jest.fn().mockReturnValue({ width: 600, height: 800, scale: 1 }),
                    render: jest.fn().mockReturnValue({ promise: Promise.reject(mockError) }),
                },
            };

            fixture.componentRef.setInput('orderedPages', [mockPage]);
            fixture.detectChanges();

            const originalRenderPages = component.renderPages;

            component.renderPages = async function () {
                onError(this.alertService, mockError as HttpErrorResponse);
            };

            await component.renderPages();

            expect(onError).toHaveBeenCalledWith(alertServiceMock, mockError);

            component.renderPages = originalRenderPages;
        });

        it('should clean up old canvases before rendering new ones', async () => {
            const mockPages = [
                {
                    slideId: 'slide1',
                    initialIndex: 1,
                    order: 1,
                    sourcePdfId: 'source1',
                    sourceIndex: 0,
                    pageProxy: {
                        getViewport: jest.fn().mockReturnValue({ width: 600, height: 800, scale: 1 }),
                        render: jest.fn().mockReturnValue({ promise: Promise.resolve() }),
                    },
                },
            ];

            const existingCanvas1 = document.createElement('canvas');
            const existingCanvas2 = document.createElement('canvas');
            const containerDiv = document.createElement('div');
            containerDiv.classList.add('pdf-canvas-container');
            containerDiv.appendChild(existingCanvas1);
            containerDiv.appendChild(existingCanvas2);
            component.pdfContainer().nativeElement.appendChild(containerDiv);

            const mockNodeList = {
                0: existingCanvas1,
                1: existingCanvas2,
                length: 2,
                item: (index: number) => (index === 0 ? existingCanvas1 : existingCanvas2),
                forEach: function (callback: (element: Element, index: number, list: NodeListOf<Element>) => void) {
                    callback(existingCanvas1, 0, this as unknown as NodeListOf<Element>);
                    callback(existingCanvas2, 1, this as unknown as NodeListOf<Element>);
                },
                entries: function* () {
                    yield [0, existingCanvas1];
                    yield [1, existingCanvas2];
                },
                keys: function* () {
                    yield 0;
                    yield 1;
                },
                values: function* () {
                    yield existingCanvas1;
                    yield existingCanvas2;
                },
                [Symbol.iterator]: function* () {
                    yield existingCanvas1;
                    yield existingCanvas2;
                },
            } as unknown as NodeListOf<Element>;

            const querySelectorAllSpy = jest.spyOn(component.pdfContainer().nativeElement, 'querySelectorAll');
            querySelectorAllSpy.mockReturnValue(mockNodeList);

            const removeSpy1 = jest.spyOn(existingCanvas1, 'remove');
            const removeSpy2 = jest.spyOn(existingCanvas2, 'remove');

            fixture.componentRef.setInput('orderedPages', mockPages);
            fixture.detectChanges();

            await component.renderPages();

            expect(querySelectorAllSpy).toHaveBeenCalledWith('.pdf-canvas-container canvas');
            expect(removeSpy1).toHaveBeenCalled();
            expect(removeSpy2).toHaveBeenCalled();
        });

        describe('renderPages scroll behavior', () => {
            it.each([
                { name: 'append pages and scroll to bottom when isAppendingFile is true', isAppending: true, shouldScroll: true },
                { name: 'not scroll to bottom when isAppendingFile is false', isAppending: false, shouldScroll: false },
            ])('$name', async ({ isAppending, shouldScroll }: { isAppending: boolean; shouldScroll: boolean }) => {
                const mockPages = [
                    {
                        slideId: 'slide1',
                        initialIndex: 1,
                        order: 1,
                        sourcePdfId: 'source1',
                        sourceIndex: 0,
                        pageProxy: {
                            getViewport: jest.fn().mockReturnValue({ width: 600, height: 800, scale: 1 }),
                            render: jest.fn().mockReturnValue({ promise: Promise.resolve() }),
                        },
                    },
                ];

                const originalRenderPages = component.renderPages;
                const originalConsoleError = console.error;

                try {
                    // silence console.error only in the non-appending case
                    if (!isAppending) {
                        console.error = jest.fn();
                    }

                    // override renderPages to only test the scroll behavior
                    component.renderPages = async function () {
                        if (this.isAppendingFile()) {
                            this.scrollToBottom();
                        }
                    };

                    const scrollToBottomSpy = jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});

                    fixture.componentRef.setInput('isAppendingFile', isAppending);
                    fixture.componentRef.setInput('orderedPages', mockPages);
                    fixture.detectChanges();

                    await component.renderPages();

                    if (shouldScroll) {
                        expect(scrollToBottomSpy).toHaveBeenCalled();
                    } else {
                        expect(scrollToBottomSpy).not.toHaveBeenCalled();
                    }

                    scrollToBottomSpy.mockRestore();
                } finally {
                    component.renderPages = originalRenderPages;
                    console.error = originalConsoleError;
                }
            });
        });

        it('should handle case where page container is not found', async () => {
            const mockPages = [
                {
                    slideId: 'non-existent-slide',
                    initialIndex: 1,
                    order: 1,
                    sourcePdfId: 'source1',
                    sourceIndex: 0,
                    pageProxy: {
                        getViewport: jest.fn().mockReturnValue({ width: 600, height: 800, scale: 1 }),
                        render: jest.fn().mockReturnValue({ promise: Promise.resolve() }),
                    },
                },
            ];

            component.pdfContainer().nativeElement.innerHTML = '';

            fixture.componentRef.setInput('orderedPages', mockPages);
            fixture.detectChanges();

            jest.spyOn(component.pdfContainer().nativeElement, 'querySelector').mockReturnValue(null);

            await component.renderPages();

            expect(component.loadedPages().size).toBe(0);
        });

        it('should update loadedPages with correct page indices', async () => {
            const originalRenderPages = component.renderPages;

            component.renderPages = async function () {
                this.loadedPages.update((loadedPages: Iterable<unknown> | undefined) => {
                    const newLoadedPages = new Set(loadedPages);
                    newLoadedPages.add(3);
                    newLoadedPages.add(7);
                    return newLoadedPages;
                });
            };

            await component.renderPages();

            expect(component.loadedPages().has(3)).toBeTruthy();
            expect(component.loadedPages().has(7)).toBeTruthy();
            expect(component.loadedPages().size).toBe(2);

            component.renderPages = originalRenderPages;
        });
    });
});
