package de.tum.in.www1.artemis.service.dto;

import de.tum.in.www1.artemis.domain.Complaint;

public class ComplaintResponseUpdateDTO {

    private Long id;

    private String responseText;

    private Complaint complaint;

    private Action action;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getResponseText() {
        return responseText;
    }

    public void setResponseText(String responseText) {
        this.responseText = responseText;
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public void setComplaint(Complaint complaint) {
        this.complaint = complaint;
    }

    public Action getAction() {
        return this.action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

}
