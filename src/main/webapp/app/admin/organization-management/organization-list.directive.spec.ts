import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MockProvider } from 'ng-mocks';
import { Subject } from 'rxjs';
import { AlertService } from 'app/foundation/service/alert.service';
import { PageableResult } from 'app/foundation/pagination/pageable-table';
import { OrganizationSelectorComponent } from '../organization-selector/organization-selector.component';
import { OrganizationManagementComponent } from './organization-management.component';
import { OrganizationManagementService } from './organization-management.service';
import { OrganizationListDirective } from './organization-list.directive';
import { Organization } from './organization.model';

const components: { name: string; component: Type<OrganizationListDirective> }[] = [
    { name: 'management', component: OrganizationManagementComponent },
    { name: 'selection', component: OrganizationSelectorComponent },
];

describe.each(components)('Organization pagination: $name', ({ component: componentType }) => {
    let component: OrganizationListDirective;
    let first: Subject<PageableResult<Organization>>;
    let latest: Subject<PageableResult<Organization>>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [componentType],
            providers: [MockProvider(OrganizationManagementService), MockProvider(AlertService)],
        });
        TestBed.overrideTemplate(componentType, '');
        component = TestBed.createComponent(componentType).componentInstance;
        vi.spyOn(TestBed.inject(AlertService), 'error');
        first = new Subject<PageableResult<Organization>>();
        latest = new Subject<PageableResult<Organization>>();
        vi.spyOn(TestBed.inject(OrganizationManagementService), 'getOrganizations').mockReturnValueOnce(first).mockReturnValueOnce(latest);
    });

    it('ignores an older response while the requested page is still loading', () => {
        component.loadOrganizations({ pageIndex: 0, pageSize: 10 });
        component.loadOrganizations({ pageIndex: 1, pageSize: 10 });
        first.next({ content: [{ id: 1 }], totalElements: 99 });
        expect(component.isLoading()).toBe(true);
        expect(component.organizations()).toEqual([]);
        expect(component.totalCount()).toBe(0);

        latest.next({ content: [{ id: 2 }], totalElements: 12 });
        expect(component.isLoading()).toBe(false);
        expect(component.organizations()).toEqual([{ id: 2 }]);
        expect(component.totalCount()).toBe(12);
    });

    it('keeps the latest page when an older request subsequently fails', () => {
        component.loadOrganizations({ pageIndex: 0, pageSize: 10 });
        component.loadOrganizations({ pageIndex: 1, pageSize: 10 });
        latest.next({ content: [{ id: 2 }], totalElements: 12 });
        first.error(new HttpErrorResponse({ status: 500 }));

        expect(component.isLoading()).toBe(false);
        expect(component.organizations()).toEqual([{ id: 2 }]);
        expect(component.totalCount()).toBe(12);
        expect(TestBed.inject(AlertService).error).not.toHaveBeenCalled();
    });
});
