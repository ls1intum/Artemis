import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { throwError, of } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { AccountService } from 'app/core/auth/account.service';
import { Account } from 'app/core/user/account.model';
import { SettingsComponent } from 'app/account/settings/settings.component';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';

describe('SettingsComponent', () => {
    let comp: SettingsComponent;
    let fixture: ComponentFixture<SettingsComponent>;
    let accountService: AccountService;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [SettingsComponent],
            providers: [FormBuilder, { provide: AccountService, useClass: MockAccountService }],
        })
            .overrideTemplate(SettingsComponent, '')
            .compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(SettingsComponent);
        comp = fixture.componentInstance;
        accountService = TestBed.get(AccountService);
        comp.settingsAccount = { langKey: 'en' } as Account;
    });

    it('should notify of success upon successful save', () => {
        // GIVEN
        spyOn(accountService, 'save').and.returnValue(of({}));

        // WHEN
        comp.ngOnInit();
        comp.save();

        // THEN
        expect(comp.success).toBe('OK');
        expect(comp.error).toBe(null);
    });

    it('should notify of error upon failed save', () => {
        // GIVEN
        spyOn(accountService, 'save').and.returnValue(throwError('An error message'));

        // WHEN
        comp.ngOnInit();
        comp.save();

        // THEN
        expect(comp.success).toBe(null);
        expect(comp.error).toBe('ERROR');
    });
});
