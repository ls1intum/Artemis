package de.tum.cit.aet.artemis.web.rest.errors;

import java.io.Serial;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import de.tum.cit.aet.artemis.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.web.rest.ComplaintResponseResource;

public class ComplaintResponseLockedException extends BadRequestAlertException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String ERROR_KEY = "complaintLock";

    public ComplaintResponseLockedException(ComplaintResponse complaintResponse) {
        super(ErrorConstants.COMPLAINT_LOCKED, "Complaint is locked", ComplaintResponseResource.ENTITY_NAME, ERROR_KEY, getParameters(complaintResponse));
    }

    private static Map<String, Object> getParameters(ComplaintResponse complaintResponse) {
        Map<String, Object> params = new HashMap<>();
        params.put("user", complaintResponse.getReviewer().getLogin());
        params.put("lockEnd", complaintResponse.lockEndDate().toLocalDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm")));
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("skipAlert", true);
        parameters.put("message", "artemisApp.errors." + ERROR_KEY);
        parameters.put("params", params);
        return parameters;
    }
}
