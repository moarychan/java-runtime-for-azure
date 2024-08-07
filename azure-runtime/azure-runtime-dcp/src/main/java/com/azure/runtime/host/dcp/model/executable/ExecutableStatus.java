package com.azure.runtime.host.dcp.model.executable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.azure.runtime.host.dcp.model.common.EnvVar;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Represents the status of an executable, extending the Kubernetes V1Status.
 */
public class ExecutableStatus /*extends V1Status*/ {
    /**
     * The execution ID is the identifier for the actual-state counterpart of the Executable.
     * For ExecutionType == Process it is the process ID, for ExecutionType == IDE it is the IDE session ID.
     */
    @JsonProperty("executionID")
    private String executionID;

    /**
     * Process ID of the executable when ExecutionType is Process.
     */
    @JsonProperty("pid")
    private int processId;

    /**
     * The current state of the process/IDE session started for this executable.
     */
    @JsonProperty("state")
    private String state = ExecutableState.UNKNOWN.getState();

    /**
     * Start (attempt) timestamp.
     */
    @JsonProperty("startupTimestamp")
    private OffsetDateTime startupTimestamp;

    /**
     * The time when the executable finished execution.
     */
    @JsonProperty("finishTimestamp")
    private OffsetDateTime finishTimestamp;

    /**
     * Exit code of the process associated with the Executable.
     */
    @JsonProperty("exitCode")
    private Integer exitCode;

    /**
     * The path of a temporary file that contains captured standard output data from the Executable process.
     */
    @JsonProperty("stdOutFile")
    private String stdOutFile;

    /**
     * The path of a temporary file that contains captured standard error data from the Executable process.
     */
    @JsonProperty("stdErrFile")
    private String stdErrFile;

    /**
     * Effective values of environment variables, after all substitutions have been applied.
     */
    @JsonProperty("effectiveEnv")
    private List<EnvVar> effectiveEnv;

    /**
     * Effective values of launch arguments to be passed to the Executable, after all substitutions are applied.
     */
    @JsonProperty("effectiveArgs")
    private List<String> effectiveArgs;

    public String getExecutionID() {
        return executionID;
    }

    public void setExecutionID(String executionID) {
        this.executionID = executionID;
    }

    public int getProcessId() {
        return processId;
    }

    public void setProcessId(int processId) {
        this.processId = processId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public OffsetDateTime getStartupTimestamp() {
        return startupTimestamp;
    }

    public void setStartupTimestamp(OffsetDateTime startupTimestamp) {
        this.startupTimestamp = startupTimestamp;
    }

    public OffsetDateTime getFinishTimestamp() {
        return finishTimestamp;
    }

    public void setFinishTimestamp(OffsetDateTime finishTimestamp) {
        this.finishTimestamp = finishTimestamp;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public String getStdOutFile() {
        return stdOutFile;
    }

    public void setStdOutFile(String stdOutFile) {
        this.stdOutFile = stdOutFile;
    }

    public String getStdErrFile() {
        return stdErrFile;
    }

    public void setStdErrFile(String stdErrFile) {
        this.stdErrFile = stdErrFile;
    }

    public List<EnvVar> getEffectiveEnv() {
        return effectiveEnv;
    }

    public void setEffectiveEnv(List<EnvVar> effectiveEnv) {
        this.effectiveEnv = effectiveEnv;
    }

    public List<String> getEffectiveArgs() {
        return effectiveArgs;
    }

    public void setEffectiveArgs(List<String> effectiveArgs) {
        this.effectiveArgs = effectiveArgs;
    }

    @Override
    public String toString() {
        return "ExecutableStatus{" +
            "executionID='" + executionID + '\'' +
            ", processId=" + processId +
            ", state='" + state + '\'' +
            ", startupTimestamp=" + startupTimestamp +
            ", finishTimestamp=" + finishTimestamp +
            ", exitCode=" + exitCode +
            ", stdOutFile='" + stdOutFile + '\'' +
            ", stdErrFile='" + stdErrFile + '\'' +
            ", effectiveEnv=" + effectiveEnv +
            ", effectiveArgs=" + effectiveArgs +
            '}';
    }
}
