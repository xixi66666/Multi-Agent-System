package com.vibeagent.run;

class RunCancelledException extends RuntimeException {

    RunCancelledException() {
        super("Run was cancelled");
    }
}
