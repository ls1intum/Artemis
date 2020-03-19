/* tslint:disable:no-unused-expression */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisAdminModule } from 'app/admin/admin.module';
import { UserManagementComponent } from 'app/admin/user-management/user-management.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('AdminModule', () => {
    let comp: UserManagementComponent;
    let fixture: ComponentFixture<UserManagementComponent>;
    let debugElement: DebugElement;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisAdminModule],
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
});
