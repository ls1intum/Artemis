package de.tum.cit.aet.artemis.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.cors.CorsConfiguration;

/**
 * Inlined replacement for {@code tech.jhipster.config.JHipsterProperties}.
 * <p>
 * Uses the {@code jhipster} prefix to maintain backward compatibility with
 * existing YAML configuration files.
 */
@ConfigurationProperties(prefix = "jhipster", ignoreUnknownFields = true)
public class ArtemisProperties {

    private final Http http = new Http();

    private final Mail mail = new Mail();

    private final Security security = new Security();

    private final Logging logging = new Logging();

    private final Cache cache = new Cache();

    private final ClientApp clientApp = new ClientApp();

    private final Registry registry = new Registry();

    private CorsConfiguration cors = new CorsConfiguration();

    public Http getHttp() {
        return http;
    }

    public Mail getMail() {
        return mail;
    }

    public Security getSecurity() {
        return security;
    }

    public Logging getLogging() {
        return logging;
    }

    public Cache getCache() {
        return cache;
    }

    public ClientApp getClientApp() {
        return clientApp;
    }

    public Registry getRegistry() {
        return registry;
    }

    public CorsConfiguration getCors() {
        return cors;
    }

    public void setCors(CorsConfiguration cors) {
        this.cors = cors;
    }

    // ---- Nested configuration classes ----

    public static class Http {

        private final Cache cache = new Cache();

        public Cache getCache() {
            return cache;
        }

        public static class Cache {

            private int timeToLiveInDays = 1461; // 4 years (default from JHipster)

            public int getTimeToLiveInDays() {
                return timeToLiveInDays;
            }

            public void setTimeToLiveInDays(int timeToLiveInDays) {
                this.timeToLiveInDays = timeToLiveInDays;
            }
        }
    }

    public static class Mail {

        private boolean enabled = false;

        private String from = "";

        private String baseUrl = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Security {

        private final Authentication authentication = new Authentication();

        public Authentication getAuthentication() {
            return authentication;
        }

        public static class Authentication {

            private final Jwt jwt = new Jwt();

            public Jwt getJwt() {
                return jwt;
            }

            public static class Jwt {

                private String secret = "";

                private String base64Secret = "";

                private long tokenValidityInSeconds = 1800; // 30 minutes

                private long tokenValidityInSecondsForRememberMe = 2592000; // 30 days

                public String getSecret() {
                    return secret;
                }

                public void setSecret(String secret) {
                    this.secret = secret;
                }

                public String getBase64Secret() {
                    return base64Secret;
                }

                public void setBase64Secret(String base64Secret) {
                    this.base64Secret = base64Secret;
                }

                public long getTokenValidityInSeconds() {
                    return tokenValidityInSeconds;
                }

                public void setTokenValidityInSeconds(long tokenValidityInSeconds) {
                    this.tokenValidityInSeconds = tokenValidityInSeconds;
                }

                public long getTokenValidityInSecondsForRememberMe() {
                    return tokenValidityInSecondsForRememberMe;
                }

                public void setTokenValidityInSecondsForRememberMe(long tokenValidityInSecondsForRememberMe) {
                    this.tokenValidityInSecondsForRememberMe = tokenValidityInSecondsForRememberMe;
                }
            }
        }
    }

    public static class Logging {

        private boolean useJsonFormat = false;

        private final Logstash logstash = new Logstash();

        public boolean isUseJsonFormat() {
            return useJsonFormat;
        }

        public void setUseJsonFormat(boolean useJsonFormat) {
            this.useJsonFormat = useJsonFormat;
        }

        public Logstash getLogstash() {
            return logstash;
        }

        public static class Logstash {

            private boolean enabled = false;

            private String host = "localhost";

            private int port = 5000;

            private int ringBufferSize = 512;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getHost() {
                return host;
            }

            public void setHost(String host) {
                this.host = host;
            }

            public int getPort() {
                return port;
            }

            public void setPort(int port) {
                this.port = port;
            }

            public int getRingBufferSize() {
                return ringBufferSize;
            }

            public void setRingBufferSize(int ringBufferSize) {
                this.ringBufferSize = ringBufferSize;
            }
        }
    }

    public static class Cache {

        private final Hazelcast hazelcast = new Hazelcast();

        public Hazelcast getHazelcast() {
            return hazelcast;
        }

        public static class Hazelcast {

            private int timeToLiveSeconds = 3600;

            private int backupCount = 1;

            public int getTimeToLiveSeconds() {
                return timeToLiveSeconds;
            }

            public void setTimeToLiveSeconds(int timeToLiveSeconds) {
                this.timeToLiveSeconds = timeToLiveSeconds;
            }

            public int getBackupCount() {
                return backupCount;
            }

            public void setBackupCount(int backupCount) {
                this.backupCount = backupCount;
            }
        }
    }

    public static class ClientApp {

        private String name = "";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Registry {

        private String password = "";

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
