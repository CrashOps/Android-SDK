package com.crashops.sdk.configuration;

/**
 * Created by Perry on 13/03/2018.
 * This class helps to represent each configurable resource from the XML.
 */
public class ConfigurableResource {

    public enum ResourceType {
        Unknown, Integer, Color, Boolean, Dimension
    }

    private ResourceType resourceType;
    private int resourceId;
    private String resourceName;

    public ConfigurableResource(int resourceId, String resourceName, ResourceType type) {
        this.resourceId = resourceId;
        this.resourceName = resourceName;
        if (type == null) type = ResourceType.Unknown;
        this.resourceType = type;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public String getResourceName() {
        return resourceName;
    }

    public int getResourceId() {
        return resourceId;
    }

    @Override
    public String toString() {
        return resourceName.toLowerCase();
    }
}