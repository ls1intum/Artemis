import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';

import { OrganizationManagementUpdateComponent } from 'app/admin/organization-management/organization-management-update.component';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { Organization } from 'app/entities/organization.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockDirective } from 'ng-mocks';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';

describe('OrganizationManagementUpdateComponent', () => {
    let component: OrganizationManagementUpdateComponent;
    let fixture: ComponentFixture<OrganizationManagementUpdateComponent>;
    let organizationService: OrganizationManagementService;
    const organization1 = new Organization();
    organization1.id = 5;
    const parentRoute = {
        data: of({ organization: organization1 }),
    } as any as ActivatedRoute;
    const route = { parent: parentRoute } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [OrganizationManagementUpdateComponent, MockDirective(TranslateDirective)],
            providers: [{ provide: ActivatedRoute, useValue: route }],
        })
            .overrideTemplate(OrganizationManagementUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(OrganizationManagementUpdateComponent);
        component = fixture.componentInstance;
        organizationService = TestBed.inject(OrganizationManagementService);
    });

    beforeEach(() => {
        jest.clearAllMocks();
    });

    describe('onInit', () => {
        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should initialize and load organization from route if any', fakeAsync(() => {
            organization1.name = 'orgOne';
            organization1.shortName = 'oO1';
            organization1.emailPattern = '.*1';

            jest.spyOn(organizationService, 'getOrganizationById').mockReturnValue(of(organization1));

            component.ngOnInit();

            expect(component.organization.id).toEqual(organization1.id);
        }));
    });

    describe('Save', () => {
        it('should update the current edited organization', fakeAsync(() => {
            organization1.name = 'updatedName';
            component.organization = organization1;
            jest.spyOn(organizationService, 'update').mockReturnValue(of(new HttpResponse<Organization>({ body: organization1 })));

            component.save();
            tick();

            expect(organizationService.update).toHaveBeenCalledWith(organization1);
            expect(component.isSaving).toBeFalse();
        }));

        it('should add the current created organization', fakeAsync(() => {
            const newOrganization = new Organization();
            newOrganization.name = 'newOrg';
            newOrganization.shortName = 'newO';
            newOrganization.emailPattern = '.*';

            component.organization = newOrganization;
            jest.spyOn(organizationService, 'add').mockReturnValue(of(new HttpResponse<Organization>({ body: newOrganization })));

            component.save();
            tick();

            expect(organizationService.add).toHaveBeenCalledWith(newOrganization);
            expect(component.isSaving).toBeFalse();
        }));
    });
});
