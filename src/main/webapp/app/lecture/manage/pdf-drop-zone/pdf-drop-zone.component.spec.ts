import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PdfDropZoneComponent } from './pdf-drop-zone.component';
import { MockDirective, MockPipe, MockProvider, ngMocks } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AlertService } from 'app/foundation/service/alert.service';

describe('PdfDropZoneComponent', () => {
    let component: PdfDropZoneComponent;
    let fixture: ComponentFixture<PdfDropZoneComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PdfDropZoneComponent],
            providers: [MockProvider(AlertService)],
        })
            .overrideComponent(PdfDropZoneComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe] },
                add: { imports: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe, (key: string) => key)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(PdfDropZoneComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('drag and drop', () => {
        it('should set isDragOver to true on dragover', () => {
            const event = {
                preventDefault: vi.fn(),
                stopPropagation: vi.fn(),
            } as unknown as DragEvent;

            component.onDragOver(event);

            expect(component.isDragOver()).toBe(true);
            expect(event.preventDefault).toHaveBeenCalled();
            expect(event.stopPropagation).toHaveBeenCalled();
        });

        it('should set isDragOver to false on dragleave', () => {
            component.isDragOver.set(true);
            const event = {
                preventDefault: vi.fn(),
                stopPropagation: vi.fn(),
            } as unknown as DragEvent;

            component.onDragLeave(event);

            expect(component.isDragOver()).toBe(false);
            expect(event.preventDefault).toHaveBeenCalled();
            expect(event.stopPropagation).toHaveBeenCalled();
        });

        it('should emit PDF files on drop', () => {
            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            const mockFileList = {
                length: 1,
                0: pdfFile,
                item: (index: number) => (index === 0 ? pdfFile : null),
            } as unknown as FileList;

            const event = {
                preventDefault: vi.fn(),
                stopPropagation: vi.fn(),
                dataTransfer: { files: mockFileList },
            } as unknown as DragEvent;

            const emitSpy = vi.spyOn(component.filesDropped, 'emit');

            component.onDrop(event);

            expect(component.isDragOver()).toBe(false);
            expect(emitSpy).toHaveBeenCalledWith([pdfFile]);
        });

        it('should filter out non-PDF files on drop', () => {
            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            const txtFile = new File(['content'], 'test.txt', { type: 'text/plain' });
            const mockFileList = {
                length: 2,
                0: pdfFile,
                1: txtFile,
                item: (index: number) => {
                    if (index === 0) return pdfFile;
                    if (index === 1) return txtFile;
                    return null;
                },
            } as unknown as FileList;

            const event = {
                preventDefault: vi.fn(),
                stopPropagation: vi.fn(),
                dataTransfer: { files: mockFileList },
            } as unknown as DragEvent;

            const emitSpy = vi.spyOn(component.filesDropped, 'emit');

            component.onDrop(event);

            expect(emitSpy).toHaveBeenCalledWith([pdfFile]);
        });

        it('should not emit if no PDF files are dropped', () => {
            const txtFile = new File(['content'], 'test.txt', { type: 'text/plain' });
            const mockFileList = {
                length: 1,
                0: txtFile,
                item: (index: number) => (index === 0 ? txtFile : null),
            } as unknown as FileList;

            const event = {
                preventDefault: vi.fn(),
                stopPropagation: vi.fn(),
                dataTransfer: { files: mockFileList },
            } as unknown as DragEvent;

            const emitSpy = vi.spyOn(component.filesDropped, 'emit');

            component.onDrop(event);

            expect(emitSpy).not.toHaveBeenCalled();
        });

        it('should accept files with .pdf extension regardless of mime type', () => {
            const pdfFile = new File(['content'], 'document.PDF', { type: '' });
            const mockFileList = {
                length: 1,
                0: pdfFile,
                item: (index: number) => (index === 0 ? pdfFile : null),
            } as unknown as FileList;

            const event = {
                preventDefault: vi.fn(),
                stopPropagation: vi.fn(),
                dataTransfer: { files: mockFileList },
            } as unknown as DragEvent;

            const emitSpy = vi.spyOn(component.filesDropped, 'emit');

            component.onDrop(event);

            expect(emitSpy).toHaveBeenCalledWith([pdfFile]);
        });

        it('should handle drop with no dataTransfer', () => {
            const event = {
                preventDefault: vi.fn(),
                stopPropagation: vi.fn(),
                dataTransfer: null,
            } as unknown as DragEvent;

            const emitSpy = vi.spyOn(component.filesDropped, 'emit');

            component.onDrop(event);

            expect(emitSpy).not.toHaveBeenCalled();
        });
    });

    describe('file input', () => {
        it('should emit PDF files from file input', () => {
            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            const mockFileList = {
                length: 1,
                0: pdfFile,
                item: (index: number) => (index === 0 ? pdfFile : null),
            } as unknown as FileList;

            const input = {
                files: mockFileList,
                value: 'C:\\fakepath\\test.pdf',
            };

            const event = { target: input } as unknown as Event;
            const emitSpy = vi.spyOn(component.filesDropped, 'emit');

            component.onFileInputChange(event);

            expect(emitSpy).toHaveBeenCalledWith([pdfFile]);
            expect(input.value).toBe('');
        });

        it('should trigger file input click on onClick', () => {
            fixture.detectChanges();
            const fileInput = component.fileInput().nativeElement;
            const clickSpy = vi.spyOn(fileInput, 'click');

            component.onClick();

            expect(clickSpy).toHaveBeenCalled();
        });

        it('should handle file input with no files', () => {
            const input = {
                files: null,
                value: '',
            };

            const event = { target: input } as unknown as Event;
            const emitSpy = vi.spyOn(component.filesDropped, 'emit');

            component.onFileInputChange(event);

            expect(emitSpy).not.toHaveBeenCalled();
        });
    });

    describe('multiple files', () => {
        it('should handle multiple PDF files', () => {
            const pdfFile1 = new File(['content1'], 'test1.pdf', { type: 'application/pdf' });
            const pdfFile2 = new File(['content2'], 'test2.pdf', { type: 'application/pdf' });
            const pdfFile3 = new File(['content3'], 'test3.pdf', { type: 'application/pdf' });
            const mockFileList = {
                length: 3,
                0: pdfFile1,
                1: pdfFile2,
                2: pdfFile3,
                item: (index: number) => {
                    if (index === 0) return pdfFile1;
                    if (index === 1) return pdfFile2;
                    if (index === 2) return pdfFile3;
                    return null;
                },
            } as unknown as FileList;

            const event = {
                preventDefault: vi.fn(),
                stopPropagation: vi.fn(),
                dataTransfer: { files: mockFileList },
            } as unknown as DragEvent;

            const emitSpy = vi.spyOn(component.filesDropped, 'emit');

            component.onDrop(event);

            expect(emitSpy).toHaveBeenCalledWith([pdfFile1, pdfFile2, pdfFile3]);
        });
    });

    it('should let the file browser select several files by default', () => {
        expect(component.fileInput().nativeElement.multiple).toBe(true);
    });

    describe('single file mode', () => {
        beforeEach(() => {
            fixture.componentRef.setInput('multiple', false);
            fixture.detectChanges();
        });

        it('should let the file browser select only one file', () => {
            expect(component.fileInput().nativeElement.multiple).toBe(false);
        });

        it('should emit only the first PDF when several files are dropped', () => {
            const textFile = new File(['text'], 'notes.txt', { type: 'text/plain' });
            const pdfFile1 = new File(['content1'], 'test1.pdf', { type: 'application/pdf' });
            const pdfFile2 = new File(['content2'], 'test2.pdf', { type: 'application/pdf' });
            const files = [textFile, pdfFile1, pdfFile2];
            const mockFileList = {
                length: 3,
                0: textFile,
                1: pdfFile1,
                2: pdfFile2,
                item: (index: number) => files[index] ?? null,
            } as unknown as FileList;
            const event = {
                preventDefault: vi.fn(),
                stopPropagation: vi.fn(),
                dataTransfer: { files: mockFileList },
            } as unknown as DragEvent;
            const emitSpy = vi.spyOn(component.filesDropped, 'emit');

            component.onDrop(event);

            expect(emitSpy).toHaveBeenCalledExactlyOnceWith([pdfFile1]);
        });
    });

    describe('texts', () => {
        it('should use the default upload texts', () => {
            const translatedKeys = ngMocks.findAll(fixture.debugElement, TranslateDirective).map((element) => ngMocks.input(element, 'jhiTranslate'));

            expect(translatedKeys).toEqual(['artemisApp.lecture.pdfUpload.dropZoneTitle', 'artemisApp.lecture.pdfUpload.dropZoneHint']);
        });

        it('should use the given title and hint keys', () => {
            fixture.componentRef.setInput('titleKey', 'custom.title');
            fixture.componentRef.setInput('hintKey', 'custom.hint');
            fixture.detectChanges();

            const translatedKeys = ngMocks.findAll(fixture.debugElement, TranslateDirective).map((element) => ngMocks.input(element, 'jhiTranslate'));

            expect(translatedKeys).toEqual(['custom.title', 'custom.hint']);
            expect(fixture.nativeElement.querySelector('[role="button"]').getAttribute('aria-label')).toBe('custom.title');
        });

        it('should describe the drop zone with its hint', () => {
            const dropZone: HTMLElement = fixture.nativeElement.querySelector('[role="button"]');
            const hint: HTMLElement = fixture.nativeElement.querySelector(`#${dropZone.getAttribute('aria-describedby')}`);

            expect(hint).not.toBeNull();
            expect(ngMocks.input(ngMocks.find(fixture.debugElement, `#${hint.id}`), 'jhiTranslate')).toBe('artemisApp.lecture.pdfUpload.dropZoneHint');
        });
    });
});
