import { ComponentFixture, TestBed, fakeAsync, inject, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterState } from '@angular/router';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { Subject, of } from 'rxjs';
import { FormBuilder, NgForm, NgModel } from '@angular/forms';
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
import { MockDirective, MockModule } from 'ng-mocks';
import { MockTranslateService, TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatChipInputEvent, MatChipsModule } from '@angular/material/chips';
import { TranslateService } from '@ngx-translate/core';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { Title } from '@angular/platform-browser';
import * as Sentry from '@sentry/browser';
import { LANGUAGES } from 'app/core/language/language.constants';

describe('User Management Update Component', () => {
    let comp: UserManagementUpdateComponent;
    let fixture: ComponentFixture<UserManagementUpdateComponent>;
    let service: UserService;
    let titleService: Title;

    const parentRoute = {
        data: of({ user: new User(1, 'user', 'first', 'last', 'first@last.com', true, 'en', [Authority.USER], ['admin'], undefined, undefined, undefined) }),
    } as any as ActivatedRoute;
    const route = { parent: parentRoute } as any as ActivatedRoute;

    let mockRouterState: RouterState;

    let modalService: NgbModal;
    let translateService: TranslateService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(MatFormFieldModule), MockModule(MatChipsModule)],
            declarations: [UserManagementUpdateComponent, TranslatePipeMock, MockDirective(NgForm), MockDirective(NgModel)],
            providers: [
                FormBuilder,
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UserManagementUpdateComponent);
                comp = fixture.componentInstance;
                service = TestBed.inject(UserService);
                modalService = TestBed.inject(NgbModal);
                titleService = TestBed.inject(Title);
                translateService = TestBed.inject(TranslateService);
                mockRouterState = {
                    snapshot: {
                        root: { firstChild: {}, data: {} },
                    },
                } as RouterState;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('onInit', () => {
        it('should load authorities and language on init', inject(
            [JhiLanguageHelper],
            fakeAsync((languageHelper: JhiLanguageHelper) => {
                // GIVEN
                jest.spyOn(service, 'authorities').mockReturnValue(of(['USER']));
                const getAllSpy = jest.spyOn(languageHelper, 'getAll').mockReturnValue([]);

                const profileInfoStub = jest.spyOn(TestBed.inject(ProfileService), 'getProfileInfo');
                profileInfoStub.mockReturnValue(of({ activeProfiles: ['bamboo'] } as ProfileInfo));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.authorities).toHaveBeenCalledOnce();
                expect(comp.authorities).toEqual(['USER']);
                expect(getAllSpy).toHaveBeenCalledOnce();
                expect(profileInfoStub).toHaveBeenCalledOnce();
            }),
        ));

        it('should load available languages', inject(
            [JhiLanguageHelper],
            fakeAsync((languageHelper: JhiLanguageHelper) => {
                // GIVEN
                const getAllSpy = jest.spyOn(languageHelper, 'getAll');
                const profileInfoStub = jest.spyOn(TestBed.inject(ProfileService), 'getProfileInfo');
                profileInfoStub.mockReturnValue(of({ activeProfiles: ['bamboo'] } as ProfileInfo));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(getAllSpy).toHaveBeenCalledOnce();
                expect(comp.languages).toEqual(LANGUAGES);
                expect(profileInfoStub).toHaveBeenCalledOnce();
            }),
        ));

        it('should return current language', inject(
            [JhiLanguageHelper],
            fakeAsync((languageHelper: JhiLanguageHelper) => {
                // GIVEN
                const routerMock: MockRouter = TestBed.inject<MockRouter>(Router as any);
                routerMock.setRouterState(mockRouterState);

                // WHEN
                translateService.use('en');

                // THEN
                languageHelper.language.subscribe((res) => expect(res).toEqual(translateService.currentLang));
            }),
        ));

        it('should set page title based on router snapshot', inject(
            [JhiLanguageHelper],
            fakeAsync((languageHelper: JhiLanguageHelper) => {
                // GIVEN
                const routerMock: MockRouter = TestBed.inject<MockRouter>(Router as any);
                mockRouterState.snapshot.root.data = { pageTitle: 'parent.page.test' };
                mockRouterState.snapshot.root.firstChild!.data = { pageTitle: 'child.page.test' };
                routerMock.setRouterState(mockRouterState);

                const updateTitleSpy = jest.spyOn(languageHelper, 'updateTitle');
                const getPageTitleSpy = jest.spyOn(languageHelper, 'getPageTitle');
                const setTitleOnTitleServiceSpy = jest.spyOn(titleService, 'setTitle');

                // WHEN
                translateService.use('en');

                // THEN
                expect(updateTitleSpy).toHaveBeenCalledOnce();
                expect(getPageTitleSpy).toHaveBeenCalledTimes(2);
                expect(getPageTitleSpy).toHaveBeenNthCalledWith(1, mockRouterState.snapshot.root);
                expect(getPageTitleSpy).toHaveBeenNthCalledWith(2, mockRouterState.snapshot.root.firstChild);
                expect(getPageTitleSpy).toHaveLastReturnedWith('child.page.test');
                expect(setTitleOnTitleServiceSpy).toHaveBeenCalledOnce();
                expect(setTitleOnTitleServiceSpy).toHaveBeenCalledWith('child.page.test');
            }),
        ));

        it('should set page title to default', inject(
            [JhiLanguageHelper],
            fakeAsync((languageHelper: JhiLanguageHelper) => {
                // GIVEN
                const routerMock: MockRouter = TestBed.inject<MockRouter>(Router as any);
                routerMock.setRouterState(mockRouterState);

                const updateTitleSpy = jest.spyOn(languageHelper, 'updateTitle');
                const setTitleOnTitleServiceSpy = jest.spyOn(titleService, 'setTitle');

                // WHEN
                translateService.use('en');

                // THEN
                expect(updateTitleSpy).toHaveBeenCalledOnce();
                expect(setTitleOnTitleServiceSpy).toHaveBeenCalledOnce();
                expect(setTitleOnTitleServiceSpy).toHaveBeenCalledWith('global.title');
            }),
        ));

        it('should capture exception if title translation not found', inject(
            [JhiLanguageHelper],
            fakeAsync((languageHelper: JhiLanguageHelper) => {
                // GIVEN
                const routerMock: MockRouter = TestBed.inject<MockRouter>(Router as any);
                routerMock.setRouterState(mockRouterState);

                const updateTitleSpy = jest.spyOn(languageHelper, 'updateTitle');
                const getTranslationSpy = jest.spyOn(translateService, 'get').mockReturnValue(of(undefined));
                const setTitleOnTitleServiceSpy = jest.spyOn(titleService, 'setTitle');
                const captureExceptionSpy = jest.spyOn(Sentry, 'captureException');

                // WHEN
                translateService.use('en');

                // THEN
                expect(updateTitleSpy).toHaveBeenCalledOnce();
                expect(getTranslationSpy).toHaveBeenCalledOnce();
                expect(getTranslationSpy).toHaveBeenCalledWith('global.title');
                expect(captureExceptionSpy).toHaveBeenCalledOnce();
                expect(captureExceptionSpy).toHaveBeenCalledWith(new Error("Translation key 'global.title' for page title not found"));
                expect(setTitleOnTitleServiceSpy).not.toHaveBeenCalled();
            }),
        ));
    });

    describe('save', () => {
        it('should call update service on save for existing user', inject(
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
                expect(comp.isSaving).toBeFalse();
            }),
        ));

        it('should call create service on save for new user', inject(
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
                expect(comp.isSaving).toBeFalse();
            }),
        ));
    });

    it('should set saving to false on save error', () => {
        comp.isSaving = true;
        // @ts-ignore
        comp.onSaveError();
        expect(comp.isSaving).toBeFalse();
    });

    it('should set password to undefined if random password should be used', () => {
        comp.user = { password: 'abc' } as User;
        comp.shouldRandomizePassword(true);
        expect(comp.user.password).toBeUndefined();

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

        expect(openSpy).toHaveBeenCalledOnce();
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
        expect(event.chipInput!.clear).toHaveBeenCalledOnce();
        expect(groupCtrlSetValueSpy).toHaveBeenCalledOnce();
        expect(groupCtrlSetValueSpy).toHaveBeenCalledWith(null);
    });
});
