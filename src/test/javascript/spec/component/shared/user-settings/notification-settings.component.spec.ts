import { NotificationSettingsComponent } from 'app/shared/user-settings/notification-settings/notification-settings.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { MockProvider } from 'ng-mocks/cjs/lib/mock-provider/mock-provider';
import { TextToLowerCamelCasePipe } from 'app/shared/pipes/text-to-lower-camel-case.pipe';
import { MockHasAnyAuthorityDirective } from '../../../helpers/mocks/directive/mock-has-any-authority.directive';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { MockPipe } from 'ng-mocks/cjs/lib/mock-pipe/mock-pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';
import { JhiAlertService } from 'ng-jhipster';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { MockComponent } from 'ng-mocks';
import { OptionSpecifier } from 'app/shared/constants/user-settings.constants';
import { NotificationOptionCore } from 'app/shared/user-settings/notification-settings/notification-settings.default';

chai.use(sinonChai);
const expect = chai.expect;

describe('NotificationSettingsComponent', () => {
    let comp: NotificationSettingsComponent;
    let fixture: ComponentFixture<NotificationSettingsComponent>;

    const imports = [ArtemisTestModule, TranslateTestingModule];
    const declarations = [
        MockComponent(AlertComponent),
        NotificationSettingsComponent,
        MockHasAnyAuthorityDirective,
        MockPipe(ArtemisTranslatePipe),
        MockPipe(TextToLowerCamelCasePipe),
    ];
    const providers = [
        MockProvider(JhiAlertService),
        MockProvider(TextToLowerCamelCasePipe),
        { provide: LocalStorageService, useClass: MockSyncStorage },
        { provide: SessionStorageService, useClass: MockSyncStorage },
    ];

    beforeEach(() => {
        // TestBed.configureTestingModule({
        return TestBed.configureTestingModule({
            imports,
            declarations,
            providers,
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(NotificationSettingsComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should initialize component', () => {
        comp.ngOnInit();
        expect(comp).to.be.ok;
    });

    it('should toggle option', () => {
        const optionId = OptionSpecifier.NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES;
        const webappStatus = true;
        const notificationOptionCoreA: NotificationOptionCore = {
            optionSpecifier: optionId,
            webapp: webappStatus,
            email: false,
            changed: false,
        };
        comp.optionCores = [notificationOptionCoreA];
        const event = {
            currentTarget: {
                id: optionId,
            },
        };

        expect(comp.optionsChanged).to.be.false;
        expect(notificationOptionCoreA.changed).to.be.false;

        comp.toggleOption(event);

        expect(notificationOptionCoreA.webapp).not.to.be.equal(webappStatus);
        expect(notificationOptionCoreA.changed).to.be.true;
        expect(comp.optionsChanged).to.be.true;
    });
});
