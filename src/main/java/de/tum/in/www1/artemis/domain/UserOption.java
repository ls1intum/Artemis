package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Individual User Options that make the User Settings
 */
@Entity
@Table(name = "user_option")
// @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserOption extends DomainObject {

    @Id
    @Column(name = "id")
    private long id;

    @Column(name = "option", nullable = false)
    private String option;

    @Column(name = "webapp", columnDefinition = "boolean default true", nullable = false)
    private boolean webapp = true;

    @Column(name = "email", columnDefinition = "boolean default false", nullable = false)
    private boolean email = false;

    @ManyToOne
    private User user;

    @Override
    public Long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getOption() {
        return option;
    }

    public void setOption(String option) {
        this.option = option;
    }

    public boolean isWebapp() {
        return webapp;
    }

    public void setWebapp(boolean webapp) {
        this.webapp = webapp;
    }

    public boolean isEmail() {
        return email;
    }

    public void setEmail(boolean email) {
        this.email = email;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public String toString() {
        return "UserOption{" + "id=" + id + ", option='" + option + '\'' + ", webapp=" + webapp + ", email=" + email + ", user=" + user + '}';
    }

    /*
     * @Id
     * @Column(name = "id") private long id; // @Column(name = "title", nullable = false)
     * @Column(name = "settings_category", nullable = false) private String settingsCategory;
     * @Column(name = "option_group", nullable = false) private String optionGroup;
     * @Column(name = "option_name", nullable = false) private String optionName;
     * @Column(name = "option_description", nullable = false) private String optionDescription;
     * @Column(name = "webapp", columnDefinition = "boolean default true", nullable = false) private boolean webapp = true;
     * @Column(name = "email", columnDefinition = "boolean default false", nullable = false) private boolean email = false;
     * @ManyToOne private User user; public User getUser() { return user; } public void setUser(User user) { this.user = user; } public String getOptionName() { return optionName;
     * } public void setOptionName(String optionName) { this.optionName = optionName; } public Long getId() { return id; } public void setOptionId(long id) { this.id = id; } public
     * String getSettingsCategory() { return settingsCategory; } public void setSettingsCategory(String settingsCategory) { this.settingsCategory = settingsCategory; } public
     * String getOptionGroup() { return optionGroup; } public void setOptionGroup(String optionGroup) { this.optionGroup = optionGroup; } public String getOptionDescription() {
     * return optionDescription; } public void setOptionDescription(String optionDescription) { this.optionDescription = optionDescription; } public boolean isWebapp() { return
     * webapp; } public void setWebapp(boolean webapp) { this.webapp = webapp; } public boolean isEmail() { return email; } public void setEmail(boolean email) { this.email =
     * email; }
     * @Override public String toString() { return "UserOption{" + "id=" + id + ", settingsCategory='" + settingsCategory + '\'' + ", optionGroup='" + optionGroup + '\'' +
     * ", optionName='" + optionName + '\'' + ", optionDescription='" + optionDescription + '\'' + ", webapp=" + webapp + ", email=" + email + ", user=" + user + '}'; }
     */
}
