import { DomainDependent, DomainService } from './code-editor-repository.service';
import { BuildLogEntryArray } from 'app/entities/build-log';
import { LocalStorageService } from 'ngx-webstorage';
import { compose, filter, fromPairs, map, toPairs } from 'lodash/fp';
import { Participation } from 'app/entities/participation';
import { AnnotationArray } from 'app/entities/ace-editor';
import { timestamp } from 'rxjs/operators';
import { ProgrammingExercise } from 'app/entities/programming-exercise';

export abstract class CodeEditorSessionService<T> extends DomainDependent<T> {
    abstract storeSession: (session: { errors: { [fileName: string]: AnnotationArray }; timestamp: number }) => void;
    abstract loadSession: () => { errors: { [fileName: string]: AnnotationArray }; timestamp: number } | null;
}

export class CodeEditorParticipationSessionService extends CodeEditorSessionService<Participation> {
    constructor(private localStorageService: LocalStorageService, domainService: DomainService<Participation>) {
        super(domainService);
    }

    storeSession = (session: { errors: { [fileName: string]: AnnotationArray }; timestamp: number }) => {
        this.localStorageService.store('sessions', JSON.stringify({ [this.domain.id]: session }));
    };

    loadSession = () => {
        const sessions = JSON.parse(this.localStorageService.retrieve('sessions') || '{}');
        const session = sessions[this.domain.id];
        return session
            ? {
                  errors: compose(
                      fromPairs,
                      map(([fileName, errors]) => [fileName, new AnnotationArray(...errors)]),
                      filter(([, errors]) => errors.length),
                      toPairs,
                  )(session.errors),
                  timestamp: session.timestamp,
              }
            : null;
    };
}

export class CodeEditorTestSessionService extends CodeEditorSessionService<ProgrammingExercise> {
    constructor(private localStorageService: LocalStorageService, domainService: DomainService<ProgrammingExercise>) {
        super(domainService);
    }

    storeSession = (session: { errors: { [fileName: string]: AnnotationArray }; timestamp: number }) => {
        // TODO: We currently don't store the test session
    };

    loadSession = () => {
        // TODO: We currently don't store the test session
        return null;
    };
}
