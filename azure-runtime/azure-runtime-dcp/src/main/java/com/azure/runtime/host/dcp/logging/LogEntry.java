package com.azure.runtime.host.dcp.logging;

public class LogEntry {
    
    private String content;
    private boolean isErrorMessage;
    
    public LogEntry(String content, boolean isErrorMessage) {
        this.content = content;
        this.isErrorMessage = isErrorMessage;
    }
    
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isErrorMessage() {
        return isErrorMessage;
    }

    public void setErrorMessage(boolean errorMessage) {
        isErrorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "LogEntry{" +
            "content='" + content + '\'' +
            ", isErrorMessage=" + isErrorMessage +
            '}';
    }
}
