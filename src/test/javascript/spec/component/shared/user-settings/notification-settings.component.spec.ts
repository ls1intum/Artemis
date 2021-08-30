import { NotificationSettingsComponent } from 'app/shared/user-settings/notification-settings/notification-settings.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NotificationService } from 'app/shared/notification/notification.service';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { ArtemisTestModule } from '../../../test.module';
import { MockProvider } from 'ng-mocks/cjs/lib/mock-provider/mock-provider';
import { TextToLowerCamelCasePipe } from 'app/shared/pipes/text-to-lower-camel-case.pipe';
import { MockHasAnyAuthorityDirective } from '../../../helpers/mocks/directive/mock-has-any-authority.directive';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { MockPipe } from 'ng-mocks/cjs/lib/mock-pipe/mock-pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('NotificationSettingsComponent', () => {
    let comp: NotificationSettingsComponent;
    let fixture: ComponentFixture<NotificationSettingsComponent>;

    let notificationService: NotificationService;

    let userSettingsService: UserSettingsService;

    const imports = [ArtemisTestModule, TranslateTestingModule];
    const declarations = [NotificationSettingsComponent, MockHasAnyAuthorityDirective, MockPipe(ArtemisTranslatePipe), MockPipe(TextToLowerCamelCasePipe)];
    const providers = [
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

                notificationService = TestBed.inject(NotificationService);
                userSettingsService = TestBed.inject(UserSettingsService);
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).to.be.ok;
    });
});
