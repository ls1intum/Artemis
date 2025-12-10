import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PdfDropZoneComponent } from './pdf-drop-zone.component';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('PdfDropZoneComponent', () => {
    let component: PdfDropZoneComponent;
    let fixture: ComponentFixture<PdfDropZoneComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PdfDropZoneComponent],
        })
            .overrideComponent(PdfDropZoneComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe] },
                add: { imports: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)] },
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
                preventDefault: jest.fn(),
                stopPropagation: jest.fn(),
            } as unknown as DragEvent;

            component.onDragOver(event);

            expect(component.isDragOver()).toBeTrue();
            expect(event.preventDefault).toHaveBeenCalled();
            expect(event.stopPropagation).toHaveBeenCalled();
        });

        it('should set isDragOver to false on dragleave', () => {
            component.isDragOver.set(true);
            const event = {
                preventDefault: jest.fn(),
                stopPropagation: jest.fn(),
            } as unknown as DragEvent;

            component.onDragLeave(event);

            expect(component.isDragOver()).toBeFalse();
            expect(event.preventDefault).toHaveBeenCalled();
            expect(event.stopPropagation).toHaveBeenCalled();
        });

        it('should emit PDF files on drop', () => {
            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            const mockFileList = {
                length: 1,
                0: pdfFile,
                item: (index: number) => (index === 0 ? pdfFile : null),
            } as FileList;

            const event = {
                preventDefault: jest.fn(),
                stopPropagation: jest.fn(),
                dataTransfer: { files: mockFileList },
            } as unknown as DragEvent;

            const emitSpy = jest.spyOn(component.filesDropped, 'emit');

            component.onDrop(event);

            expect(component.isDragOver()).toBeFalse();
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
            } as FileList;

            const event = {
                preventDefault: jest.fn(),
                stopPropagation: jest.fn(),
                dataTransfer: { files: mockFileList },
            } as unknown as DragEvent;

            const emitSpy = jest.spyOn(component.filesDropped, 'emit');

            component.onDrop(event);

            expect(emitSpy).toHaveBeenCalledWith([pdfFile]);
        });

        it('should not emit if no PDF files are dropped', () => {
            const txtFile = new File(['content'], 'test.txt', { type: 'text/plain' });
            const mockFileList = {
                length: 1,
                0: txtFile,
                item: (index: number) => (index === 0 ? txtFile : null),
            } as FileList;

            const event = {
                preventDefault: jest.fn(),
                stopPropagation: jest.fn(),
                dataTransfer: { files: mockFileList },
            } as unknown as DragEvent;

            const emitSpy = jest.spyOn(component.filesDropped, 'emit');

            component.onDrop(event);

            expect(emitSpy).not.toHaveBeenCalled();
        });

        it('should accept files with .pdf extension regardless of mime type', () => {
            const pdfFile = new File(['content'], 'document.PDF', { type: '' });
            const mockFileList = {
                length: 1,
                0: pdfFile,
                item: (index: number) => (index === 0 ? pdfFile : null),
            } as FileList;

            const event = {
                preventDefault: jest.fn(),
                stopPropagation: jest.fn(),
                dataTransfer: { files: mockFileList },
            } as unknown as DragEvent;

            const emitSpy = jest.spyOn(component.filesDropped, 'emit');

            component.onDrop(event);

            expect(emitSpy).toHaveBeenCalledWith([pdfFile]);
        });

        it('should handle drop with no dataTransfer', () => {
            const event = {
                preventDefault: jest.fn(),
                stopPropagation: jest.fn(),
                dataTransfer: null,
            } as unknown as DragEvent;

            const emitSpy = jest.spyOn(component.filesDropped, 'emit');

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
            } as FileList;

            const input = {
                files: mockFileList,
                value: 'C:\\fakepath\\test.pdf',
            };

            const event = { target: input } as unknown as Event;
            const emitSpy = jest.spyOn(component.filesDropped, 'emit');

            component.onFileInputChange(event);

            expect(emitSpy).toHaveBeenCalledWith([pdfFile]);
            expect(input.value).toBe('');
        });

        it('should trigger file input click on onClick', () => {
            fixture.detectChanges();
            const fileInput = component.fileInput().nativeElement;
            const clickSpy = jest.spyOn(fileInput, 'click');

            component.onClick();

            expect(clickSpy).toHaveBeenCalled();
        });

        it('should handle file input with no files', () => {
            const input = {
                files: null,
                value: '',
            };

            const event = { target: input } as unknown as Event;
            const emitSpy = jest.spyOn(component.filesDropped, 'emit');

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
            } as FileList;

            const event = {
                preventDefault: jest.fn(),
                stopPropagation: jest.fn(),
                dataTransfer: { files: mockFileList },
            } as unknown as DragEvent;

            const emitSpy = jest.spyOn(component.filesDropped, 'emit');

            component.onDrop(event);

            expect(emitSpy).toHaveBeenCalledWith([pdfFile1, pdfFile2, pdfFile3]);
        });
    });
});
