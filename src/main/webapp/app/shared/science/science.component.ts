import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';

export abstract class AbstractScienceComponent {
    protected constructor(
        protected scienceService: ScienceService,
        private type: ScienceEventType,
        private resourceId?: number,
    ) {}

    protected setResourceId(resourceId: number) {
        this.resourceId = resourceId;
    }

    protected logEvent() {
        if (this.scienceService.eventLoggingActive())
            if (this.resourceId) {
                this.scienceService.logEvent(this.type, this.resourceId).subscribe();
            } else {
                this.scienceService.logEvent(this.type).subscribe();
            }
    }
}
