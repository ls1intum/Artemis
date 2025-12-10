import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PdfDropZoneComponent } from './pdf-drop-zone.component';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('PdfDropZoneComponent', () => {
    let component: PdfDropZoneComponent;
    let fixture: ComponentFixture<PdfDropZoneComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PdfDropZoneComponent, MockPipe(ArtemisTranslatePipe)],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfDropZoneComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('drag and drop', () => {
        it('should set isDragOver to true on dragover', () => {
            const event = new DragEvent('dragover');
            Object.defineProperty(event, 'preventDefault', { value: jest.fn() });
            Object.defineProperty(event, 'stopPropagation', { value: jest.fn() });

            component.onDragOver(event);

            expect(component.isDragOver()).toBeTrue();
        });

        it('should set isDragOver to false on dragleave', () => {
            component.isDragOver.set(true);
            const event = new DragEvent('dragleave');
            Object.defineProperty(event, 'preventDefault', { value: jest.fn() });
            Object.defineProperty(event, 'stopPropagation', { value: jest.fn() });

            component.onDragLeave(event);

            expect(component.isDragOver()).toBeFalse();
        });

        it('should emit PDF files on drop', () => {
            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            const dataTransfer = new DataTransfer();
            dataTransfer.items.add(pdfFile);

            const event = new DragEvent('drop', { dataTransfer });
            Object.defineProperty(event, 'preventDefault', { value: jest.fn() });
            Object.defineProperty(event, 'stopPropagation', { value: jest.fn() });

            const emitSpy = jest.spyOn(component.filesDropped, 'emit');

            component.onDrop(event);

            expect(component.isDragOver()).toBeFalse();
            expect(emitSpy).toHaveBeenCalledWith([pdfFile]);
        });

        it('should filter out non-PDF files on drop', () => {
            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            const txtFile = new File(['content'], 'test.txt', { type: 'text/plain' });
            const dataTransfer = new DataTransfer();
            dataTransfer.items.add(pdfFile);
            dataTransfer.items.add(txtFile);

            const event = new DragEvent('drop', { dataTransfer });
            Object.defineProperty(event, 'preventDefault', { value: jest.fn() });
            Object.defineProperty(event, 'stopPropagation', { value: jest.fn() });

            const emitSpy = jest.spyOn(component.filesDropped, 'emit');

            component.onDrop(event);

            expect(emitSpy).toHaveBeenCalledWith([pdfFile]);
        });

        it('should not emit if no PDF files are dropped', () => {
            const txtFile = new File(['content'], 'test.txt', { type: 'text/plain' });
            const dataTransfer = new DataTransfer();
            dataTransfer.items.add(txtFile);

            const event = new DragEvent('drop', { dataTransfer });
            Object.defineProperty(event, 'preventDefault', { value: jest.fn() });
            Object.defineProperty(event, 'stopPropagation', { value: jest.fn() });

            const emitSpy = jest.spyOn(component.filesDropped, 'emit');

            component.onDrop(event);

            expect(emitSpy).not.toHaveBeenCalled();
        });

        it('should accept files with .pdf extension regardless of mime type', () => {
            const pdfFile = new File(['content'], 'document.PDF', { type: '' });
            const dataTransfer = new DataTransfer();
            dataTransfer.items.add(pdfFile);

            const event = new DragEvent('drop', { dataTransfer });
            Object.defineProperty(event, 'preventDefault', { value: jest.fn() });
            Object.defineProperty(event, 'stopPropagation', { value: jest.fn() });

            const emitSpy = jest.spyOn(component.filesDropped, 'emit');

            component.onDrop(event);

            expect(emitSpy).toHaveBeenCalledWith([pdfFile]);
        });
    });

    describe('file input', () => {
        it('should emit PDF files from file input', () => {
            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            const input = document.createElement('input');
            input.type = 'file';

            const dataTransfer = new DataTransfer();
            dataTransfer.items.add(pdfFile);
            input.files = dataTransfer.files;

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
    });

    describe('multiple files', () => {
        it('should handle multiple PDF files', () => {
            const pdfFile1 = new File(['content1'], 'test1.pdf', { type: 'application/pdf' });
            const pdfFile2 = new File(['content2'], 'test2.pdf', { type: 'application/pdf' });
            const pdfFile3 = new File(['content3'], 'test3.pdf', { type: 'application/pdf' });
            const dataTransfer = new DataTransfer();
            dataTransfer.items.add(pdfFile1);
            dataTransfer.items.add(pdfFile2);
            dataTransfer.items.add(pdfFile3);

            const event = new DragEvent('drop', { dataTransfer });
            Object.defineProperty(event, 'preventDefault', { value: jest.fn() });
            Object.defineProperty(event, 'stopPropagation', { value: jest.fn() });

            const emitSpy = jest.spyOn(component.filesDropped, 'emit');

            component.onDrop(event);

            expect(emitSpy).toHaveBeenCalledWith([pdfFile1, pdfFile2, pdfFile3]);
        });
    });
});
