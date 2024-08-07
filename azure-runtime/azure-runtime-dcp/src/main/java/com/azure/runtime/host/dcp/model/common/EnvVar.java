package com.azure.runtime.host.dcp.model.common;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class EnvVar {
    @JsonProperty("name")
    private String name;

    @JsonProperty("value")
    private String value;
    
    public EnvVar() {
    }
    
    public EnvVar(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EnvVar envVar = (EnvVar) o;
        return Objects.equals(name, envVar.name) && Objects.equals(value, envVar.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }
}
