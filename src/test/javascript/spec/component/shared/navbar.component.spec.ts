import * as chai from 'chai';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { NgbCollapse, NgbDropdown } from '@ng-bootstrap/ng-bootstrap';
import { ChartsModule } from 'ng2-charts';
import { NavbarComponent } from 'app/shared/layouts/navbar/navbar.component';
import { NotificationSidebarComponent } from 'app/shared/notification/notification-sidebar/notification-sidebar.component';
import { GuidedTourComponent } from 'app/guided-tour/guided-tour.component';
import { LoadingNotificationComponent } from 'app/shared/notification/loading-notification/loading-notification.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { ActiveMenuDirective } from 'app/shared/layouts/navbar/active-menu.directive';
import { JhiTranslateDirective } from 'ng-jhipster';
import { FindLanguageFromKeyPipe } from 'app/shared/language/find-language-from-key.pipe';
import { Router } from '@angular/router';
import { Directive, Input } from '@angular/core';
import { MockRouter } from '../../helpers/mocks/mock-router';

chai.use(sinonChai);
const expect = chai.expect;

// tslint:disable-next-line:directive-selector
@Directive({ selector: '[routerLink]' })
export class MockRouterLinkDirective {
    @Input('routerLink') data: any;
}

// tslint:disable-next-line:directive-selector
@Directive({ selector: '[routerLinkActiveOptions]' })
export class MockRouterLinkActiveOptionsDirective {
    @Input('routerLinkActiveOptions') data: any;
}

class MockBreadcrumb {
    label: string;
    uri: string;
    translate: boolean;
}

describe('NavbarComponent', () => {
    let fixture: ComponentFixture<NavbarComponent>;
    let component: NavbarComponent;

    const router = new MockRouter();
    router.setUrl('');

    const courseManagementCrumb = {
        label: 'global.menu.course',
        translate: true,
        uri: '/course-management/',
    } as MockBreadcrumb;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ChartsModule],
            declarations: [
                NavbarComponent,
                MockDirective(NgbCollapse),
                MockDirective(HasAnyAuthorityDirective),
                MockDirective(NgbDropdown),
                MockDirective(ActiveMenuDirective),
                MockDirective(JhiTranslateDirective),
                MockDirective(MockRouterLinkDirective),
                MockDirective(MockRouterLinkActiveOptionsDirective),
                MockPipe(TranslatePipe),
                MockPipe(FindLanguageFromKeyPipe),
                MockComponent(NotificationSidebarComponent),
                MockComponent(GuidedTourComponent),
                MockComponent(LoadingNotificationComponent),
            ],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                MockProvider(TranslateService),
                { provide: Router, useValue: router },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(NavbarComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize component', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
    });

    it('should build breadcrumbs for course management', () => {
        const testUrl = '/course-management/';
        router.setUrl(testUrl);

        fixture.detectChanges();

        // Use matching here to ignore non-semantic differences between objects
        sinon.assert.match(component.breadcrumbs[0], courseManagementCrumb);
    });
});
