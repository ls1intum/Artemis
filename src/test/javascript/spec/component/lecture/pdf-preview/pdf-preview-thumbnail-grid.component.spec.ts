import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { HttpClientModule } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { PdfPreviewThumbnailGridComponent } from 'app/lecture/pdf-preview/pdf-preview-thumbnail-grid/pdf-preview-thumbnail-grid.component';
import { ElementRef, InputSignal, Signal, SimpleChanges } from '@angular/core';
import dayjs from 'dayjs/esm';

interface HiddenPageMap {
    [pageIndex: number]: {
        date: dayjs.Dayjs;
        exerciseId: number | null;
    };
}

interface HiddenPage {
    pageIndex: number;
    date: dayjs.Dayjs;
    exerciseId: number | null;
}

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

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should update newHiddenPages when hiddenPages changes', () => {
        const updatedHiddenPages: HiddenPageMap = {
            4: { date: dayjs(), exerciseId: null },
            5: { date: dayjs(), exerciseId: null },
            6: { date: dayjs(), exerciseId: null },
        };

        fixture.componentRef.setInput('hiddenPages', updatedHiddenPages);

        fixture.detectChanges();

        expect(component.newHiddenPages()).toEqual(updatedHiddenPages);
    });

    it('should load the PDF when currentPdfUrl changes', async () => {
        const mockLoadPdf = jest.spyOn(component, 'loadPdf').mockResolvedValue();
        const initialPdfUrl = 'initial.pdf';
        const updatedPdfUrl = 'updated.pdf';

        let currentPdfUrlValue = initialPdfUrl;
        component.currentPdfUrl = jest.fn(() => currentPdfUrlValue) as unknown as InputSignal<string>;
        component.appendFile = jest.fn(() => false) as unknown as InputSignal<boolean>;

        currentPdfUrlValue = updatedPdfUrl;
        const changes: SimpleChanges = {
            currentPdfUrl: {
                currentValue: updatedPdfUrl,
                previousValue: initialPdfUrl,
                firstChange: false,
                isFirstChange: () => false,
            },
        };
        await component.ngOnChanges(changes);

        expect(mockLoadPdf).toHaveBeenCalledWith(updatedPdfUrl, false);
    });

    it('should load PDF and render pages', async () => {
        const spyCreateCanvas = jest.spyOn(component as any, 'createCanvas');

        await component.loadPdf('fake-url', false);

        expect(spyCreateCanvas).toHaveBeenCalled();
        expect(component.totalPagesArray().size).toBe(1);
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

        const initialHiddenPages = { 1: dayjs() };
        fixture.componentRef.setInput('hiddenPages', initialHiddenPages);

        component.toggleVisibility(1, mockEvent);
        expect(component.activeButtonIndex()).toBe(1);
        expect(mockButton.style.opacity).toBe('1');

        component.showPage(1);
        expect(component.newHiddenPages()[1]).toBeUndefined();
    });

    it('should select and deselect pages', () => {
        component.togglePageSelection(1, { target: { checked: true } } as unknown as Event);
        expect(component.selectedPages().has(1)).toBeTruthy();

        component.togglePageSelection(1, { target: { checked: false } } as unknown as Event);
        expect(component.selectedPages().has(1)).toBeFalsy();
    });

    it('should handle enlarged view correctly for a specific page', () => {
        const mockCanvas = document.createElement('canvas');
        const container = document.createElement('div');
        container.id = 'pdf-page-1';
        container.appendChild(mockCanvas);

        component.pdfContainer = jest.fn(() => ({
            nativeElement: {
                querySelector: jest.fn((selector: string) => {
                    if (selector === '#pdf-page-1 canvas') {
                        return mockCanvas;
                    }
                    return null;
                }),
            },
        })) as unknown as Signal<ElementRef<HTMLDivElement>>;

        component.displayEnlargedCanvas(1);

        expect(component.originalCanvas()).toBe(mockCanvas);
        expect(component.isEnlargedView()).toBeTruthy();
    });

    it('should update newHiddenPages and emit the change', () => {
        const emitSpy = jest.spyOn(component.newHiddenPagesOutput, 'emit');

        const hiddenPage: HiddenPage = {
            pageIndex: 1,
            date: dayjs('2024-01-01'),
            exerciseId: null,
        };

        component.onHiddenPageChange(hiddenPage);

        expect(component.newHiddenPages()[hiddenPage.pageIndex]).toBeDefined();
        expect(component.newHiddenPages()[hiddenPage.pageIndex].date.isSame(dayjs(hiddenPage.date))).toBeTruthy();
        expect(emitSpy).toHaveBeenCalledWith(component.newHiddenPages());
    });

    it('should remove the page from newHiddenPages and hide the action button', () => {
        const initialPages = { 1: dayjs() };
        fixture.componentRef.setInput('hiddenPages', initialPages);
        fixture.detectChanges();

        const hideButtonSpy = jest.spyOn(component, 'hideActionButton');

        component.showPage(1);

        expect(component.newHiddenPages()[1]).toBeUndefined();
        expect(hideButtonSpy).toHaveBeenCalledWith(1);
    });

    it('should handle showing a page that is not hidden', () => {
        fixture.componentRef.setInput('hiddenPages', {});
        fixture.detectChanges();

        const hideButtonSpy = jest.spyOn(component, 'hideActionButton');

        component.showPage(1);

        expect(hideButtonSpy).toHaveBeenCalledWith(1);
    });

    it('should set opacity to 0 for existing button', () => {
        const mockButton = document.createElement('button');
        mockButton.id = 'hide-show-button-1';
        document.body.appendChild(mockButton);

        component.hideActionButton(1);

        expect(mockButton.style.opacity).toBe('0');

        document.body.removeChild(mockButton);
    });

    it('should handle non-existent button gracefully', () => {
        component.hideActionButton(999);

        expect(document.getElementById('hide-show-button-999')).toBeNull();
    });

    describe('toggleVisibility method', () => {
        it('should set activeButtonIndex and make button visible', () => {
            const pageIndex = 3;
            const mockButton = document.createElement('button');
            const mockEvent = {
                stopPropagation: jest.fn(),
                target: mockButton,
            } as unknown as Event;

            component.toggleVisibility(pageIndex, mockEvent);

            expect(component.activeButtonIndex()).toBe(pageIndex);
            expect(mockButton.style.opacity).toBe('1');
            expect(mockEvent.stopPropagation).toHaveBeenCalled();
        });

        it('should find closest button element if target is not a button', () => {
            const pageIndex = 3;
            const mockButton = document.createElement('button');
            const mockSpan = document.createElement('span');
            mockButton.appendChild(mockSpan);

            // Mock the closest method
            Object.defineProperty(mockSpan, 'closest', {
                value: jest.fn().mockReturnValue(mockButton),
            });

            const mockEvent = {
                stopPropagation: jest.fn(),
                target: mockSpan,
            } as unknown as Event;

            component.toggleVisibility(pageIndex, mockEvent);

            expect(component.activeButtonIndex()).toBe(pageIndex);
            expect(mockButton.style.opacity).toBe('1');
            expect(mockEvent.stopPropagation).toHaveBeenCalled();
        });

        it('should handle case when no button is found', () => {
            const pageIndex = 3;
            const mockDiv = document.createElement('div');

            // Mock the closest method to return null
            Object.defineProperty(mockDiv, 'closest', {
                value: jest.fn().mockReturnValue(null),
            });

            const mockEvent = {
                stopPropagation: jest.fn(),
                target: mockDiv,
            } as unknown as Event;

            component.toggleVisibility(pageIndex, mockEvent);

            expect(component.activeButtonIndex()).toBe(pageIndex);
            expect(mockEvent.stopPropagation).toHaveBeenCalled();
        });
    });

    describe('togglePageSelection method', () => {
        it('should add page to selectedPages when checkbox is checked', () => {
            const pageIndex = 4;
            const mockEvent = {
                target: { checked: true },
            } as unknown as Event;
            const initialSelectedPages = new Set<number>([1, 2]);
            component.selectedPages.set(new Set(initialSelectedPages));
            jest.spyOn(component.selectedPagesOutput, 'emit');

            component.togglePageSelection(pageIndex, mockEvent);

            expect(component.selectedPages().has(pageIndex)).toBeTruthy();
            expect(component.selectedPages().size).toBe(3);
            expect(component.selectedPagesOutput.emit).toHaveBeenCalledWith(component.selectedPages());
        });

        it('should remove page from selectedPages when checkbox is unchecked', () => {
            const pageIndex = 2;
            const mockEvent = {
                target: { checked: false },
            } as unknown as Event;
            const initialSelectedPages = new Set<number>([1, 2, 3]);
            component.selectedPages.set(new Set(initialSelectedPages));
            jest.spyOn(component.selectedPagesOutput, 'emit');

            component.togglePageSelection(pageIndex, mockEvent);

            expect(component.selectedPages().has(pageIndex)).toBeFalsy();
            expect(component.selectedPages().size).toBe(2);
            expect(component.selectedPagesOutput.emit).toHaveBeenCalledWith(component.selectedPages());
        });

        it('should emit selectedPages after making changes', () => {
            const pageIndex = 5;
            const mockEvent = {
                target: { checked: true },
            } as unknown as Event;
            component.selectedPages.set(new Set<number>());
            jest.spyOn(component.selectedPagesOutput, 'emit');

            component.togglePageSelection(pageIndex, mockEvent);

            expect(component.selectedPagesOutput.emit).toHaveBeenCalledWith(component.selectedPages());
            expect(component.selectedPagesOutput.emit).toHaveBeenCalledWith(new Set([5]));
        });
    });

    describe('displayEnlargedCanvas method', () => {
        it('should set the original canvas for the specified page', () => {
            const pageIndex = 3;
            const mockCanvas = document.createElement('canvas');
            component.pdfContainer = jest.fn(() => ({
                nativeElement: {
                    querySelector: jest.fn().mockReturnValue(mockCanvas),
                },
            })) as unknown as Signal<ElementRef<HTMLDivElement>>;

            component.displayEnlargedCanvas(pageIndex);

            expect(component.originalCanvas()).toBe(mockCanvas);
        });

        it('should set isEnlargedView to true', () => {
            const pageIndex = 2;
            const mockCanvas = document.createElement('canvas');
            component.pdfContainer = jest.fn(() => ({
                nativeElement: {
                    querySelector: jest.fn().mockReturnValue(mockCanvas),
                },
            })) as unknown as Signal<ElementRef<HTMLDivElement>>;
            component.isEnlargedView.set(false);

            component.displayEnlargedCanvas(pageIndex);

            expect(component.isEnlargedView()).toBeTruthy();
        });

        it('should set initialPageNumber to the provided pageIndex', () => {
            const pageIndex = 7;
            const mockCanvas = document.createElement('canvas');
            component.pdfContainer = jest.fn(() => ({
                nativeElement: {
                    querySelector: jest.fn().mockReturnValue(mockCanvas),
                },
            })) as unknown as Signal<ElementRef<HTMLDivElement>>;

            component.displayEnlargedCanvas(pageIndex);

            expect(component.initialPageNumber()).toBe(pageIndex);
        });

        it('should query the correct selector for the page canvas', () => {
            const pageIndex = 5;
            const mockCanvas = document.createElement('canvas');
            const querySelectorSpy = jest.fn().mockReturnValue(mockCanvas);
            component.pdfContainer = jest.fn(() => ({
                nativeElement: {
                    querySelector: querySelectorSpy,
                },
            })) as unknown as Signal<ElementRef<HTMLDivElement>>;

            component.displayEnlargedCanvas(pageIndex);

            expect(querySelectorSpy).toHaveBeenCalledWith(`#pdf-page-${pageIndex} canvas`);
        });
    });

    describe('updateCheckboxStates method', () => {
        it('should update checkbox states to match the current selection model', () => {
            const mockCheckboxes = [
                { id: 'checkbox-1', checked: false },
                { id: 'checkbox-2', checked: false },
                { id: 'checkbox-3', checked: false },
            ] as unknown as NodeListOf<HTMLInputElement>;

            component.pdfContainer = jest.fn(() => ({
                nativeElement: {
                    querySelectorAll: jest.fn().mockReturnValue(mockCheckboxes),
                },
            })) as unknown as Signal<ElementRef<HTMLDivElement>>;

            component.selectedPages.set(new Set([1, 3]));

            const updateCheckboxStatesMethod = component['updateCheckboxStates'].bind(component);
            updateCheckboxStatesMethod();

            expect(mockCheckboxes[0].checked).toBeTruthy(); // checkbox-1 should be checked
            expect(mockCheckboxes[1].checked).toBeFalsy(); // checkbox-2 should remain unchecked
            expect(mockCheckboxes[2].checked).toBeTruthy(); // checkbox-3 should be checked
        });

        it('should do nothing if no checkboxes are found', () => {
            component.pdfContainer = jest.fn(() => ({
                nativeElement: {
                    querySelectorAll: jest.fn().mockReturnValue(null),
                },
            })) as unknown as Signal<ElementRef<HTMLDivElement>>;

            const updateCheckboxStatesMethod = component['updateCheckboxStates'].bind(component);

            expect(() => updateCheckboxStatesMethod()).not.toThrow();
        });

        it('should handle empty selection set correctly', () => {
            const mockCheckboxes = [
                { id: 'checkbox-1', checked: true },
                { id: 'checkbox-2', checked: true },
                { id: 'checkbox-3', checked: true },
            ] as unknown as NodeListOf<HTMLInputElement>;

            component.pdfContainer = jest.fn(() => ({
                nativeElement: {
                    querySelectorAll: jest.fn().mockReturnValue(mockCheckboxes),
                },
            })) as unknown as Signal<ElementRef<HTMLDivElement>>;

            component.selectedPages.set(new Set());

            const updateCheckboxStatesMethod = component['updateCheckboxStates'].bind(component);
            updateCheckboxStatesMethod();

            expect(mockCheckboxes[0].checked).toBeFalsy();
            expect(mockCheckboxes[1].checked).toBeFalsy();
            expect(mockCheckboxes[2].checked).toBeFalsy();
        });

        it('should correctly parse page numbers from checkbox IDs', () => {
            const mockCheckboxes = [
                { id: 'checkbox-10', checked: false },
                { id: 'checkbox-20', checked: false },
                { id: 'checkbox-30', checked: true },
            ] as unknown as NodeListOf<HTMLInputElement>;

            component.pdfContainer = jest.fn(() => ({
                nativeElement: {
                    querySelectorAll: jest.fn().mockReturnValue(mockCheckboxes),
                },
            })) as unknown as Signal<ElementRef<HTMLDivElement>>;

            component.selectedPages.set(new Set([10, 20])); // Select pages 10 and 20

            const updateCheckboxStatesMethod = component['updateCheckboxStates'].bind(component);
            updateCheckboxStatesMethod();

            expect(mockCheckboxes[0].checked).toBeTruthy(); // checkbox-10 should be checked
            expect(mockCheckboxes[1].checked).toBeTruthy(); // checkbox-20 should be checked
            expect(mockCheckboxes[2].checked).toBeFalsy(); // checkbox-30 should be unchecked
        });
    });

    describe('scrollToBottom method', () => {
        it('should scroll the container to the bottom with smooth behavior', () => {
            const mockScrollHeight = 1000;
            const mockScrollToSpy = jest.fn();

            component.pdfContainer = jest.fn(() => ({
                nativeElement: {
                    scrollHeight: mockScrollHeight,
                    scrollTo: mockScrollToSpy,
                },
            })) as unknown as Signal<ElementRef<HTMLDivElement>>;

            component.scrollToBottom();

            expect(mockScrollToSpy).toHaveBeenCalledWith({
                top: mockScrollHeight,
                left: 0,
                behavior: 'smooth',
            });
        });

        it('should use the current scrollHeight of the container', () => {
            const dynamicHeight = 2500;
            const mockScrollToSpy = jest.fn();

            component.pdfContainer = jest.fn(() => ({
                nativeElement: {
                    get scrollHeight() {
                        return dynamicHeight;
                    },
                    scrollTo: mockScrollToSpy,
                },
            })) as unknown as Signal<ElementRef<HTMLDivElement>>;

            component.scrollToBottom();

            expect(mockScrollToSpy).toHaveBeenCalledWith(expect.objectContaining({ top: dynamicHeight }));
        });

        it('should call scrollTo with the correct scroll options type', () => {
            const mockNativeElement = {
                scrollHeight: 1500,
                scrollTo: jest.fn(),
            };

            component.pdfContainer = jest.fn(() => ({
                nativeElement: mockNativeElement,
            })) as unknown as Signal<ElementRef<HTMLDivElement>>;

            component.scrollToBottom();

            expect(mockNativeElement.scrollTo).toHaveBeenCalledWith({
                top: 1500,
                left: 0,
                behavior: 'smooth',
            });

            const scrollOptions = mockNativeElement.scrollTo.mock.calls[0][0];
            expect(scrollOptions).toHaveProperty('top');
            expect(scrollOptions).toHaveProperty('left');
            expect(scrollOptions).toHaveProperty('behavior');
            expect(typeof scrollOptions.top).toBe('number');
            expect(typeof scrollOptions.left).toBe('number');
            expect(typeof scrollOptions.behavior).toBe('string');
        });

        it('should work with different container heights', () => {
            const testHeights = [0, 100, 5000, 10000];

            for (const height of testHeights) {
                const mockScrollToSpy = jest.fn();

                component.pdfContainer = jest.fn(() => ({
                    nativeElement: {
                        scrollHeight: height,
                        scrollTo: mockScrollToSpy,
                    },
                })) as unknown as Signal<ElementRef<HTMLDivElement>>;

                component.scrollToBottom();

                expect(mockScrollToSpy).toHaveBeenCalledWith({
                    top: height,
                    left: 0,
                    behavior: 'smooth',
                });
            }
        });
    });
});
