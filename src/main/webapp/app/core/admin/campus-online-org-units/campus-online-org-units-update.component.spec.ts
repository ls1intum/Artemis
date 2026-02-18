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
import { CampusOnlineOrgUnitsUpdateComponent } from 'app/core/admin/campus-online-org-units/campus-online-org-units-update.component';
import { CampusOnlineOrgUnit, CampusOnlineService } from 'app/core/course/manage/services/campus-online.service';

describe('CampusOnlineOrgUnitsUpdateComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CampusOnlineOrgUnitsUpdateComponent;
    let fixture: ComponentFixture<CampusOnlineOrgUnitsUpdateComponent>;
    let campusOnlineService: CampusOnlineService;

    const mockActivatedRoute = {
        parent: {
            data: of({}),
        },
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CampusOnlineOrgUnitsUpdateComponent],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideTemplate(CampusOnlineOrgUnitsUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(CampusOnlineOrgUnitsUpdateComponent);
        component = fixture.componentInstance;
        campusOnlineService = TestBed.inject(CampusOnlineService);
    });

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should initialize with empty org unit for new', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.orgUnit().externalId).toBe('');
        expect(component.orgUnit().name).toBe('');
        expect(component.isSaving()).toBe(false);
    });

    it('should load org unit for edit', () => {
        const existingOrgUnit: CampusOnlineOrgUnit = { id: 1, externalId: '12345', name: 'CIT' };
        mockActivatedRoute.parent.data = of({ orgUnit: { id: 1 } });
        vi.spyOn(campusOnlineService, 'getOrgUnit').mockReturnValue(of(existingOrgUnit));

        fixture.detectChanges();
        expect(component.orgUnit().id).toBe(1);
        expect(component.orgUnit().externalId).toBe('12345');
        expect(component.orgUnit().name).toBe('CIT');
    });

    it('should create a new org unit', () => {
        const newOrgUnit: CampusOnlineOrgUnit = { externalId: '99999', name: 'New' };
        component.orgUnit.set(newOrgUnit);

        const createdOrgUnit: CampusOnlineOrgUnit = { id: 3, externalId: '99999', name: 'New' };
        vi.spyOn(campusOnlineService, 'createOrgUnit').mockReturnValue(of(new HttpResponse({ body: createdOrgUnit })));
        vi.spyOn(component, 'previousState').mockImplementation(() => {});

        component.save();

        expect(campusOnlineService.createOrgUnit).toHaveBeenCalledWith(newOrgUnit);
        expect(component.isSaving()).toBe(false);
    });

    it('should update an existing org unit', () => {
        const existingOrgUnit: CampusOnlineOrgUnit = { id: 1, externalId: '12345', name: 'Updated' };
        component.orgUnit.set(existingOrgUnit);

        vi.spyOn(campusOnlineService, 'updateOrgUnit').mockReturnValue(of(new HttpResponse({ body: existingOrgUnit })));
        vi.spyOn(component, 'previousState').mockImplementation(() => {});

        component.save();

        expect(campusOnlineService.updateOrgUnit).toHaveBeenCalledWith(existingOrgUnit);
        expect(component.isSaving()).toBe(false);
    });
});
