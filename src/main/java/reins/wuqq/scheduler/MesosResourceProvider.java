package reins.wuqq.scheduler;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Protos.ContainerInfo.DockerInfo;
import org.apache.mesos.Protos.ContainerInfo.DockerInfo.Network;
import org.apache.mesos.Protos.Value.Scalar;
import org.apache.mesos.Protos.Value.Type;
import org.apache.mesos.SchedulerDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reins.wuqq.model.Instance;
import reins.wuqq.support.ClusterUtil;
import reins.wuqq.support.MesosUtil;
import reins.wuqq.support.ResourceDescriptor;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Arrays;
import java.util.List;

/**
 *
 * FIXME 目前，不考虑消息丢失与机器失效的情况。
 *
 */
@Component
@ThreadSafe
@Slf4j(topic = "ResourceProvider")
public class MesosResourceProvider extends AbstractMesosResourceProvider {
    @Autowired
    private PersistedSchedulerDetail schedulerTasks;

    @Override
    public synchronized Instance launch(@Nonnull final Instance instance) {
       schedulerTasks.addPendingTask(instance);
       return instance;
    }

    @Override
    public synchronized void kill(@Nonnull final TaskID taskID) {
        schedulerDriver.killTask(taskID);
    }

    @Override
    public synchronized void resourceOffers(@Nonnull final SchedulerDriver driver, @Nonnull final List<Offer> offers) {
        val pendingTasks = schedulerTasks.getPendingTasks();

        for (val offer: offers) {
            val launched = tryLaunch(offer, pendingTasks);

            if (!launched) {
                driver.declineOffer(offer.getId());
            }
        }

        if (hasLaunched(pendingTasks)) {
            schedulerTasks.setPendingTasks(pendingTasks);
        }
    }

    private boolean hasLaunched(final List<Instance> pendingTasks) {
        return (pendingTasks.size() != schedulerTasks.getPendingTasks().size());
    }

    private boolean tryLaunch(final Offer offer, final List<Instance> pendingTasks) {
        val iterator = pendingTasks.iterator();

        while (iterator.hasNext()) {
            val task = iterator.next();
            val launched = tryLaunchTaskOn(task, offer);

            if (launched) {
                iterator.remove();
                return true;
            }
        }

        return false;
    }

    private boolean tryLaunchTaskOn(final Instance task, final Offer offer) {
        val launchable = hasSufficientResource(offer.getResourcesList(), task);

        if (launchable) {
            launchOn(task, offer);
            return true;
        }

        return false;
    }

    private void launchOn(final Instance instance, final Offer offer) {
        log.debug("Launch(instance: {}, node: {})", instance.getId(), offer.getSlaveId().getValue());


        val taskInfo = buildTask(instance, offer);

        schedulerDriver.launchTasks(Arrays.asList(offer.getId()), Arrays.asList(taskInfo));
        resourceStatusListener.onNodeLaunched(instance);
    }

    private TaskInfo buildTask(final Instance instance, final Offer offer) {
        val dockerInfo = buildContainer(instance);
        val containerInfo = ContainerInfo.newBuilder().setDocker(dockerInfo);

        return TaskInfo.newBuilder()
                .setTaskId(MesosUtil.toID(instance.getId()))
                .setName(ClusterUtil.getTaskName(instance))
                .setSlaveId(offer.getSlaveId())
                .setContainer(containerInfo)
                .setCommand(NULL_COMMAND)
                .addResources(getMemRequirement(instance))
                .addResources(getCPURequirement(instance))
                // FIXME
                //.addResources(getDiskRequirement(instance))
                .build();
    }

    private static final CommandInfo NULL_COMMAND = CommandInfo.newBuilder()
            .setShell(false)
            .build();

    private Resource getDiskRequirement(final Instance instance) {
        return Resource.newBuilder().build();
    }

    private Resource getCPURequirement(final Instance instance) {
        return Resource.newBuilder()
                .setName("cpus")
                .setType(Type.SCALAR)
                .setScalar(Scalar.newBuilder().setValue(instance.getCpus()))
                .build();
    }

    private Resource getMemRequirement(final Instance instance) {
        return Resource.newBuilder()
                .setName("mem")
                .setType(Type.SCALAR)
                .setScalar(Scalar.newBuilder().setValue(instance.getMemory()))
                .build();
    }

    private DockerInfo buildContainer(final Instance instance) {
        return DockerInfo.newBuilder()
                .setImage(instance.getImage())
                .setNetwork(Network.BRIDGE)
                .build();
    }

    private boolean hasSufficientResource(final List<Resource> offeredResources, final Instance instance) {
        val offeredResourceDesc = new ResourceDescriptor(offeredResources);

        if (offeredResourceDesc.getCpus() < instance.getCpus()) {
            return false;
        }
        if (offeredResourceDesc.getMemory() < instance.getMemory()) {
            return false;
        }
        if (offeredResourceDesc.getDisk() < instance.getDisk()) {
            return false;
        }

        return true;
    }

    @Override
    protected synchronized void onNodeStarted(final @Nonnull TaskStatus status) {
        log.info("OnNodeStarted(taskId: {})", status.getTaskId().getValue());
    }

    @Override
    protected synchronized void onNodeLost(final @Nonnull TaskStatus status) {
        log.info("OnNodeLost(taskId: {})", status.getTaskId().getValue());
    }
}
