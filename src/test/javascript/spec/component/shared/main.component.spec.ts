import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { By } from '@angular/platform-browser';
import { AppComponent } from '../../../../../main/webapp/app/app.component';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { ThemeService } from 'app/core/theme/theme.service';
import { of } from 'rxjs';
import { MockComponent } from 'ng-mocks';
import { AlertOverlayComponent } from 'app/shared/alert/alert-overlay.component';
import { PageRibbonComponent } from 'app/shared/layouts/profiles/page-ribbon.component';
import { NotificationPopupComponent } from 'app/shared/notification/notification-popup/notification-popup.component';
import { RouterModule } from '@angular/router';

// Mock the initialize method
class MockThemeService {
    initialize() {
        return of();
    }
}

describe('JhiMainComponent', () => {
    let fixture: ComponentFixture<AppComponent>;
    let comp: AppComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateTestingModule, RouterModule.forRoot([])],
            declarations: [AppComponent, MockComponent(AlertOverlayComponent), MockComponent(PageRibbonComponent), MockComponent(NotificationPopupComponent)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ThemeService, useClass: MockThemeService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AppComponent);
                comp = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should display footer if there is no exam', () => {
        comp.isExamStarted = false;
        comp.showSkeleton = true;
        fixture.detectChanges();

        const footerElement = fixture.debugElement.query(By.css('jhi-footer'));

        expect(footerElement).not.toBeNull();
    });

    it('should not display footer during an exam', () => {
        comp.isExamStarted = true;
        comp.showSkeleton = true;
        fixture.detectChanges();

        const footerElement = fixture.debugElement.query(By.css('jhi-footer'));

        expect(footerElement).toBeNull();
    });
});
