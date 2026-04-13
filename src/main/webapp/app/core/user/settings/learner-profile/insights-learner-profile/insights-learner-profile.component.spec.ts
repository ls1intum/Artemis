import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InsightsLearnerProfileComponent } from './insights-learner-profile.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('InsightsLearnerProfileComponent', () => {
    setupTestBed({ zoneless: true });

    let component: InsightsLearnerProfileComponent;
    let fixture: ComponentFixture<InsightsLearnerProfileComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [InsightsLearnerProfileComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(InsightsLearnerProfileComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render the component', () => {
        const compiled = fixture.nativeElement;
        expect(compiled).toBeTruthy();
    });
});
