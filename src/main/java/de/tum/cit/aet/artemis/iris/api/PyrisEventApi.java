package de.tum.cit.aet.artemis.iris.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisEventService;
import de.tum.cit.aet.artemis.iris.service.pyris.UnsupportedPyrisEventException;
import de.tum.cit.aet.artemis.iris.service.pyris.event.CompetencyJolSetEvent;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.iris.service.pyris.event.PyrisEvent;
import de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;

@Conditional(IrisEnabled.class)
@Controller
@Lazy
public class PyrisEventApi extends AbstractIrisApi {

    private final PyrisEventService pyrisEventService;

    public PyrisEventApi(PyrisEventService pyrisEventService) {
        this.pyrisEventService = pyrisEventService;
    }

    /**
     * Triggers a Pyris action based on the received {@link PyrisEvent}.
     * This method processes the event and delegates the handling to the appropriate service.
     * <p>
     * Note: It's possible that no action is triggered if the event does not fulfill all requirements.
     * See {@link IrisCourseChatSessionService#handleCompetencyJolSetEvent(CompetencyJolSetEvent)} and
     * {@link IrisExerciseChatSessionService#handleNewResultEvent(NewResultEvent)} for more details on the specific
     * actions taken for each event type.
     *
     * @param event The event object received to trigger the matching action
     * @throws UnsupportedPyrisEventException if the event is not supported
     *
     * @see PyrisEvent
     */
    public void trigger(PyrisEvent<?> event) {
        pyrisEventService.trigger(event);
    }
}
