package edu.reins.mongocloud.impl;

import edu.reins.mongocloud.ClusterManager;
import edu.reins.mongocloud.Context;
import edu.reins.mongocloud.EventBus;
import edu.reins.mongocloud.ResourceProvider;
import edu.reins.mongocloud.cluster.Cluster;
import edu.reins.mongocloud.instance.Instance;
import edu.reins.mongocloud.instance.InstanceType;
import edu.reins.mongocloud.model.ClusterID;
import edu.reins.mongocloud.model.InstanceDefinition;
import edu.reins.mongocloud.model.InstanceID;
import edu.reins.mongocloud.monitor.Monitor;
import edu.reins.mongocloud.support.annotation.Nothrow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ContextImpl implements Context {
    @Autowired
    private EventBus eventBus;

    @Autowired
    private ClusterManager clusterManager;

    @Autowired
    private ResourceProvider resourceProvider;

    @Autowired
    private Environment environment;

    @Autowired
    private Monitor monitor;

    private Map<InstanceID, Instance> instances = new ConcurrentHashMap<>();

    private Map<ClusterID, Cluster> clusters = new ConcurrentHashMap<>();

    @Nothrow
    @Override
    public EventBus getEventBus() {
        return eventBus;
    }

    @Nothrow
    @Override
    public Map<InstanceID, Instance> getInstances() {
        return instances;
    }

    @Nothrow
    @Override
    public Map<ClusterID, Cluster> getClusters() {
        return clusters;
    }

    @Nothrow
    @Override
    public ClusterManager getClusterManager() {
        return clusterManager;
    }

    @Nothrow
    @Override
    public ResourceProvider getResourceProvider() {
        return resourceProvider;
    }

    @Nothrow
    @Override
    public Environment getEnv() {
        return environment;
    }

    @Nothrow
    @Override
    public Monitor getMonitor() {
        return monitor;
    }
}