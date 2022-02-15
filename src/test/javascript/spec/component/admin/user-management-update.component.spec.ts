import { ComponentFixture, fakeAsync, inject, TestBed, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { of, Subject } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { UserManagementUpdateComponent } from 'app/admin/user-management/user-management-update.component';
import { User } from 'app/core/user/user.model';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { UserService } from 'app/core/user/user.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { Organization } from 'app/entities/organization.model';
import { OrganizationSelectorComponent } from 'app/shared/organization-selector/organization-selector.component';
import { NgForm, NgModel } from '@angular/forms';
import { MockComponent, MockDirective, MockModule } from 'ng-mocks';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatChipInputEvent, MatChipsModule } from '@angular/material/chips';

describe('User Management Update Component', () => {
    let comp: UserManagementUpdateComponent;
    let fixture: ComponentFixture<UserManagementUpdateComponent>;
    let service: UserService;
    const parentRoute = {
        data: of({ user: new User(1, 'user', 'first', 'last', 'first@last.com', true, 'en', [Authority.USER], ['admin'], undefined, undefined, undefined) }),
    } as any as ActivatedRoute;
    const route = { parent: parentRoute } as any as ActivatedRoute;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(MatFormFieldModule), MockModule(MatChipsModule)],
            declarations: [UserManagementUpdateComponent, TranslatePipeMock, MockDirective(NgForm), MockDirective(NgModel), MockComponent(AlertErrorComponent)],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UserManagementUpdateComponent);
                comp = fixture.componentInstance;
                service = TestBed.inject(UserService);
                modalService = TestBed.inject(NgbModal);
            });
    });

    describe('OnInit', () => {
        it('Should load authorities and language on init', inject(
            [JhiLanguageHelper],
            fakeAsync((languageHelper: JhiLanguageHelper) => {
                // GIVEN
                jest.spyOn(service, 'authorities').mockReturnValue(of(['USER']));
                const getAllSpy = jest.spyOn(languageHelper, 'getAll').mockReturnValue([]);

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.authorities).toHaveBeenCalled();
                expect(comp.authorities).toEqual(['USER']);
                expect(getAllSpy).toHaveBeenCalled();
            }),
        ));
    });

    describe('save', () => {
        it('Should call update service on save for existing user', inject(
            [],
            fakeAsync(() => {
                // GIVEN
                const entity = new User(123);
                jest.spyOn(service, 'update').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: entity,
                        }),
                    ),
                );
                comp.user = entity;
                comp.user.login = 'test_user';
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }),
        ));

        it('Should call create service on save for new user', inject(
            [],
            fakeAsync(() => {
                // GIVEN
                const entity = new User();
                jest.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: entity })));
                comp.user = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.create).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }),
        ));
    });

    it('should set saving to false on save error', () => {
        comp.isSaving = true;
        // @ts-ignore
        comp.onSaveError();
        expect(comp.isSaving).toBe(false);
    });

    it('should set password to undefined if random password should be used', () => {
        comp.user = { password: 'abc' } as User;
        comp.shouldRandomizePassword(true);
        expect(comp.user.password).toBe(undefined);

        comp.shouldRandomizePassword(false);
        expect(comp.user.password).toBe('');
    });

    it('should open organizations modal', () => {
        const orgs = [{}] as Organization[];
        comp.user = { organizations: orgs } as User;

        const sub = new Subject<Organization>();
        const modalRef = {
            componentInstance: { organizations: undefined },
            closed: sub.asObservable(),
        } as NgbModalRef;
        const openSpy = jest.spyOn(modalService, 'open').mockReturnValue(modalRef);

        comp.openOrganizationsModal();

        expect(openSpy).toHaveBeenCalledTimes(1);
        expect(openSpy).toHaveBeenCalledWith(OrganizationSelectorComponent, { size: 'xl', backdrop: 'static' });
        expect(modalRef.componentInstance.organizations).toBe(orgs);

        const newOrg = {} as Organization;
        sub.next(newOrg);
        expect(orgs).toContain(newOrg);

        comp.user.organizations = undefined;

        sub.next(newOrg);
        expect(comp.user.organizations).toEqual([newOrg]);
    });

    it('should remove organization from user', () => {
        const org0 = { id: 1 };
        const org1 = { id: 2 };
        comp.user = { organizations: [org0, org1] } as User;
        comp.removeOrganizationFromUser(org1);
        expect(comp.user.organizations).toEqual([org0]);
    });

    it('should add users to groups', () => {
        const groupCtrlSetValueSpy = jest.spyOn(comp.groupCtrl, 'setValue');

        const newGroup = 'nicegroup';
        comp.user = { groups: [] } as any as User;
        const event = { value: newGroup, chipInput: { clear: jest.fn() } } as any as MatChipInputEvent;
        comp.onGroupAdd(comp.user, event);

        expect(comp.user.groups).toEqual([newGroup]);
        expect(event.chipInput!.clear).toHaveBeenCalledTimes(1);
        expect(groupCtrlSetValueSpy).toHaveBeenCalledTimes(1);
        expect(groupCtrlSetValueSpy).toHaveBeenCalledWith(null);
    });
});
