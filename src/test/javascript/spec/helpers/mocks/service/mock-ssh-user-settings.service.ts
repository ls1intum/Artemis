import { of } from 'rxjs';
import { UserSshPublicKey } from 'app/entities/programming/user-ssh-public-key.model';
import { IASshUserSettingsService } from 'app/core/user/settings/ssh-settings/ssh-user-settings.service';

export class MockSshUserSettingsService implements IASshUserSettingsService {
    getCachedSshKeys = () => Promise.resolve([]);
    getSshPublicKeys = () => of([]);
    getSshPublicKey = (keyId: number) => of({ id: 99 } as UserSshPublicKey);
    addNewSshPublicKey = (userKey: UserSshPublicKey) => of();
    deleteSshPublicKey = (keyId: number) => of();
}
