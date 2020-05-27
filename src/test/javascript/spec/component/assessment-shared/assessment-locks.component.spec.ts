import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AssessmentLocksComponent } from 'app/assessment/assessment-locks/assessment-locks.component';
import { ArtemisTestModule } from '../../test.module';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisAppRoutingModule } from 'app/app-routing.module';
import { RouterTestingModule } from '@angular/router/testing';
import { ArtemisAppModule } from 'app/app.module';

describe('AssessmentGeneralFeedbackComponent', () => {
    let component: AssessmentLocksComponent;
    let fixture: ComponentFixture<AssessmentLocksComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [AssessmentLocksComponent],
            imports: [ArtemisTestModule, ArtemisSharedModule, ArtemisAppRoutingModule, RouterTestingModule, ArtemisAppModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AssessmentLocksComponent);
                component = fixture.componentInstance;
            });
    });

    it('should create', () => {
        console.log(component);
        expect(component).toBeTruthy();
    });

    it('should call getAllLockedSubmissions on init', () => {
        component.ngOnInit();

        expect(component.getAllLockedSubmissions).toHaveBeenCalled;
        expect(component.lockedSubmissions).not.toBeNull();
    });
});
