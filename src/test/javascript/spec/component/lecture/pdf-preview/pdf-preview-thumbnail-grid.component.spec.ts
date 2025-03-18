import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpClientModule } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { PdfPreviewThumbnailGridComponent } from 'app/lecture/manage/pdf-preview/pdf-preview-thumbnail-grid/pdf-preview-thumbnail-grid.component';
import { ElementRef, signal, Signal, SimpleChanges } from '@angular/core';
import dayjs from 'dayjs/esm';
import { OrderedPage, HiddenPageMap, HiddenPage } from 'app/lecture/pdf-preview/pdf-preview.component';

jest.mock('pdfjs-dist', () => {
    return {
        getDocument: jest.fn(() => ({
            promise: Promise.resolve({
                numPages: 1,
                getPage: jest.fn(() =>
                    Promise.resolve({
                        getViewport: jest.fn(() => ({ width: 600, height: 800, scale: 1 })),
                        render: jest.fn(() => ({
                            promise: Promise.resolve(),
                        })),
                    }),
                ),
            }),
        })),
    };
});

jest.mock('pdfjs-dist/build/pdf.worker', () => {
    return {};
});

describe('PdfPreviewThumbnailGridComponent', () => {
    let component: PdfPreviewThumbnailGridComponent;
    let fixture: ComponentFixture<PdfPreviewThumbnailGridComponent>;
    let alertServiceMock: any;

    const mockOrderedPages: OrderedPage[] = [
        { slideId: 'slide1', pageIndex: 1, pageProxy: undefined, order: 1 },
        { slideId: 'slide2', pageIndex: 2, pageProxy: undefined, order: 2 },
        { slideId: 'slide3', pageIndex: 3, pageProxy: undefined, order: 3 },
    ];

    beforeEach(async () => {
        alertServiceMock = {
            error: jest.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [PdfPreviewThumbnailGridComponent, HttpClientModule],
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

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should update hiddenPages when they change', () => {
        const updatedHiddenPages: HiddenPageMap = {
            slide4: { date: dayjs(), exerciseId: null },
            slide5: { date: dayjs(), exerciseId: null },
            slide6: { date: dayjs(), exerciseId: null },
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

        await component.ngOnChanges(changes);

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
            exerciseId: null,
        };

        let emittedValue = null;
        emitSpy.mockImplementation((value) => {
            emittedValue = value;
        });

        component.onHiddenPagesReceived(hiddenPage);

        expect(emittedValue).toBeDefined();
        expect(emittedValue![hiddenPage.slideId]).toBeDefined();
        expect(emittedValue![hiddenPage.slideId].date.isSame(dayjs(hiddenPage.date))).toBeTruthy();
        expect(emittedValue![hiddenPage.slideId].exerciseId).toBeNull();
        expect(emitSpy).toHaveBeenCalled();
    });

    it('should remove the page from hiddenPages and hide the action button', () => {
        const initialPages: HiddenPageMap = {
            slide1: { date: dayjs(), exerciseId: null },
        };

        fixture.componentRef.setInput('hiddenPages', initialPages);
        fixture.detectChanges();

        const hideButtonSpy = jest.spyOn(component, 'hideActionButton');
        const emitSpy = jest.spyOn(component.hiddenPagesOutput, 'emit');

        let emittedValue = null;
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
        document.body.appendChild(mockButton);

        component.hideActionButton('slide1');

        expect(mockButton.style.opacity).toBe('0');

        document.body.removeChild(mockButton);
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
                value: jest.fn().mockReturnValue(null),
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
                exerciseId: null,
            };

            component.onHiddenPagesReceived(hiddenPage);

            expect(emitSpy).toHaveBeenCalled();

            const emittedValue = emitSpy.mock.calls[0][0];

            expect(emittedValue).toBeDefined();
            expect(emittedValue[hiddenPage.slideId]).toBeDefined();
            expect(emittedValue[hiddenPage.slideId].date.isSame(dayjs(hiddenPage.date))).toBeTruthy();
            expect(emittedValue[hiddenPage.slideId].exerciseId).toBeNull();
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
                    exerciseId: null,
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

            expect(Object.keys(component.hiddenPages()).length).toBe(3);
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

            expect(Object.keys(emittedValue).length).toBe(1);
            expect(emittedValue['slide1']).toBeDefined();
            expect(emittedValue['slide1'].date.isSame(dayjs('2024-02-01'))).toBeTruthy();
            expect(emittedValue['slide1'].exerciseId).toBe(789);

            expect(emitSpy).toHaveBeenCalled();
        });
    });

    describe('drag and drop functionality', () => {
        beforeEach(() => {
            fixture.componentRef.setInput('orderedPages', mockOrderedPages);
            fixture.detectChanges();

            const mockElement = document.createElement('div');
            document.querySelectorAll = jest.fn().mockReturnValue([mockElement]);
        });

        it('should initiate drag operation', () => {
            const slideId = 'slide1';
            const mockDataTransfer = {
                setData: jest.fn(),
                effectAllowed: null,
            };
            const mockEvent = {
                dataTransfer: mockDataTransfer,
                preventDefault: jest.fn(),
            } as unknown as DragEvent;

            const mockDiv = document.createElement('div');
            document.getElementById = jest.fn().mockImplementation((id) => {
                if (id === `pdf-page-${slideId}`) {
                    return mockDiv;
                }
                return null;
            });

            component.onDragStart(mockEvent, slideId);

            expect(mockDataTransfer.setData).toHaveBeenCalledWith('text/plain', slideId);
            expect(mockDataTransfer.effectAllowed).toBe('move');
            expect(component.dragSlideId()).toBe(slideId);
            expect(component.isDragging()).toBeTruthy();
        });

        it('should allow dropping on drag over', () => {
            const mockEvent = {
                preventDefault: jest.fn(),
                dataTransfer: {
                    dropEffect: null,
                },
            } as unknown as DragEvent;

            component.onDragOver(mockEvent);

            expect(mockEvent.preventDefault).toHaveBeenCalled();
            expect(mockEvent.dataTransfer!.dropEffect).toBe('move');
        });

        it('should handle dropping to reorder pages', () => {
            const sourceSlideId = 'slide1';
            const targetSlideId = 'slide3';

            const mockEvent = {
                preventDefault: jest.fn(),
                dataTransfer: {
                    getData: jest.fn().mockReturnValue(sourceSlideId),
                },
            } as unknown as DragEvent;

            const spyReorderPages = jest.spyOn(component, 'reorderPages').mockImplementation();

            component.onDrop(mockEvent, targetSlideId);

            expect(mockEvent.preventDefault).toHaveBeenCalled();
            expect(spyReorderPages).toHaveBeenCalledWith(sourceSlideId, targetSlideId);
            expect(component.isDragging()).toBeFalsy();
            expect(component.dragSlideId()).toBeNull();
        });

        it('should not reorder if source and target are the same', () => {
            const slideId = 'slide2';

            const mockEvent = {
                preventDefault: jest.fn(),
                dataTransfer: {
                    getData: jest.fn().mockReturnValue(slideId),
                },
            } as unknown as DragEvent;

            const spyReorderPages = jest.spyOn(component, 'reorderPages');

            component.onDrop(mockEvent, slideId);

            expect(mockEvent.preventDefault).toHaveBeenCalled();
            expect(spyReorderPages).not.toHaveBeenCalled();
        });

        it('should reorder pages correctly', () => {
            const sourceSlideId = 'slide1';
            const targetSlideId = 'slide3';

            const emitSpy = jest.spyOn(component.pageOrderOutput, 'emit');

            component.reorderPages(sourceSlideId, targetSlideId);

            expect(emitSpy).toHaveBeenCalled();
            const newOrder = emitSpy.mock.calls[0][0];
            expect(newOrder.findIndex((p) => p.slideId === sourceSlideId)).toBe(2);
            expect(component.reordering()).toBeTruthy();
        });

        it('should not reorder if page is not found', () => {
            const emitSpy = jest.spyOn(component.pageOrderOutput, 'emit');

            component.reorderPages('nonexistent', 'slide2');

            expect(emitSpy).not.toHaveBeenCalled();
        });

        it('should get the correct page order', () => {
            expect(component.getPageOrder('slide2')).toBe(2);
            expect(component.getPageOrder('nonexistent')).toBe(-1);
        });

        it('should find page by slideId', () => {
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

    describe('drag event handlers', () => {
        it('should add drag-over class on drag enter', () => {
            const slideId = 'slide1';
            const mockEvent = {
                preventDefault: jest.fn(),
            } as unknown as DragEvent;

            const mockElement = document.createElement('div');
            document.getElementById = jest.fn().mockReturnValue(mockElement);

            component.dragSlideId.set('slide2');

            component.onDragEnter(mockEvent, slideId);

            expect(mockEvent.preventDefault).toHaveBeenCalled();
            expect(mockElement.classList.contains('drag-over')).toBeTruthy();
        });

        it('should not add drag-over class when dragging the same slide', () => {
            const slideId = 'slide1';
            const mockEvent = {
                preventDefault: jest.fn(),
            } as unknown as DragEvent;

            const mockElement = document.createElement('div');
            document.getElementById = jest.fn().mockReturnValue(mockElement);

            component.dragSlideId.set(slideId);

            component.onDragEnter(mockEvent, slideId);

            expect(mockEvent.preventDefault).toHaveBeenCalled();
            expect(mockElement.classList.contains('drag-over')).toBeFalsy();
        });

        it('should remove drag-over class on drag leave', () => {
            const slideId = 'slide1';
            const mockEvent = {
                preventDefault: jest.fn(),
            } as unknown as DragEvent;

            const mockElement = document.createElement('div');
            mockElement.classList.add('drag-over');
            document.getElementById = jest.fn().mockReturnValue(mockElement);

            component.onDragLeave(mockEvent, slideId);

            expect(mockEvent.preventDefault).toHaveBeenCalled();
            expect(mockElement.classList.contains('drag-over')).toBeFalsy();
        });

        it('should handle drag end correctly', () => {
            const mockElements = [document.createElement('div'), document.createElement('div')];
            mockElements[0].classList.add('dragging');
            mockElements[1].classList.add('drag-over');

            document.querySelectorAll = jest.fn().mockReturnValue(mockElements);

            component.isDragging.set(true);
            component.dragSlideId.set('slide1');

            component.onDragEnd();

            expect(component.isDragging()).toBeFalsy();
            expect(component.dragSlideId()).toBeNull();

            expect(mockElements[0].classList.contains('dragging')).toBeFalsy();
            expect(mockElements[1].classList.contains('drag-over')).toBeFalsy();
        });

        it('should handle null element gracefully on drag enter', () => {
            const slideId = 'nonexistent';
            const mockEvent = {
                preventDefault: jest.fn(),
            } as unknown as DragEvent;

            document.getElementById = jest.fn().mockReturnValue(null);

            component.onDragEnter(mockEvent, slideId);

            expect(mockEvent.preventDefault).toHaveBeenCalled();
        });

        it('should handle null element gracefully on drag leave', () => {
            const slideId = 'nonexistent';
            const mockEvent = {
                preventDefault: jest.fn(),
            } as unknown as DragEvent;

            document.getElementById = jest.fn().mockReturnValue(null);

            expect(() => {
                component.onDragLeave(mockEvent, slideId);
            }).toThrow();

            expect(mockEvent.preventDefault).toHaveBeenCalled();
        });
    });
});
