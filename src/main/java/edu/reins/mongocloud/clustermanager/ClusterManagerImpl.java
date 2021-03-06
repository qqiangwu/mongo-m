package edu.reins.mongocloud.clustermanager;

import edu.reins.mongocloud.ClusterManager;
import edu.reins.mongocloud.Context;
import edu.reins.mongocloud.EventBus;
import edu.reins.mongocloud.Fsm;
import edu.reins.mongocloud.cluster.*;
import edu.reins.mongocloud.clustermanager.exception.ClusterIDConflictException;
import edu.reins.mongocloud.clustermanager.exception.ClusterNotFoundException;
import edu.reins.mongocloud.clustermanager.exception.OperationNotAllowedException;
import edu.reins.mongocloud.model.ClusterDefinition;
import edu.reins.mongocloud.model.ClusterID;
import edu.reins.mongocloud.support.annotation.Nothrow;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.annotation.ContextInsensitive;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import javax.annotation.PostConstruct;

@Component
@Slf4j
public class ClusterManagerImpl implements ClusterManager, Fsm<ClusterManagerState, ClusterManagerEvent> {
    @Autowired
    private EventBus eventBus;

    @Autowired
    private Context context;

    private final StateMachineImpl stateMachine;

    @Nothrow
    public ClusterManagerImpl() {
        val builder = StateMachineBuilderFactory.create(
                StateMachineImpl.class,
                ClusterManagerState.class,
                ClusterManagerEventType.class,
                Void.class);

        builder.transition()
                .from(ClusterManagerState.START).to(ClusterManagerState.RUNNING)
                .on(ClusterManagerEventType.SETUP);

        builder.transitions()
                .fromAmong(ClusterManagerState.values())
                .to(ClusterManagerState.CLOSED)
                .on(ClusterManagerEventType.FAILOVER);

        builder.transitions()
                .fromAmong(ClusterManagerState.values())
                .to(ClusterManagerState.CLOSED)
                .on(ClusterManagerEventType.DESTROYED);

        builder.transitions()
                .fromAmong(ClusterManagerState.values())
                .to(ClusterManagerState.CLOSED)
                .on(ClusterManagerEventType.CLOSE);

        stateMachine = builder.newStateMachine(ClusterManagerState.START);
        stateMachine.start();
    }

    @PostConstruct
    public synchronized void setup() {
        eventBus.register(ClusterManagerEvent.class, this);
    }

    @Nothrow
    @Override
    public synchronized void handle(final ClusterManagerEvent event) {
        stateMachine.fire(event.getType());
    }

    @Nothrow
    @Override
    public synchronized ClusterManagerState getState() {
        return stateMachine.getCurrentState();
    }

    @Nothrow
    @Override
    public synchronized boolean isInitialized() {
        return stateMachine.getCurrentState() == ClusterManagerState.RUNNING;
    }

    /**
     *
     * @throws ClusterIDConflictException
     * @throws IllegalStateException        if clusterManager is not initialized
     */
    @Override
    public synchronized void createCluster(final ClusterDefinition clusterDefinition)
            throws ClusterIDConflictException {
        ensureInitialized();

        val clusterID = new ClusterID(clusterDefinition.getId());
        val cluster = new ShardedCluster(clusterID, clusterDefinition, context);

        if (context.getClusters().putIfAbsent(clusterID, cluster) != null) {
            throw new ClusterIDConflictException(clusterID);
        }

        eventBus.post(new ClusterEvent(clusterID, ClusterEventType.INIT));
    }

    /**
     * @param clusterID
     *
     * @throws ClusterNotFoundException if the id is not found
     * @throws OperationNotAllowedException if the cluster is in bad state
     *
     * @throws IllegalStateException  if the clusterManager is not initialized
     */
    @Override
    public void scaleOut(final ClusterID clusterID) throws ClusterNotFoundException, OperationNotAllowedException {
        ensureInitialized();

        final Cluster cluster = getOrThrows(clusterID);

        ensureState(cluster, ClusterState.RUNNING);

        LOG.info("scaleOut(cluster: {})", cluster.getID());

        sendMessageToCluster(cluster, ClusterEventType.SCALE_OUT);
    }

    /**
     * @param clusterID
     *
     * @throws ClusterNotFoundException if the id is not found
     * @throws OperationNotAllowedException if the cluster is in bad state
     *
     * @throws IllegalStateException  if the clusterManager is not initialized
     */
    @Override
    public void scaleIn(final ClusterID clusterID) throws ClusterNotFoundException, OperationNotAllowedException {
        ensureInitialized();

        final Cluster cluster = getOrThrows(clusterID);

        ensureState(cluster, ClusterState.RUNNING);

        LOG.info("scaleIn(cluster: {})", cluster.getID());

        sendMessageToCluster(cluster, ClusterEventType.SCALE_IN);
    }

    private void ensureInitialized() {
        if (!isInitialized()) {
            throw new IllegalStateException("ClusterMananger is not initialized");
        }
    }

    private Cluster getOrThrows(final ClusterID clusterID) throws ClusterNotFoundException {
        final Cluster cluster = context.getClusters().get(clusterID);

        if (cluster == null) {
            throw new ClusterNotFoundException(cluster.getID().getValue());
        }

        return cluster;
    }

    private void ensureState(final Cluster cluster, final ClusterState state) throws OperationNotAllowedException {
        if (cluster.getState() != state) {
            throw new OperationNotAllowedException(String.format("clusterState: %s", cluster.getState()));
        }
    }

    private void sendMessageToCluster(final Cluster cluster, final ClusterEventType eventType) {
        eventBus.post(new ClusterEvent(cluster.getID(), eventType));
    }

    @ContextInsensitive
    private static class StateMachineImpl extends
            AbstractStateMachine<StateMachineImpl, ClusterManagerState, ClusterManagerEventType, Void> {
        @Nothrow
        protected void onSETUP(
                final ClusterManagerState from, final ClusterManagerState to, final ClusterManagerEventType event) {
            LOG.info("cluster starts to running");
        }

        @Nothrow
        protected void onFAILOVER(
                final ClusterManagerState from, final ClusterManagerState to, final ClusterManagerEventType event) {
            LOG.info("cluster is closed");

            shutdown();
        }

        @Nothrow
        protected void onDESTROYED(
                final ClusterManagerState from, final ClusterManagerState to, final ClusterManagerEventType event) {
            LOG.info("cluster is destroyed");

            shutdown();
        }

        @Nothrow
        protected void onCLOSE(
                final ClusterManagerState from, final ClusterManagerState to, final ClusterManagerEventType event) {
            LOG.info("cluster is closed");

            shutdown();
        }

        @Nothrow
        private void shutdown() {
            System.exit(0);
        }
    }
}
