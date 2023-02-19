package de.tum.in.www1.artemis.domain.exam;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.AbstractAuditingEntity;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

@Entity
@Table(name = "exam_session")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamSession extends AbstractAuditingEntity {

    @ManyToOne
    @JoinColumn(name = "student_exam_id")
    private StudentExam studentExam;

    @Column(name = "session_token")
    private String sessionToken;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "browser_fingerprint_hash")
    private String browserFingerprintHash;

    @Column(name = "instance_id")
    private String instanceId;

    @Column(name = "ip_address")
    private String ipAddress;

    @Transient
    private boolean isInitialSessionTransient;

    public StudentExam getStudentExam() {
        return studentExam;
    }

    public void setStudentExam(StudentExam studentExam) {
        this.studentExam = studentExam;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getBrowserFingerprintHash() {
        return browserFingerprintHash;
    }

    public void setBrowserFingerprintHash(String browserFingerprintHash) {
        this.browserFingerprintHash = browserFingerprintHash;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    @JsonIgnore
    public IPAddress getIpAddressAsIpAddress() {
        return ipAddress != null ? new IPAddressString(ipAddress).getAddress() : null;
    }

    public void setIpAddressFromIpAddress(IPAddress ipAddress) {
        this.ipAddress = ipAddress != null ? ipAddress.toCanonicalString() : null;

    }

    public boolean isInitialSession() {
        return isInitialSessionTransient;
    }

    public void setInitialSession(boolean isInitialSessionTransient) {
        this.isInitialSessionTransient = isInitialSessionTransient;
    }

    public void hideDetails() {
        setUserAgent(null);
        setBrowserFingerprintHash(null);
        setInstanceId(null);
        setIpAddress(null);
    }
}
