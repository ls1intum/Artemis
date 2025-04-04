import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, lastValueFrom, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { UserSshPublicKey } from 'app/programming/shared/entities/user-ssh-public-key.model';

export interface IASshUserSettingsService {
    getCachedSshKeys: () => Promise<UserSshPublicKey[] | undefined>;
    getSshPublicKeys: () => Observable<UserSshPublicKey[]>;
    getSshPublicKey: (keyId: number) => Observable<UserSshPublicKey>;
    addNewSshPublicKey: (userKey: UserSshPublicKey) => Observable<HttpResponse<UserSshPublicKey>>;
    deleteSshPublicKey: (keyId: number) => Observable<void>;
}

@Injectable({ providedIn: 'root' })
export class SshUserSettingsService implements IASshUserSettingsService {
    private http = inject(HttpClient);

    private userSshKeysValue?: UserSshPublicKey[];
    private sshKeysRequest?: Promise<UserSshPublicKey[] | undefined>;

    get sshKeys() {
        return this.userSshKeysValue;
    }

    set sshKeys(sshKeys: UserSshPublicKey[] | undefined) {
        this.userSshKeysValue = sshKeys;
    }

    /**
     * Gets all ssh keys of a user from the server, but only if they have not yet been requested before, or aren't already present
     */
    getCachedSshKeys(): Promise<UserSshPublicKey[] | undefined> {
        // check and see if we have retrieved the sshKeys data from the server already
        // if we have, reuse it by immediately resolving
        if (this.sshKeys) {
            return Promise.resolve(this.sshKeys);
        }

        // If a request is already in progress, return the same promise
        if (this.sshKeysRequest) {
            return this.sshKeysRequest;
        }

        this.sshKeysRequest = lastValueFrom(
            this.getSshPublicKeys().pipe(
                map((keys: UserSshPublicKey[]) => {
                    if (keys) {
                        this.sshKeys = keys ? keys : undefined;
                        this.sshKeysRequest = undefined;
                        return this.sshKeys;
                    }
                    return this.sshKeys;
                }),
                catchError(() => {
                    this.sshKeys = undefined;
                    this.sshKeysRequest = undefined;
                    return of(undefined);
                }),
            ),
        );
        return this.sshKeysRequest;
    }

    /**
     * Retrieves all public SSH keys of a user
     */
    getSshPublicKeys(): Observable<UserSshPublicKey[]> {
        return this.http.get<UserSshPublicKey[]>('api/programming/ssh-settings/public-keys');
    }

    /**
     * Retrieves a specific public SSH keys of a user
     */
    getSshPublicKey(keyId: number): Observable<UserSshPublicKey> {
        return this.http.get<UserSshPublicKey>(`api/programming/ssh-settings/public-key/${keyId}`);
    }

    /**
     * Sends the added SSH key to the server
     *
     * @param userSshPublicKey The userSshPublicKey DTO containing the details for the new key which should be created
     */
    addNewSshPublicKey(userSshPublicKey: UserSshPublicKey): Observable<HttpResponse<UserSshPublicKey>> {
        return this.http.post<UserSshPublicKey>('api/programming/ssh-settings/public-key', userSshPublicKey, { observe: 'response' });
    }

    /**
     * Sends a request to the server to delete the user's current SSH key
     */
    deleteSshPublicKey(keyId: number): Observable<void> {
        return this.http.delete<void>(`api/programming/ssh-settings/public-key/${keyId}`);
    }
}
