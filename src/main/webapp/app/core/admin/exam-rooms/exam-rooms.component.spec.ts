import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamRoomsComponent } from './exam-rooms.component';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';

describe('ExamRoomsComponent', () => {
    let component: ExamRoomsComponent;
    let fixture: ComponentFixture<ExamRoomsComponent>;
    let httpMock: HttpTestingController;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [ExamRoomsComponent],
            imports: [HttpClientTestingModule],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamRoomsComponent);
        component = fixture.componentInstance;
        httpMock = TestBed.inject(HttpTestingController);
        fixture.detectChanges();
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should show error for non-zip files', () => {
        const file = new File(['dummy'], 'test.txt', { type: 'text/plain' });
        const event = { target: { files: [file] } } as any;
        component.onFileSelected(event);
        expect(component.uploadError).toContain('Please select a .zip file');
        expect(component.selectedFile).toBeNull();
    });

    it('should accept valid zip files', () => {
        const file = new File(['dummy'], 'rooms.zip', { type: 'application/zip' });
        const event = { target: { files: [file] } } as any;
        component.onFileSelected(event);
        expect(component.uploadError).toBeNull();
        expect(component.selectedFile).toEqual(file);
    });

    it('should upload the zip file successfully', () => {
        const file = new File(['dummy content'], 'rooms.zip', { type: 'application/zip' });
        component.selectedFile = file;

        component.upload();
        const req = httpMock.expectOne('/api/admin/exam-rooms/upload');
        expect(req.request.method).toBe('POST');
        req.flush({ success: true });

        expect(component.uploadSuccess).toBeTrue();
        expect(component.uploading).toBeFalse();
    });

    afterEach(() => {
        httpMock.verify();
    });
});
