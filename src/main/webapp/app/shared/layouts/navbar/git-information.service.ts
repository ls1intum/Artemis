import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, OperatorFunction } from 'rxjs';
import { filter, map } from 'rxjs/operators';

/**
 * Provides git information for the current commit
 */
@Injectable({ providedIn: 'root' })
export class GitInformationService {
    private infoUrl = SERVER_API_URL + 'management/info';
    private gitInformation: BehaviorSubject<GitInformation | undefined>;

    constructor(private http: HttpClient) {}

    /**
     * Returns an observable that will provide current git information
     */
    public getGitInformation(): Observable<GitInformation> {
        if (!this.gitInformation) {
            this.gitInformation = new BehaviorSubject(undefined);
            this.http
                .get<GitInformation>(this.infoUrl, { observe: 'response' })
                .pipe(
                    map((res: HttpResponse<any>) => {
                        const data = res.body!;
                        let gitInformation = new GitInformation();
                        gitInformation = data.git;
                        return gitInformation;
                    }),
                )
                .subscribe((gitInformation: GitInformation) => {
                    this.gitInformation.next(gitInformation);
                });
        }
        return this.gitInformation.pipe(filter((x) => x != undefined) as OperatorFunction<GitInformation | undefined, GitInformation>);
    }
}

export class GitInformation {
    branch: string;
    commit: {
        id: {
            abbrev: string;
            full: string;
        };
        message: {
            full: string;
            short: string;
        };
        time: string;
    };
}
