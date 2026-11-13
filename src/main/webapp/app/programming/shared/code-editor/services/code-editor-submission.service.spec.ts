import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { DomainService } from 'app/programming/shared/code-editor/services/code-editor-domain.service';
import { CodeEditorSubmissionService } from 'app/programming/shared/code-editor/services/code-editor-submission.service';
import { DomainChange, DomainType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { User } from 'app/account/user/user.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';

describe('CodeEditorSubmissionService', () => {
    const domainChange = new Subject<DomainChange>();
    let getLatestPendingSubmission: ReturnType<typeof vi.fn>;

    const setup = (login: string | undefined) => {
        getLatestPendingSubmission = vi.fn().mockReturnValue(of({ submission: undefined }));
        TestBed.configureTestingModule({
            providers: [
                { provide: DomainService, useValue: { subscribeDomainChange: () => domainChange.asObservable() } },
                {
                    provide: ProgrammingSubmissionService,
                    useValue: { getLatestPendingSubmissionByParticipationId: getLatestPendingSubmission, unsubscribeForLatestSubmissionOfParticipation: vi.fn() },
                },
                { provide: AlertService, useValue: { error: vi.fn() } },
                { provide: AccountService, useValue: { userIdentity: () => (login ? ({ login } as User) : undefined) } },
            ],
        });
        TestBed.inject(CodeEditorSubmissionService);
    };

    const participationOf = (student?: User, team?: Team): StudentParticipation => ({ id: 1, exercise: { id: 2 }, student, team }) as StudentParticipation;

    it('subscribes to the topics of the user for the participation of the user', () => {
        setup('student1');

        domainChange.next([DomainType.PARTICIPATION, participationOf({ login: 'student1' } as User)]);

        expect(getLatestPendingSubmission).toHaveBeenCalledWith(1, 2, true);
    });

    it('subscribes to the topics of the instructors for the participation of another student', () => {
        setup('instructor1');

        domainChange.next([DomainType.PARTICIPATION, participationOf({ login: 'student1' } as User)]);

        expect(getLatestPendingSubmission).toHaveBeenCalledWith(1, 2, false);
    });

    it('subscribes to the topics of the user for the participation of the team of the user', () => {
        setup('student2');

        domainChange.next([DomainType.PARTICIPATION, participationOf(undefined, { students: [{ login: 'student1' }, { login: 'student2' }] } as Team)]);

        expect(getLatestPendingSubmission).toHaveBeenCalledWith(1, 2, true);
    });

    it('treats a participation that does not say whom it belongs to as the own one', () => {
        setup('instructor1');

        domainChange.next([DomainType.PARTICIPATION, participationOf()]);

        expect(getLatestPendingSubmission).toHaveBeenCalledWith(1, 2, true);
    });
});
