package edu.reins.mongocloud.cluster;

import edu.reins.mongocloud.Context;
import edu.reins.mongocloud.EventBus;
import edu.reins.mongocloud.cluster.fsm.ClusterAction;
import edu.reins.mongocloud.cluster.fsm.ClusterStateMachine;
import edu.reins.mongocloud.cluster.mongo.MongoEvent;
import edu.reins.mongocloud.cluster.mongo.MongoEventType;
import edu.reins.mongocloud.cluster.mongo.RsDefinition;
import edu.reins.mongocloud.instance.Instance;
import edu.reins.mongocloud.instance.InstanceEvent;
import edu.reins.mongocloud.instance.InstanceEventType;
import edu.reins.mongocloud.instance.InstanceImpl;
import edu.reins.mongocloud.model.ClusterID;
import edu.reins.mongocloud.model.InstanceDefinition;
import edu.reins.mongocloud.model.InstanceID;
import edu.reins.mongocloud.support.annotation.Nothrow;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@ToString
public class ReplicaCluster implements Cluster {
    private static final String DATA_SERVER_DEFINITION = "instance.data.definition";

    private final ClusterID id;
    private final ClusterID parent;
    private final Context context;
    private final List<Instance> instances;
    private final ClusterStateMachine stateMachine;
    private Optional<RouterClusterMeta> routerClusterMeta = Optional.empty();

    @Nothrow
    public ReplicaCluster(final Cluster parent, final int idx, final Context context) {
        final InstanceDefinition dataServerDef = Clusters.loadDefinition(context.getEnv(), DATA_SERVER_DEFINITION);


        this.id = new ClusterID(String.format("%s::shard-%d", parent.getID().getValue(), idx));
        this.parent = parent.getID();
        this.context = context;
        this.stateMachine = buildStateMachine();

        final Map<String, String> env = Collections.singletonMap(Clusters.ENV_RS, getID().getValue());

        this.instances = IntStream.range(0, 3)
                .mapToObj(i -> new InstanceImpl(context, this, i, dataServerDef, env))
                .collect(Collectors.toList());
    }

    @Nothrow
    private ClusterStateMachine buildStateMachine() {
        val builder = ClusterStateMachine.create();

        // from NEW to INIT: launch all instances
        builder.transition()
                .from(ClusterState.NEW).to(ClusterState.SUBMITTED)
                .on(ClusterEventType.INIT)
                .perform(new OnInit());

        builder.transition()
                .from(ClusterState.SUBMITTED).to(ClusterState.SUBMITTED)
                .on(ClusterEventType.CHILD_RUNNING)
                .when(Conditions.instancesNotFullyRunning(instances))
                .perform(new OnChildReady());

        builder.transition()
                .from(ClusterState.SUBMITTED).to(ClusterState.INIT_RS)
                .on(ClusterEventType.CHILD_RUNNING)
                .when(Conditions.allInstancesRunning(instances))
                .perform(new OnChildrenRunning());

        // from INIT_RS: wait all instances to join the replica set
        builder.transition()
                .from(ClusterState.INIT_RS).to(ClusterState.RUNNING)
                .on(ClusterEventType.RS_INITED)
                .perform(new OnRsInited());

        builder.transition()
                .from(ClusterState.INIT_RS).to(ClusterState.FAILED)
                .on(ClusterEventType.FAIL)
                .perform(new OnFailed());

        // done
        return builder.newStateMachine(ClusterState.NEW, this, context);
    }

    @Nothrow
    @Override
    public ClusterID getID() {
        return id;
    }

    @Nothrow
    @Override
    public ClusterState getState() {
        return stateMachine.getCurrentState();
    }

    @Nothrow
    @Override
    public List<Instance> getInstances() {
        return instances;
    }

    @Nothrow
    @Override
    public void handle(final ClusterEvent event) {
        stateMachine.fire(event.getType(), event);
    }

    private final class OnInit extends ClusterAction {
        @Nothrow
        @Override
        protected void doExec(final ClusterEvent event) {
            LOG.info("onInit(cluster: {}): register instances and send INIT", getID());

            final EventBus eventBus = context.getEventBus();
            final Map<InstanceID, Instance> instanceContext = context.getInstances();

            routerClusterMeta = Optional.of(event.getPayload(RouterClusterMeta.class));

            instances.forEach(instance -> {
                instanceContext.put(instance.getID(), instance);
                eventBus.post(new InstanceEvent(InstanceEventType.INIT, instance.getID()));
            });
        }
    }

    private final class OnChildReady extends ClusterAction {
        @Nothrow
        @Override
        protected void doExec(final ClusterEvent event) {
            LOG.info("onChildReady(cluster: {}, child: {})", getID(), event.getPayload(InstanceID.class));
        }
    }

    private final class OnChildrenRunning extends ClusterAction {
        @Nothrow
        @Override
        protected void doExec(final ClusterEvent event) {
            LOG.info("onChildrenRunning(cluster: {}): launch finished, init rs", getID());

            final RsDefinition rs = RsDefinition.from(ReplicaCluster.this);

            context.getEventBus().post(new MongoEvent(MongoEventType.INIT_RS, getID(), rs));
        }
    }

    private final class OnRsInited extends ClusterAction {
        @Nothrow
        @Override
        protected void doExec(final ClusterEvent event) {
            LOG.info("onRsInited(cluster: {}): launch finished", getID());

            context.getEventBus()
                    .post(new ClusterEvent(parent, ClusterEventType.CHILD_RUNNING, getID()));
        }
    }

    // TODO     notify parent
    private final class OnFailed extends ClusterAction {
        @Nothrow
        @Override
        protected void doExec(final ClusterEvent event) {
            LOG.info("onFailed(id: {}, msg: {})", getID(), event.getPayload(String.class));
        }
    }
}