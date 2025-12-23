/**
 * Vitest tests for ExamRoomsComponent.
 * Tests the admin view for managing exam room data including file uploads,
 * room overview, and data deletion functionality.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of, throwError } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';

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

describe('ExamRoomsComponent', () => {
    setupTestBed({ zoneless: true });

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

        // getAdminOverview is called on page load, provide default mock
        vi.spyOn(service, 'getAdminOverview').mockReturnValue(
            of(
                createHttpResponse({
                    numberOfStoredExamRooms: 0,
                    numberOfStoredExamSeats: 0,
                    numberOfStoredLayoutStrategies: 0,
                    newestUniqueExamRooms: [],
                } as ExamRoomAdminOverviewDTO),
            ),
        );
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    /**
     * Helper to create HTTP response from body.
     */
    function createHttpResponse<T>(body?: T): HttpResponse<T> {
        return new HttpResponse<T>({ status: 200, body: body });
    }

    /**
     * Helper to simulate file selection on input element.
     */
    function setInputFiles(input: HTMLInputElement, files: File[]) {
        Object.defineProperty(input, 'files', { value: files });
        input.dispatchEvent(new Event('change'));
    }

    /**
     * Creates mock admin overview with a single room.
     */
    function mockServiceWithSingleRoom(): ExamRoomDTO {
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

        vi.spyOn(service, 'getAdminOverview').mockReturnValue(
            of(
                createHttpResponse({
                    numberOfStoredExamRooms: 1,
                    numberOfStoredExamSeats: 50,
                    numberOfStoredLayoutStrategies: 1,
                    newestUniqueExamRooms: [examRoom],
                } as ExamRoomAdminOverviewDTO),
            ),
        );

        return examRoom;
    }

    /**
     * Creates mock for successful file upload.
     */
    function mockServiceUploadSuccess(): ExamRoomUploadInformationDTO {
        const uploadData: ExamRoomUploadInformationDTO = {
            uploadedFileName: 'my_file.zip',
            numberOfUploadedRooms: 1,
            numberOfUploadedSeats: 50,
            uploadedRoomNames: ['Audimax'],
        } as ExamRoomUploadInformationDTO;

        vi.spyOn(service, 'uploadRoomDataZipFile').mockReturnValue(of(createHttpResponse(uploadData)));

        return uploadData;
    }

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should load exam room overview on page load', () => {
        fixture.detectChanges();

        expect(service.getAdminOverview).toHaveBeenCalledOnce();
        expect(component.hasOverview()).toBe(true);
        expect(component.numberOf()!.examRooms).toBe(0);
        expect(component.numberOf()!.examSeats).toBe(0);
        expect(component.numberOf()!.layoutStrategies).toBe(0);
        expect(component.numberOf()!.uniqueExamRooms).toBe(0);
        expect(component.numberOf()!.uniqueExamSeats).toBe(0);
        expect(component.numberOf()!.uniqueLayoutStrategies).toBe(0);

        expect(component.distinctLayoutStrategyNames()).toBe('');
        expect(component.hasExamRoomData()).toBe(false);
    });

    it('should properly extract values from admin overview', () => {
        const uploadedRoom = mockServiceWithSingleRoom();

        fixture.detectChanges();

        expect(component.hasOverview()).toBe(true);
        expect(service.getAdminOverview).toHaveBeenCalledOnce();

        expect(component.numberOf()!.examRooms).toBe(1);
        expect(component.numberOf()!.examSeats).toBe(50);
        expect(component.numberOf()!.layoutStrategies).toBe(1);
        expect(component.numberOf()!.uniqueExamRooms).toBe(1);
        expect(component.numberOf()!.uniqueExamSeats).toBe(50);
        expect(component.numberOf()!.uniqueLayoutStrategies).toBe(1);

        expect(component.distinctLayoutStrategyNames()).toBe('default');
        expect(component.hasExamRoomData()).toBe(true);
        expect(component.examRoomData()).toHaveLength(1);
        expect(component.examRoomData()![0]).toEqual({
            ...uploadedRoom,
            defaultCapacity: 30,
            maxCapacity: 30,
        });
    });

    it('should show error message on loadExamRoomOverview fail', () => {
        vi.spyOn(service, 'getAdminOverview').mockReturnValue(throwError(() => new Error()));

        fixture.detectChanges();

        expect(service.getAdminOverview).toHaveBeenCalledOnce();
        expect(component.hasOverview()).toBe(false);
    });

    it('should reject non-zip files', () => {
        fixture.detectChanges();
        const onFileSelectedSpy = vi.spyOn(component, 'onFileSelectedAcceptZip');
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');

        const nonZipFile = new File(['ignored content'], 'non.zip.txt', { type: 'text/plain' });

        setInputFiles(fileSelectButton, [nonZipFile]);
        fixture.detectChanges();

        expect(onFileSelectedSpy).toHaveBeenCalledOnce();
        expect(component.hasSelectedFile()).toBe(false);
        expect(uploadButton.disabled).toBe(true);
    });

    it('should reject empty input', () => {
        fixture.detectChanges();
        const onFileSelectedSpy = vi.spyOn(component, 'onFileSelectedAcceptZip');
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');

        setInputFiles(fileSelectButton, []);
        fixture.detectChanges();

        expect(onFileSelectedSpy).toHaveBeenCalledOnce();
        expect(component.hasSelectedFile()).toBe(false);
        expect(uploadButton.disabled).toBe(true);
    });

    it('should reject files exceeding max size', () => {
        fixture.detectChanges();
        const onFileSelectedSpy = vi.spyOn(component, 'onFileSelectedAcceptZip');
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');

        const sizeInBytes = MAX_FILE_SIZE + 100;
        const bytes = new Uint8Array(sizeInBytes);
        const oversizedZipFile = new File([bytes], 'my_file.zip', { type: 'application/zip' });

        setInputFiles(fileSelectButton, [oversizedZipFile]);
        fixture.detectChanges();

        expect(onFileSelectedSpy).toHaveBeenCalledOnce();
        expect(component.hasSelectedFile()).toBe(false);
        expect(uploadButton.disabled).toBe(true);
    });

    it('should enable upload button on valid file selection', () => {
        fixture.detectChanges();
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const fileSelectLabel = fixture.debugElement.nativeElement.querySelector('label[for="roomDataFileSelect"]');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');
        const zipFile = new File(['ignored content'], 'my_file.zip', { type: 'application/zip' });

        setInputFiles(fileSelectButton, [zipFile]);
        fixture.detectChanges();

        expect(component.hasSelectedFile()).toBe(true);
        expect(fileSelectLabel.textContent.trim()).toBe('my_file.zip');
        expect(uploadButton.disabled).toBe(false);
    });

    it('should make upload service call and refresh overview on valid zip upload', () => {
        fixture.detectChanges();
        mockServiceUploadSuccess();
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');
        const zipFile = new File(['ignored content'], 'my_file.zip', { type: 'application/zip' });

        setInputFiles(fileSelectButton, [zipFile]);
        fixture.detectChanges();
        uploadButton.click();
        fixture.detectChanges();

        expect(service.uploadRoomDataZipFile).toHaveBeenCalledOnce();
        expect(service.uploadRoomDataZipFile).toHaveBeenCalledWith(zipFile);
        expect(component.hasSelectedFile()).toBe(false);
        // Once from initial load, once from upload button click
        expect(service.getAdminOverview).toHaveBeenCalledTimes(2);
    });

    it('should not show upload information on failure', () => {
        fixture.detectChanges();
        vi.spyOn(service, 'uploadRoomDataZipFile').mockReturnValue(throwError(() => new Error()));
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');
        const zipFile = new File(['ignored content'], 'my_file.zip', { type: 'application/zip' });

        setInputFiles(fileSelectButton, [zipFile]);
        fixture.detectChanges();
        uploadButton.click();
        fixture.detectChanges();

        expect(service.uploadRoomDataZipFile).toHaveBeenCalledOnce();
        expect(service.uploadRoomDataZipFile).toHaveBeenCalledWith(zipFile);
        expect(service.getAdminOverview).toHaveBeenCalledOnce();
        expect(component.hasUploadInformation()).toBe(false);
    });

    it('should show upload summary on successful upload', () => {
        fixture.detectChanges();
        const uploadData = mockServiceUploadSuccess();
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');
        const zipFile = new File(['ignored content'], 'my_file.zip', { type: 'application/zip' });

        setInputFiles(fileSelectButton, [zipFile]);
        fixture.detectChanges();
        uploadButton.click();
        fixture.detectChanges();

        expect(component.hasUploadInformation()).toBe(true);
        expect(component.uploadInformation()!.uploadedFileName).toEqual(uploadData.uploadedFileName);
        expect(component.uploadInformation()!.numberOfUploadedRooms).toEqual(uploadData.numberOfUploadedRooms);
        expect(component.uploadInformation()!.numberOfUploadedSeats).toEqual(uploadData.numberOfUploadedSeats);
        expect(component.uploadInformation()!.uploadedRoomNames).toEqual(uploadData.uploadedRoomNames);
    });

    it('should call delete outdated and unused service on button click', () => {
        fixture.detectChanges();
        vi.spyOn(service, 'deleteOutdatedAndUnusedExamRooms').mockReturnValue(
            of(
                createHttpResponse({
                    numberOfDeletedExamRooms: 4,
                } as ExamRoomDeletionSummaryDTO),
            ),
        );
        const deleteButton = fixture.debugElement.nativeElement.querySelector('#roomDataDeleteOutdatedAndUnused');

        deleteButton.click();
        fixture.detectChanges();

        expect(service.deleteOutdatedAndUnusedExamRooms).toHaveBeenCalledOnce();
        // Once from initial load, once from button click
        expect(service.getAdminOverview).toHaveBeenCalledTimes(2);
    });

    it('should not reload overview if deletion fails', () => {
        fixture.detectChanges();
        vi.spyOn(service, 'deleteOutdatedAndUnusedExamRooms').mockReturnValue(throwError(() => new Error()));
        const deleteButton = fixture.debugElement.nativeElement.querySelector('#roomDataDeleteOutdatedAndUnused');

        deleteButton.click();
        fixture.detectChanges();

        expect(service.deleteOutdatedAndUnusedExamRooms).toHaveBeenCalledOnce();
        // Only once from initial load
        expect(service.getAdminOverview).toHaveBeenCalledOnce();
    });

    it('should show deletion summary on successful deletion', () => {
        fixture.detectChanges();
        vi.spyOn(service, 'deleteOutdatedAndUnusedExamRooms').mockReturnValue(
            of(
                createHttpResponse({
                    numberOfDeletedExamRooms: 4,
                } as ExamRoomDeletionSummaryDTO),
            ),
        );
        const deleteButton = fixture.debugElement.nativeElement.querySelector('#roomDataDeleteOutdatedAndUnused');

        deleteButton.click();
        fixture.detectChanges();

        expect(component.hasDeletionInformation()).toBe(true);
        expect(component.deletionInformation()).toBeDefined();
        expect(component.deletionInformation()!.numberOfDeletedExamRooms).toBe(4);
    });
});
