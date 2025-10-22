import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InsightsLearnerProfileComponent } from './insights-learner-profile.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockProvider } from 'ng-mocks';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('InsightsLearnerProfileComponent', () => {
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

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render the component', () => {
        const compiled = fixture.nativeElement;
        expect(compiled).toBeTruthy();
    });
});
