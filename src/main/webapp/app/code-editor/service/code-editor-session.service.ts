import { Injectable } from '@angular/core';
import { LocalStorageService } from 'ngx-webstorage';
import { compose, filter, fromPairs, map, toPairs } from 'lodash/fp';
import { AnnotationArray } from 'app/entities/ace-editor/annotation.model';
import { DomainService } from 'app/code-editor/service/code-editor-domain.service';
import { DomainType } from 'app/code-editor/model/code-editor.model';
import { DomainDependentService } from 'app/code-editor/service/code-editor-domain-dependent.service';

export interface ICodeEditorSessionService {
    storeSession: (session: { errors: { [fileName: string]: AnnotationArray }; timestamp: number }) => void;
    loadSession: () => { errors: { [fileName: string]: AnnotationArray }; timestamp: number } | null;
}

@Injectable({ providedIn: 'root' })
export class CodeEditorSessionService extends DomainDependentService implements ICodeEditorSessionService {
    constructor(protected localStorageService: LocalStorageService, domainService: DomainService) {
        super(domainService);
        this.initDomainSubscription();
    }

    storeSession = (session: { errors: { [fileName: string]: AnnotationArray }; timestamp: number }) => {
        const [domainType, domainValue] = this.domain;
        if (domainType === DomainType.PARTICIPATION) {
            this.localStorageService.store('sessions', JSON.stringify({ [domainValue.id]: session }));
        }
    };

    loadSession = () => {
        const [domainType, domainValue] = this.domain;
        if (domainType === DomainType.PARTICIPATION) {
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
