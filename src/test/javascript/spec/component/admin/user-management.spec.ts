/* tslint:disable:no-unused-expression */
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisAdminModule } from 'app/admin/admin.module';
import { UserManagementComponent } from 'app/admin/user-management/user-management.component';
import { UserService } from 'app/core/user/user.service';
import { MockUserService } from '../../mocks/mock-user.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../mocks/mock-account.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('UserManagementComponent', () => {
    let comp: UserManagementComponent;
    let fixture: ComponentFixture<UserManagementComponent>;
    let debugElement: DebugElement;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisAdminModule],
            providers: [
                { provide: UserService, useClass: MockUserService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UserManagementComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    // The admin module is lazy loaded - we therefore need a dummy test to load the module and verify that there are no dependency related issues.
    it('should render a component from the admin module', () => {
        expect(comp).to.exist;
    });

    it('should parse the user search result into the correct component state', fakeAsync(() => {
        comp.loadAll();
        // 1 sec of pause, because of the debounce time
        tick(1000);

        expect(comp.users[0].id).to.be.eq(1);
        expect(comp.totalItems).to.be.eq(1);
        expect(comp.loadingSearchResult).to.be.false;
    }));
});
