import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExternalLlmUsageSettingsComponent } from './external-llm-usage-settings.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { MockDirective, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExternalLlmUsageSettingsComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ExternalLlmUsageSettingsComponent;
    let fixture: ComponentFixture<ExternalLlmUsageSettingsComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ExternalLlmUsageSettingsComponent, MockDirective(TranslateDirective)],
            providers: [
                MockProvider(IrisChatService),
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
            ],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(ExternalLlmUsageSettingsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
