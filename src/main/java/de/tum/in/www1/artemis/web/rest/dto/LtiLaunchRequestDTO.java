package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Created by Josias Montag on 22.09.16.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LtiLaunchRequestDTO {

    // Method and variable names need to match the LTI standard

    private String lis_person_sourcedid;

    private String lis_person_name_given;

    private String lis_person_name_family;

    private String lis_person_name_full;

    private String lis_person_contact_email_primary;

    private String lis_outcome_service_url;

    private String lti_message_type;

    private String lti_version;

    private String ext_user_username;

    private String context_id;

    private String context_label;

    private String oauth_version;

    private String oauth_signature_method;

    private Long oauth_timestamp;

    private String roles;

    private String launch_presentation_locale;

    private String custom_component_display_name;

    private String launch_presentation_return_url;

    private String lis_result_sourcedid;

    private String resource_link_id;

    private String user_id;

    private String oauth_nonce;

    private String oauth_consumer_key;

    private String oauth_signature;

    private String tool_consumer_instance_name;

    private Boolean custom_require_existing_user = false;

    private Boolean custom_lookup_user_by_email = false;

    public LtiLaunchRequestDTO() {
    }

    public Boolean getCustom_require_existing_user() {
        return custom_require_existing_user;
    }

    public void setCustom_require_existing_user(Boolean custom_require_existing_user) {
        this.custom_require_existing_user = custom_require_existing_user;
    }

    public Boolean getCustom_lookup_user_by_email() {
        return custom_lookup_user_by_email;
    }

    public void setCustom_lookup_user_by_email(Boolean custom_lookup_user_by_email) {
        this.custom_lookup_user_by_email = custom_lookup_user_by_email;
    }

    public String getLis_person_sourcedid() {
        return lis_person_sourcedid;
    }

    public void setLis_person_sourcedid(String lis_person_sourcedid) {
        this.lis_person_sourcedid = lis_person_sourcedid;
    }

    public String getLis_person_name_given() {
        return lis_person_name_given;
    }

    public void setLis_person_name_given(String lis_person_name_given) {
        this.lis_person_name_given = lis_person_name_given;
    }

    public String getLis_person_name_family() {
        return lis_person_name_family;
    }

    public void setLis_person_name_family(String lis_person_name_family) {
        this.lis_person_name_family = lis_person_name_family;
    }

    public String getLis_person_name_full() {
        return lis_person_name_full;
    }

    public void setLis_person_name_full(String lis_person_name_full) {
        this.lis_person_name_full = lis_person_name_full;
    }

    public String getLis_person_contact_email_primary() {
        return lis_person_contact_email_primary;
    }

    public void setLis_person_contact_email_primary(String lis_person_contact_email_primary) {
        this.lis_person_contact_email_primary = lis_person_contact_email_primary;
    }

    public String getLis_outcome_service_url() {
        return lis_outcome_service_url;
    }

    public void setLis_outcome_service_url(String lis_outcome_service_url) {
        this.lis_outcome_service_url = lis_outcome_service_url;
    }

    public String getLti_message_type() {
        return lti_message_type;
    }

    public void setLti_message_type(String lti_message_type) {
        this.lti_message_type = lti_message_type;
    }

    public String getExt_user_username() {
        return ext_user_username;
    }

    public void setExt_user_username(String ext_user_username) {
        this.ext_user_username = ext_user_username;
    }

    public String getLti_version() {
        return lti_version;
    }

    public void setLti_version(String lti_version) {
        this.lti_version = lti_version;
    }

    public String getContext_id() {
        return context_id;
    }

    public void setContext_id(String context_id) {
        this.context_id = context_id;
    }

    public String getOauth_version() {
        return oauth_version;
    }

    public void setOauth_version(String oauth_version) {
        this.oauth_version = oauth_version;
    }

    public String getOauth_signature_method() {
        return oauth_signature_method;
    }

    public void setOauth_signature_method(String oauth_signature_method) {
        this.oauth_signature_method = oauth_signature_method;
    }

    public Long getOauth_timestamp() {
        return oauth_timestamp;
    }

    public void setOauth_timestamp(Long oauth_timestamp) {
        this.oauth_timestamp = oauth_timestamp;
    }

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public String getLaunch_presentation_locale() {
        return launch_presentation_locale;
    }

    public void setLaunch_presentation_locale(String launch_presentation_locale) {
        this.launch_presentation_locale = launch_presentation_locale;
    }

    public String getCustom_component_display_name() {
        return custom_component_display_name;
    }

    public void setCustom_component_display_name(String custom_component_display_name) {
        this.custom_component_display_name = custom_component_display_name;
    }

    public String getLaunch_presentation_return_url() {
        return launch_presentation_return_url;
    }

    public void setLaunch_presentation_return_url(String launch_presentation_return_url) {
        this.launch_presentation_return_url = launch_presentation_return_url;
    }

    public String getLis_result_sourcedid() {
        return lis_result_sourcedid;
    }

    public void setLis_result_sourcedid(String lis_result_sourcedid) {
        this.lis_result_sourcedid = lis_result_sourcedid;
    }

    public String getResource_link_id() {
        return resource_link_id;
    }

    public void setResource_link_id(String resource_link_id) {
        this.resource_link_id = resource_link_id;
    }

    public String getUser_id() {
        return user_id;
    }

    public void setUser_id(String user_id) {
        this.user_id = user_id;
    }

    public String getOauth_nonce() {
        return oauth_nonce;
    }

    public void setOauth_nonce(String oauth_nonce) {
        this.oauth_nonce = oauth_nonce;
    }

    public String getOauth_consumer_key() {
        return oauth_consumer_key;
    }

    public void setOauth_consumer_key(String oauth_consumer_key) {
        this.oauth_consumer_key = oauth_consumer_key;
    }

    public String getOauth_signature() {
        return oauth_signature;
    }

    public void setOauth_signature(String oauth_signature) {
        this.oauth_signature = oauth_signature;
    }

    public String getTool_consumer_instance_name() {
        return tool_consumer_instance_name;
    }

    public void setTool_consumer_instance_name(String tool_consumer_instance_name) {
        this.tool_consumer_instance_name = tool_consumer_instance_name;
    }

    public String getContext_label() {
        return context_label;
    }

    public void setContext_label(String context_label) {
        this.context_label = context_label;
    }

    @Override
    public String toString() {
        return "LtiLaunchRequest{" + "lis_person_sourcedid='" + lis_person_sourcedid + '\'' + "lis_person_name_given='" + lis_person_name_given + '\'' + "lis_person_name_family='"
                + lis_person_name_family + '\'' + "lis_person_name_full='" + lis_person_name_full + '\'' + ", lis_person_contact_email_primary='" + lis_person_contact_email_primary
                + '\'' + ", lis_outcome_service_url='" + lis_outcome_service_url + '\'' + ", lti_message_type='" + lti_message_type + '\'' + ", lti_version='" + lti_version + '\''
                + ", context_id='" + context_id + '\'' + ", context_label='" + context_label + '\'' + ", oauth_version='" + oauth_version + '\'' + ", oauth_signature_method='"
                + oauth_signature_method + '\'' + ", oauth_timestamp=" + oauth_timestamp + ", roles='" + roles + '\'' + ", launch_presentation_locale='"
                + launch_presentation_locale + '\'' + ", custom_component_display_name='" + custom_component_display_name + '\'' + ", launch_presentation_return_url='"
                + launch_presentation_return_url + '\'' + ", lis_result_sourcedid='" + lis_result_sourcedid + '\'' + ", resource_link_id='" + resource_link_id + '\''
                + ", user_id='" + user_id + '\'' + ", oauth_nonce=" + oauth_nonce + ", oauth_consumer_key='" + oauth_consumer_key + '\'' + ", oauth_signature='" + oauth_signature
                + ", custom_require_existing_user=" + custom_require_existing_user + ", custom_lookup_user_by_email='" + custom_lookup_user_by_email + '\'' + '}';
    }
}
