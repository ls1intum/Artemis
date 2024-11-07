import { DomainDependentService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain-dependent.service';
import { HttpClient } from '@angular/common/http';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { DomainChange, DomainType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';

/**
 * Service that can be extended to update rest endpoint urls with the received domain information.
 */
export abstract class DomainDependentEndpointService extends DomainDependentService {
    protected restResourceUrl?: string;

    protected constructor(
        protected http: HttpClient,
        protected jhiWebsocketService: JhiWebsocketService,
        domainService: DomainService,
    ) {
        super(domainService);
        this.initDomainSubscription();
    }

    /**
     * Sets resourceUrls according to the parameter.
     * @param domain - enum that defines the type of the domain.
     */
    setDomain(domain: DomainChange) {
        super.setDomain(domain);
        this.restResourceUrl = this.calculateRestResourceURL(domain);
    }

    calculateRestResourceURL(domain: DomainChange): string | undefined {
        const [domainType, domainValue] = domain;
        switch (domainType) {
            case DomainType.PARTICIPATION:
                return `api/repository/${domainValue.id}`;
            case DomainType.TEST_REPOSITORY:
                return `api/test-repository/${domainValue.id}`;
            case DomainType.AUXILIARY_REPOSITORY:
                return `api/auxiliary-repository/${domainValue.id}`;
        }
    }
}
