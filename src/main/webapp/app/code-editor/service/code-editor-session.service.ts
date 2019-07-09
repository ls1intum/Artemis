import { Injectable } from '@angular/core';
import { DomainDependent, DomainService, DomainType } from 'app/code-editor/service';
import { LocalStorageService } from 'ngx-webstorage';
import { compose, filter, fromPairs, map, toPairs } from 'lodash/fp';
import { AnnotationArray } from 'app/entities/ace-editor';
import { ICodeEditorSessionService } from 'app/code-editor/service/icode-editor-session.service';

@Injectable({ providedIn: 'root' })
export class CodeEditorSessionService extends DomainDependent implements ICodeEditorSessionService {
    constructor(protected localStorageService: LocalStorageService, domainService: DomainService) {
        super(domainService);
        this.initDomainSubscription();
    }

    storeSession = (session: { errors: { [fileName: string]: AnnotationArray }; timestamp: number }) => {
        const [domainType, domainValue] = this.domain;
        if (domainType !== DomainType.TEST_REPOSITORY) {
            this.localStorageService.store('sessions', JSON.stringify({ [domainValue.id]: session }));
        }
    };

    loadSession = () => {
        const [domainType, domainValue] = this.domain;
        if (domainType !== DomainType.TEST_REPOSITORY) {
            const sessions = JSON.parse(this.localStorageService.retrieve('sessions') || '{}');
            const session = sessions[domainValue.id];
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
        } else {
            return null;
        }
    };
}
