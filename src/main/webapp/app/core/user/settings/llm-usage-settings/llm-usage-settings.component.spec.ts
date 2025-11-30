import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LlmUsageSettingsComponent } from './llm-usage-settings.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { MockDirective, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('LlmUsageSettingsComponent', () => {
    let component: LlmUsageSettingsComponent;
    let fixture: ComponentFixture<LlmUsageSettingsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LlmUsageSettingsComponent],
            declarations: [MockDirective(TranslateDirective)],
            providers: [
                MockProvider(IrisChatService),
                MockProvider(TranslateService),
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(LlmUsageSettingsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
