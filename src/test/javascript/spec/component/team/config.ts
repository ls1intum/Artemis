import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { JhiEventManager } from 'ng-jhipster';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTeamService } from '../../helpers/mocks/service/mock-team.service';
import { ArtemisTestModule } from '../../test.module';

const config = {
    imports: [ArtemisTestModule, ArtemisTeamModule],
    declarations: [],
    providers: [
        JhiEventManager,
        { provide: TeamService, useClass: MockTeamService },
        { provide: LocalStorageService, useClass: MockSyncStorage },
        { provide: SessionStorageService, useClass: MockSyncStorage },
    ],
};

export default config;
