package com.azure.runtime.host.dcp.model.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class CustomResource<TSpec, TStatus> implements KubernetesObject, IAnnotationHolder {
    public static final String SERVICE_PRODUCER_ANNOTATION = "service-producer";
    public static final String SERVICE_CONSUMER_ANNOTATION = "service-consumer";
    public static final String ENDPOINT_NAME_ANNOTATION = "endpoint-name";
    public static final String RESOURCE_NAME_ANNOTATION = "resource-name";
    public static final String OTEL_SERVICE_NAME_ANNOTATION = "otel-service-name";
    public static final String OTEL_SERVICE_INSTANCE_ID_ANNOTATION = "otel-service-instance-id";
    public static final String RESOURCE_STATE_ANNOTATION = "resource-state";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @JsonProperty("metadata")
    private V1ObjectMeta metadata = new V1ObjectMeta();

    @JsonProperty("spec")
    private TSpec spec;

    @JsonProperty("status")
    private TStatus status;


    public CustomResource() {
    }

    public CustomResource(TSpec spec) {
        this.spec = spec;
    }

    public String getAppModelResourceName() {
        return metadata.getAnnotations() != null && metadata.getAnnotations().containsKey(RESOURCE_NAME_ANNOTATION) ? metadata.getAnnotations().get(RESOURCE_NAME_ANNOTATION) : null;
    }

    public String getAppModelInitialState() {
        return metadata.getAnnotations() != null && metadata.getAnnotations().containsKey(RESOURCE_STATE_ANNOTATION) ? metadata.getAnnotations().get(RESOURCE_STATE_ANNOTATION) : null;
    }

    public void annotate(String annotationName, String value) {
        if (metadata.getAnnotations() == null) {
            metadata.setAnnotations(new HashMap<>());
        }
        metadata.getAnnotations().put(annotationName, value);
    }

    public <TValue> void annotateAsObjectList(String annotationName, TValue value) {
        if (metadata.getAnnotations() == null) {
            metadata.setAnnotations(new HashMap<>());
        }
        annotateAsObjectList(metadata.getAnnotations(), annotationName, value);
    }

    public <TValue> boolean tryGetAnnotationAsObjectList(String annotationName, List<TValue> list) {
        return tryGetAnnotationAsObjectList(metadata.getAnnotations(), annotationName, list);
    }

    public static <TValue> boolean tryGetAnnotationAsObjectList(Map<String, String> annotations, String annotationName, List<TValue> list) {
        list.clear();
        if (annotations == null) {
            return false;
        }

        String annotationValue = annotations.get(annotationName);
        if (annotationValue == null || annotationValue.isBlank()) {
            return false;
        }

        try {
            list = new ObjectMapper().readValue(annotationValue, new TypeReference<List<TValue>>() {
            });
        } catch (Exception e) {
            return false;
        }

        return list != null;
    }

    public static <TValue> void annotateAsObjectList(Map<String, String> annotations, String annotationName, TValue value) {
        List<TValue> values;

        if (annotations.containsKey(annotationName) && annotations.get(annotationName) != null && !annotations.get(annotationName).isBlank()) {
            try {
                values = OBJECT_MAPPER.readValue(annotations.get(annotationName), new TypeReference<List<TValue>>() {
                });
            } catch (Exception e) {
                values = new ArrayList<>();
            }

            if (!values.contains(value)) {
                values.add(value);
            }
        } else {
            values = List.of(value);
        }

        String newAnnotationVal = null;
        try {
            newAnnotationVal = OBJECT_MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        annotations.put(annotationName, newAnnotationVal);
    }

    @Override
    public V1ObjectMeta getMetadata() {
        return metadata;
    }

    public void setMetadata(V1ObjectMeta metadata) {
        this.metadata = metadata;
    }

    public TSpec getSpec() {
        return spec;
    }

    public void setSpec(TSpec spec) {
        this.spec = spec;
    }

    public TStatus getStatus() {
        return status;
    }

    public void setStatus(TStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "CustomResource{" +
            "metadata=" + metadata +
            ", spec=" + spec +
            ", status=" + status +
            '}';
    }
}
