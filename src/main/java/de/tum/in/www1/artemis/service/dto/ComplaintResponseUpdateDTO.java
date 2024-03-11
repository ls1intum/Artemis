package de.tum.in.www1.artemis.service.dto;

public class ComplaintResponseUpdateDTO {

    private String responseText;

    private Boolean complaintIsAccepted;

    private Action action;

    public String getResponseText() {
        return responseText;
    }

    public void setResponseText(String responseText) {
        this.responseText = responseText;
    }

    public Boolean getComplaintIsAccepted() {
        return complaintIsAccepted;
    }

    public void setComplaintIsAccepted(boolean complaintIsAccepted) {
        this.complaintIsAccepted = complaintIsAccepted;
    }

    public Action getAction() {
        return this.action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

}
