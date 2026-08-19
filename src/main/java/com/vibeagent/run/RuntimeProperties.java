package com.vibeagent.run;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "vibe.runtime")
public class RuntimeProperties {

    private Duration maxRuntime = Duration.ofMinutes(60);
    private long maxTotalTokens = 1_000_000L;
    private int maxRepairRounds = 3;
    private int maxToolTurns = 30;

    public Duration getMaxRuntime() {
        return maxRuntime;
    }

    public void setMaxRuntime(Duration maxRuntime) {
        this.maxRuntime = maxRuntime;
    }

    public long getMaxTotalTokens() {
        return maxTotalTokens;
    }

    public void setMaxTotalTokens(long maxTotalTokens) {
        this.maxTotalTokens = maxTotalTokens;
    }

    public int getMaxRepairRounds() {
        return maxRepairRounds;
    }

    public void setMaxRepairRounds(int maxRepairRounds) {
        this.maxRepairRounds = maxRepairRounds;
    }

    public int getMaxToolTurns() {
        return maxToolTurns;
    }

    public void setMaxToolTurns(int maxToolTurns) {
        this.maxToolTurns = maxToolTurns;
    }
}
