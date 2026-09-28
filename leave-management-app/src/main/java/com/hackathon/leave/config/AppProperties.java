package com.hackathon.leave.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo-tunable knobs. Both values are read from application.properties:
 *   app.escalation.timeout-seconds
 *   app.conflict.max-simultaneous
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Escalation escalation = new Escalation();
    private final Conflict conflict = new Conflict();

    public Escalation getEscalation() {
        return escalation;
    }

    public Conflict getConflict() {
        return conflict;
    }

    public static class Escalation {
        private long timeoutSeconds = 60;

        public long getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Conflict {
        private int maxSimultaneous = 2;

        public int getMaxSimultaneous() {
            return maxSimultaneous;
        }

        public void setMaxSimultaneous(int maxSimultaneous) {
            this.maxSimultaneous = maxSimultaneous;
        }
    }
}
