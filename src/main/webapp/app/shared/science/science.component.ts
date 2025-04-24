import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';
import { Directive, inject } from '@angular/core';

@Directive()
export abstract class AbstractScienceComponent {
    scienceService = inject(ScienceService);

    protected constructor(
        private type?: ScienceEventType,
        private resourceId?: number,
    ) {}

    protected setScienceEventType(type: ScienceEventType) {
        this.type = type;
    }

    protected setResourceId(resourceId: number) {
        this.resourceId = resourceId;
    }

    protected logEvent() {
        if (this.type) {
            this.scienceService.logEvent(this.type, this.resourceId);
        }
    }
}
