import { ComponentFixture, TestBed } from '@angular/core/testing';

import { IrisSettingsComponent } from './iris-settings.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { MockDirective, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AccountService } from 'app/core/auth/account.service';

describe('IrisSettingsComponent', () => {
    let component: IrisSettingsComponent;
    let fixture: ComponentFixture<IrisSettingsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [IrisSettingsComponent],
            declarations: [MockDirective(TranslateDirective)],
            providers: [MockProvider(IrisChatService), MockProvider(TranslateService), MockProvider(AccountService), provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();

        fixture = TestBed.createComponent(IrisSettingsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
