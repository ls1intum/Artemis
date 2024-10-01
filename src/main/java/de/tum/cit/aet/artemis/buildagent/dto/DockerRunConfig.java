package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DockerRunConfig implements Serializable {

    private boolean isNetworkDisabled;

    private String dns;

    private String dnsSearch;

    private List<String> dnsOption;

    private List<String> env;

    private String hostname;

    private String ip;

    private String ipv6;

    private String user;

    public boolean isNetworkDisabled() {
        return isNetworkDisabled;
    }

    public void setNetworkDisabled(boolean networkDisabled) {
        isNetworkDisabled = networkDisabled;
    }

    public String getDns() {
        return dns;
    }

    public void setDns(String dns) {
        this.dns = dns;
    }

    public String getDnsSearch() {
        return dnsSearch;
    }

    public void setDnsSearch(String dnsSearch) {
        this.dnsSearch = dnsSearch;
    }

    public List<String> getDnsOption() {
        return dnsOption;
    }

    public void setDnsOption(List<String> dnsOption) {
        this.dnsOption = dnsOption;
    }

    public List<String> getEnv() {
        return env;
    }

    public void setEnv(List<String> env) {
        this.env = env;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getIpv6() {
        return ipv6;
    }

    public void setIpv6(String ipv6) {
        this.ipv6 = ipv6;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public enum AllowedDockerFlags {

        NETWORK("network"), DNS("dns"), DNS_SEARCH("dns-search"), DNS_OPTION("dns-option"), ENV("env"), HOSTNAME("hostname"), IP("ip"), IPV6("ipv6"), USER("user");

        private final String flag;

        AllowedDockerFlags(String flag) {
            this.flag = flag;
        }

        public String flag() {
            return flag;
        }

        private static final Set<String> ALLOWED_FLAGS = new HashSet<>();

        static {
            for (AllowedDockerFlags value : values()) {
                ALLOWED_FLAGS.add(value.flag());
            }
        }

        public static boolean isAllowed(String flag) {
            return ALLOWED_FLAGS.contains(flag);
        }
    }
}
