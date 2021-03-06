package edu.reins.mongocloud.mongo.request;

import edu.reins.mongocloud.model.ClusterID;
import lombok.Value;

@Value
public final class JoinRequest {
    private final ClusterID cluster;
    private final ClusterID router;
    private final ClusterID participant;
}
