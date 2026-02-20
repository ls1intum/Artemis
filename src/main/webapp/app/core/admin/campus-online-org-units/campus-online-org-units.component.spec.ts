import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';

import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/shared/service/alert.service';
import { CampusOnlineOrgUnitsComponent } from 'app/core/admin/campus-online-org-units/campus-online-org-units.component';
import { CampusOnlineOrgUnit, CampusOnlineService } from 'app/core/course/manage/services/campus-online.service';

describe('CampusOnlineOrgUnitsComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CampusOnlineOrgUnitsComponent;
    let fixture: ComponentFixture<CampusOnlineOrgUnitsComponent>;
    let campusOnlineService: CampusOnlineService;
    let alertService: AlertService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CampusOnlineOrgUnitsComponent],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: ActivatedRoute, useValue: { data: of({}) } },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideTemplate(CampusOnlineOrgUnitsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(CampusOnlineOrgUnitsComponent);
        component = fixture.componentInstance;
        campusOnlineService = TestBed.inject(CampusOnlineService);
        alertService = TestBed.inject(AlertService);
    });

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should initialize and load org units', () => {
        const orgUnit1: CampusOnlineOrgUnit = { id: 1, externalId: '12345', name: 'CIT' };
        const orgUnit2: CampusOnlineOrgUnit = { id: 2, externalId: '67890', name: 'Management' };

        vi.spyOn(campusOnlineService, 'getOrgUnits').mockReturnValue(of([orgUnit1, orgUnit2]));

        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.orgUnits()).toHaveLength(2);
        expect(component.orgUnits()[0].externalId).toBe('12345');
        expect(component.orgUnits()[1].name).toBe('Management');
    });

    it('should delete an org unit and update the list', () => {
        const orgUnit1: CampusOnlineOrgUnit = { id: 1, externalId: '12345', name: 'CIT' };
        const orgUnit2: CampusOnlineOrgUnit = { id: 2, externalId: '67890', name: 'Management' };

        component.orgUnits.set([orgUnit1, orgUnit2]);
        vi.spyOn(campusOnlineService, 'deleteOrgUnit').mockReturnValue(of(new HttpResponse<void>()));

        component.deleteOrgUnit(1);
        expect(component.orgUnits()).toHaveLength(1);
        expect(component.orgUnits()[0].id).toBe(2);
    });

    it('should import org units from CSV and show success alert', () => {
        const importedUnits: CampusOnlineOrgUnit[] = [
            { id: 10, externalId: '11111', name: 'Faculty A' },
            { id: 11, externalId: '22222', name: 'Faculty B' },
        ];
        const allUnitsAfterImport: CampusOnlineOrgUnit[] = [
            { id: 1, externalId: '12345', name: 'CIT' },
            { id: 10, externalId: '11111', name: 'Faculty A' },
            { id: 11, externalId: '22222', name: 'Faculty B' },
        ];

        vi.spyOn(campusOnlineService, 'importOrgUnits').mockReturnValue(of(importedUnits));
        vi.spyOn(campusOnlineService, 'getOrgUnits').mockReturnValue(of(allUnitsAfterImport));
        const successSpy = vi.spyOn(alertService, 'success');

        // Mock FileReader to synchronously trigger onload with CSV content
        const csvContent = 'externalId,name\n11111,Faculty A\n22222,Faculty B';
        vi.spyOn(FileReader.prototype, 'readAsText').mockImplementation(function (this: FileReader) {
            Object.defineProperty(this, 'result', { value: csvContent, writable: false });
            this.onload?.({} as ProgressEvent<FileReader>);
        });

        const input = { files: [new File([csvContent], 'test.csv', { type: 'text/csv' })], value: '' } as unknown as HTMLInputElement;
        component.onCSVFileSelected({ target: input } as unknown as Event);

        expect(campusOnlineService.importOrgUnits).toHaveBeenCalledWith([
            { externalId: '11111', name: 'Faculty A' },
            { externalId: '22222', name: 'Faculty B' },
        ]);
        expect(component.orgUnits()).toHaveLength(3);
        expect(component.isImporting()).toBe(false);
        expect(successSpy).toHaveBeenCalledOnce();
    });

    it('should show error alert when CSV has no valid rows', () => {
        const errorSpy = vi.spyOn(alertService, 'error');

        const csvContent = 'invalidHeader1,invalidHeader2\nval1,val2';
        vi.spyOn(FileReader.prototype, 'readAsText').mockImplementation(function (this: FileReader) {
            Object.defineProperty(this, 'result', { value: csvContent, writable: false });
            this.onload?.({} as ProgressEvent<FileReader>);
        });

        const input = { files: [new File([csvContent], 'test.csv', { type: 'text/csv' })], value: '' } as unknown as HTMLInputElement;
        component.onCSVFileSelected({ target: input } as unknown as Event);

        expect(component.isImporting()).toBe(false);
        expect(errorSpy).toHaveBeenCalledOnce();
    });
});
