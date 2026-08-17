package com.vibeagent.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "vibe.models")
public class AgentModelsProperties {

    private boolean enabled;
    private Map<String, Provider> providers = new LinkedHashMap<>();
    private Map<String, Route> routes = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Provider> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, Provider> providers) {
        this.providers = providers;
    }

    public Map<String, Route> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, Route> routes) {
        this.routes = routes;
    }

    public static class Provider {

        private String baseUrl;
        private String apiKey;
        private String model;
        private Duration timeout = Duration.ofSeconds(90);
        private BigDecimal inputCostPerMillion = BigDecimal.ZERO;
        private BigDecimal outputCostPerMillion = BigDecimal.ZERO;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public BigDecimal getInputCostPerMillion() {
            return inputCostPerMillion;
        }

        public void setInputCostPerMillion(BigDecimal inputCostPerMillion) {
            this.inputCostPerMillion = inputCostPerMillion;
        }

        public BigDecimal getOutputCostPerMillion() {
            return outputCostPerMillion;
        }

        public void setOutputCostPerMillion(BigDecimal outputCostPerMillion) {
            this.outputCostPerMillion = outputCostPerMillion;
        }
    }

    public static class Route {

        private String primary;
        private String fallback;

        public String getPrimary() {
            return primary;
        }

        public void setPrimary(String primary) {
            this.primary = primary;
        }

        public String getFallback() {
            return fallback;
        }

        public void setFallback(String fallback) {
            this.fallback = fallback;
        }
    }
}
