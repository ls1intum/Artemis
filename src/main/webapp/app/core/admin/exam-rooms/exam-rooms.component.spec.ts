import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { ExamRoomsComponent } from 'app/core/admin/exam-rooms/exam-rooms.component';
import { ExamRoomsService } from 'app/core/admin/exam-rooms/exam-rooms.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import {
    ExamRoomAdminOverviewDTO,
    ExamRoomDTO,
    ExamRoomDeletionSummaryDTO,
    ExamRoomLayoutStrategyDTO,
    ExamRoomUploadInformationDTO,
} from 'app/core/admin/exam-rooms/exam-rooms.model';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { DeleteDialogService } from 'app/shared/delete-dialog/service/delete-dialog.service';
import { MockDeleteDialogService } from 'test/helpers/mocks/service/mock-delete-dialog.service';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';

describe('ExamRoomsComponentTest', () => {
    let component: ExamRoomsComponent;
    let fixture: ComponentFixture<ExamRoomsComponent>;
    let service: ExamRoomsService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamRoomsComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
                { provide: DeleteDialogService, useClass: MockDeleteDialogService },
                ExamRoomsService,
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamRoomsComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(ExamRoomsService);

        // getAdminOverview gets implicitly called each time the page is opened
        jest.spyOn(service, 'getAdminOverview').mockReturnValue(
            of(
                convertBodyToHttpResponse({
                    numberOfStoredExamRooms: 0,
                    numberOfStoredExamSeats: 0,
                    numberOfStoredLayoutStrategies: 0,
                    newestUniqueExamRooms: [],
                } as ExamRoomAdminOverviewDTO),
            ),
        );
    });

    function convertBodyToHttpResponse<T>(body?: T): HttpResponse<T> {
        return new HttpResponse<T>({ status: 200, body: body });
    }

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should load exam room overview on page load', () => {
        // WHEN
        fixture.detectChanges();

        // THEN
        expect(service.getAdminOverview).toHaveBeenCalledOnce();
        expect(component.hasOverview()).toBeTrue();
        expect(component.numberOf()!.examRooms).toBe(0);
        expect(component.numberOf()!.examSeats).toBe(0);
        expect(component.numberOf()!.layoutStrategies).toBe(0);
        expect(component.numberOf()!.uniqueExamRooms).toBe(0);
        expect(component.numberOf()!.uniqueExamSeats).toBe(0);
        expect(component.numberOf()!.uniqueLayoutStrategies).toBe(0);

        expect(component.distinctLayoutStrategyNames()).toBe('');
        expect(component.hasExamRoomData()).toBeFalse();
    });

    it('should properly extract values from admin overview', () => {
        // GIVEN
        const uploadedRoom: ExamRoomDTO = mockServiceGetAdminOverviewSingleRoom();

        // WHEN
        fixture.detectChanges();

        // THEN
        expect(component.hasOverview()).toBeTrue();
        expect(service.getAdminOverview).toHaveBeenCalledOnce();

        expect(component.numberOf()!.examRooms).toBe(1);
        expect(component.numberOf()!.examSeats).toBe(50);
        expect(component.numberOf()!.layoutStrategies).toBe(1);
        expect(component.numberOf()!.uniqueExamRooms).toBe(1);
        expect(component.numberOf()!.uniqueExamSeats).toBe(50);
        expect(component.numberOf()!.uniqueLayoutStrategies).toBe(1);

        expect(component.distinctLayoutStrategyNames()).toBe('default');
        expect(component.hasExamRoomData()).toBeTrue();
        expect(component.examRoomData()).toHaveLength(1);
        expect(component.examRoomData()![0]).toEqual(Object.assign({}, uploadedRoom, { defaultCapacity: 30, maxCapacity: 30 }));
    });

    it('should show error message on loadExamRoomOverview fail', () => {
        // GIVEN
        jest.spyOn(service, 'getAdminOverview').mockReturnValue(throwError(() => new Error()));

        // WHEN
        fixture.detectChanges();

        // THEN
        expect(service.getAdminOverview).toHaveBeenCalledOnce();
        expect(component.hasOverview()).toBeFalse();
    });

    it('should reject non-zip files', () => {
        // GIVEN
        fixture.detectChanges(); // initial render
        const onFileSelectedSpy = jest.spyOn(component, 'onFileSelectedAcceptZip');
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');

        const nonZipFile = new File(['ignored content'], 'non.zip.txt', { type: 'text/plain' });

        // WHEN
        setInputFiles(fileSelectButton, [nonZipFile]);
        fixture.detectChanges();

        // THEN
        expect(onFileSelectedSpy).toHaveBeenCalledOnce();
        expect(component.hasSelectedFile()).toBeFalse();
        expect(uploadButton.disabled).toBeTrue();
    });

    function setInputFiles(input: HTMLInputElement, files: File[]) {
        Object.defineProperty(input, 'files', { value: files });
        input.dispatchEvent(new Event('change'));
    }

    it('should reject empty input', () => {
        // GIVEN
        fixture.detectChanges(); // initial render
        const onFileSelectedSpy = jest.spyOn(component, 'onFileSelectedAcceptZip');
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');

        // WHEN
        setInputFiles(fileSelectButton, []);
        fixture.detectChanges();

        // THEN
        expect(onFileSelectedSpy).toHaveBeenCalledOnce();
        expect(component.hasSelectedFile()).toBeFalse();
        expect(uploadButton.disabled).toBeTrue();
    });

    it('should reject too big of a file', () => {
        // GIVEN
        fixture.detectChanges(); // initial render
        const onFileSelectedSpy = jest.spyOn(component, 'onFileSelectedAcceptZip');
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');
        const sizeInBytes = MAX_FILE_SIZE + 100;
        const bytes = new Uint8Array(sizeInBytes);
        const zipFile = new File([bytes], 'my_file.zip', { type: 'application/zip' });

        // WHEN
        setInputFiles(fileSelectButton, [zipFile]);
        fixture.detectChanges();

        // THEN
        expect(onFileSelectedSpy).toHaveBeenCalledOnce();
        expect(component.hasSelectedFile()).toBeFalse();
        expect(uploadButton.disabled).toBeTrue();
    });

    it('should make upload button clickable on valid file', () => {
        // GIVEN
        fixture.detectChanges(); // initial render
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const fileSelectLabel = fixture.debugElement.nativeElement.querySelector('label[for="roomDataFileSelect"]');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');
        const zipFile = new File(['ignored content'], 'my_file.zip', { type: 'application/zip' });

        // WHEN
        setInputFiles(fileSelectButton, [zipFile]);
        fixture.detectChanges();

        // THEN
        expect(component.hasSelectedFile()).toBeTrue();
        expect(fileSelectLabel.textContent.trim()).toBe('my_file.zip');
        expect(uploadButton.disabled).toBeFalse();
    });

    it('should make upload service call and refresh overview on valid zip file upload', () => {
        // GIVEN
        fixture.detectChanges(); // initial render
        mockServiceUploadRoomDataZipFile();
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');
        const zipFile = new File(['ignored content'], 'my_file.zip', { type: 'application/zip' });

        // WHEN
        setInputFiles(fileSelectButton, [zipFile]);
        fixture.detectChanges(); // required or else the upload button is still disabled
        uploadButton.click();
        fixture.detectChanges();

        // THEN
        expect(service.uploadRoomDataZipFile).toHaveBeenCalledOnce();
        expect(service.uploadRoomDataZipFile).toHaveBeenCalledWith(zipFile);
        expect(component.hasSelectedFile()).toBeFalse();
        // once from the initial page load, and once from clicking the upload button
        expect(service.getAdminOverview).toHaveBeenCalledTimes(2);
    });

    it('should not show upload information on failure', () => {
        // GIVEN
        fixture.detectChanges(); // initial render
        jest.spyOn(service, 'uploadRoomDataZipFile').mockReturnValue(throwError(() => new Error()));
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');
        const zipFile = new File(['ignored content'], 'my_file.zip', { type: 'application/zip' });

        // WHEN
        setInputFiles(fileSelectButton, [zipFile]);
        fixture.detectChanges(); // required or else the upload button is still disabled
        uploadButton.click();
        fixture.detectChanges();

        // THEN
        expect(service.uploadRoomDataZipFile).toHaveBeenCalledOnce();
        expect(service.uploadRoomDataZipFile).toHaveBeenCalledWith(zipFile);
        expect(service.getAdminOverview).toHaveBeenCalledOnce();
        expect(component.hasUploadInformation()).toBeFalse();
    });

    it('should show upload summary on successful upload', () => {
        // GIVEN
        fixture.detectChanges(); // initial render
        const uploadData: ExamRoomUploadInformationDTO = mockServiceUploadRoomDataZipFile();
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');
        const zipFile = new File(['ignored content'], 'my_file.zip', { type: 'application/zip' });

        // WHEN
        setInputFiles(fileSelectButton, [zipFile]);
        fixture.detectChanges(); // required or else the upload button is still disabled
        uploadButton.click();
        fixture.detectChanges();

        // THEN
        expect(component.hasUploadInformation()).toBeTrue();
        expect(component.uploadInformation()!.uploadedFileName).toEqual(uploadData.uploadedFileName);
        expect(component.uploadInformation()!.numberOfUploadedRooms).toEqual(uploadData.numberOfUploadedRooms);
        expect(component.uploadInformation()!.numberOfUploadedSeats).toEqual(uploadData.numberOfUploadedSeats);
        expect(component.uploadInformation()!.uploadedRoomNames).toEqual(uploadData.uploadedRoomNames);
    });

    function mockServiceUploadRoomDataZipFile(): ExamRoomUploadInformationDTO {
        const uploadData: ExamRoomUploadInformationDTO = {
            uploadedFileName: 'my_file.zip',
            numberOfUploadedRooms: 1,
            numberOfUploadedSeats: 50,
            uploadedRoomNames: ['Audimax'],
        } as ExamRoomUploadInformationDTO;

        jest.spyOn(service, 'uploadRoomDataZipFile').mockReturnValue(of(convertBodyToHttpResponse(uploadData)));

        return uploadData;
    }

    /// Returns the exam room it uses
    function mockServiceGetAdminOverviewSingleRoom(): ExamRoomDTO {
        const examRoom: ExamRoomDTO = {
            roomNumber: '123.456.789',
            name: 'Audimax',
            building: 'MI',
            numberOfSeats: 50,
            layoutStrategies: [
                {
                    name: 'default',
                    type: 'certainType',
                    capacity: 30,
                } as ExamRoomLayoutStrategyDTO,
            ],
        } as ExamRoomDTO;

        jest.spyOn(service, 'getAdminOverview').mockReturnValue(
            of(
                convertBodyToHttpResponse({
                    numberOfStoredExamRooms: 1,
                    numberOfStoredExamSeats: 50,
                    numberOfStoredLayoutStrategies: 1,
                    newestUniqueExamRooms: [examRoom],
                } as ExamRoomAdminOverviewDTO),
            ),
        );

        return examRoom;
    }

    it('should call delete outdated and unused service on delete outdated and unused button click', () => {
        // GIVEN
        fixture.detectChanges(); // initial render
        jest.spyOn(service, 'deleteOutdatedAndUnusedExamRooms').mockReturnValue(
            of(
                convertBodyToHttpResponse({
                    numberOfDeletedExamRooms: 4,
                } as ExamRoomDeletionSummaryDTO),
            ),
        );
        const deleteOutdatedAndUnusedButton = fixture.debugElement.nativeElement.querySelector('#roomDataDeleteOutdatedAndUnused');

        // WHEN
        deleteOutdatedAndUnusedButton.click();
        fixture.detectChanges();

        // THEN
        expect(service.deleteOutdatedAndUnusedExamRooms).toHaveBeenCalledOnce();
        // once from the initial load, once from the button click
        expect(service.getAdminOverview).toHaveBeenCalledTimes(2);
    });

    it('should not reload overview if deletion fails on delete outdated and unused button click', () => {
        // GIVEN
        fixture.detectChanges(); // initial render
        jest.spyOn(service, 'deleteOutdatedAndUnusedExamRooms').mockReturnValue(throwError(() => new Error()));
        const deleteOutdatedAndUnusedButton = fixture.debugElement.nativeElement.querySelector('#roomDataDeleteOutdatedAndUnused');

        // WHEN
        deleteOutdatedAndUnusedButton.click();
        fixture.detectChanges();

        // THEN
        expect(service.deleteOutdatedAndUnusedExamRooms).toHaveBeenCalledOnce();
        // once from the initial load
        expect(service.getAdminOverview).toHaveBeenCalledOnce();
    });

    it('should show deletion summary on successful outdated and unused deletion', () => {
        // GIVEN
        fixture.detectChanges(); // initial render
        jest.spyOn(service, 'deleteOutdatedAndUnusedExamRooms').mockReturnValue(
            of(
                convertBodyToHttpResponse({
                    numberOfDeletedExamRooms: 4,
                } as ExamRoomDeletionSummaryDTO),
            ),
        );
        const deleteOutdatedAndUnusedButton = fixture.debugElement.nativeElement.querySelector('#roomDataDeleteOutdatedAndUnused');

        // WHEN
        deleteOutdatedAndUnusedButton.click();
        fixture.detectChanges();

        // THEN
        expect(component.hasDeletionInformation()).toBeTrue();
        expect(component.deletionInformation()).toBeDefined();
        expect(component.deletionInformation()!.numberOfDeletedExamRooms).toBe(4);
    });
});
