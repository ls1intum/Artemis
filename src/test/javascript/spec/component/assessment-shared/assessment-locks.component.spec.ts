import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { AssessmentLocksComponent } from 'app/assessment/assessment-locks/assessment-locks.component';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

describe('AssessmentLocksComponent', () => {
    let component: AssessmentLocksComponent;
    let fixture: ComponentFixture<AssessmentLocksComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule, ArtemisAssessmentSharedModule],
            declarations: [],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(AssessmentLocksComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should call getAllLockedSubmissions on init', () => {
        component.ngOnInit();

        expect(component.getAllLockedSubmissions).toHaveBeenCalled;
        expect(component.submissions.length).toBe(0);
    });
});
