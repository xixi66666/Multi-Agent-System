package com.vibeagent.runtime;

import java.util.List;

public record ReviewOutcome(
        boolean approved,
        String summary,
        List<String> findings) {

    public ReviewOutcome {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
