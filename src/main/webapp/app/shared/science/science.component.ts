import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';

export abstract class AbstractScienceComponent {
    protected constructor(
        protected scienceService: ScienceService,
        private type: ScienceEventType,
    ) {
        scienceService.logEvent(type).subscribe();
    }
}
