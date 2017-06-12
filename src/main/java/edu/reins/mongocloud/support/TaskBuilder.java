package edu.reins.mongocloud.support;

import edu.reins.mongocloud.model.InstanceDefinition;
import edu.reins.mongocloud.model.InstanceLaunchRequest;
import lombok.val;
import org.apache.mesos.Protos;

import java.util.Arrays;
import java.util.Objects;

public class TaskBuilder {
    private String volume;
    private Protos.Offer offer;
    private InstanceLaunchRequest instanceRequest;
    private InstanceDefinition definition;

    public TaskBuilder setDockerVolume(final String volume) {
        this.volume = volume;

        return this;
    }

    public TaskBuilder setOffer(final Protos.Offer offer) {
        this.offer = offer;

        return this;
    }

    public TaskBuilder setInstanceRequest(final InstanceLaunchRequest instanceRequest) {
        this.instanceRequest = instanceRequest;
        this.definition = instanceRequest.getDefinition();

        return this;
    }

    public Protos.TaskInfo build() {
        Objects.requireNonNull(volume);
        Objects.requireNonNull(offer);
        Objects.requireNonNull(instanceRequest);

        return buildTask();
    }

    private Protos.TaskInfo buildTask() {
        val portMapping = preparePortMapping();
        val containerInfo = buildContainer(portMapping);
        val commandInfo = buildCommand();

        return Protos.TaskInfo.newBuilder()
                .setTaskId(Instances.toTaskID(instanceRequest.getInstanceID()))
                .setName(Instances.toTaskName(instanceRequest.getInstanceID()))
                .setSlaveId(offer.getSlaveId())
                .setContainer(containerInfo)
                .setCommand(commandInfo)
                .addResources(getMemRequirement())
                .addResources(getCPURequirement())
                .addResources(getDiskRequirement())
                .addResources(getPortMapping(portMapping))
                .build();
    }

    private Protos.ContainerInfo.DockerInfo.PortMapping preparePortMapping() {
        val containerPort = getContainerPort();
        val hostPort = new ResourceDescriptor(offer.getResourcesList()).getPorts().get(0);

        return Protos.ContainerInfo.DockerInfo.PortMapping.newBuilder()
                .setContainerPort(containerPort)
                .setHostPort(hostPort)
                .setProtocol("tcp")
                .build();
    }

    private int getContainerPort() {
        switch (instanceRequest.getDefinition().getType()) {
            case CONFIG: return 27019;
            case ROUTER: return 27017;
            case DATA: return 27018;
        }

        throw new AssertionError("Bad instanceRequest type");
    }

    private Protos.CommandInfo buildCommand() {
        val args = instanceRequest.getDefinition().getArgs().split(" ");

        return Protos.CommandInfo.newBuilder()
                .setShell(false)
                .addAllArguments(Arrays.asList(args))
                .build();
    }

    private Protos.Resource getDiskRequirement() {
        return Protos.Resource.newBuilder()
                .setName("disk")
                .setType(Protos.Value.Type.SCALAR)
                .setScalar(Protos.Value.Scalar.newBuilder().setValue(definition.getDisk()))
                .build();
    }

    private Protos.Resource getCPURequirement() {
        return Protos.Resource.newBuilder()
                .setName("cpus")
                .setType(Protos.Value.Type.SCALAR)
                .setScalar(Protos.Value.Scalar.newBuilder().setValue(definition.getCpus()))
                .build();
    }

    private Protos.Resource getMemRequirement() {
        return Protos.Resource.newBuilder()
                .setName("mem")
                .setType(Protos.Value.Type.SCALAR)
                .setScalar(Protos.Value.Scalar.newBuilder().setValue(definition.getMemory()))
                .build();
    }

    private Protos.Resource getPortMapping(final Protos.ContainerInfo.DockerInfo.PortMapping mapping) {
        val port = mapping.getHostPort();
        val range = Protos.Value.Ranges.newBuilder()
                .addRange(Protos.Value.Range.newBuilder().setBegin(port).setEnd(port));

        return Protos.Resource.newBuilder()
                .setName("ports")
                .setType(Protos.Value.Type.RANGES)
                .setRanges(range)
                .build();
    }

    private Protos.ContainerInfo.Builder buildContainer(final Protos.ContainerInfo.DockerInfo.PortMapping portMapping) {
        val docker = Protos.ContainerInfo.DockerInfo.newBuilder()
                .setImage(definition.getImage())
                .setNetwork(Protos.ContainerInfo.DockerInfo.Network.BRIDGE)
                .setForcePullImage(false)
                .addPortMappings(portMapping)
                .build();

        val dockerVolume = Protos.Volume.newBuilder()
                .setContainerPath(volume)
                .setMode(Protos.Volume.Mode.RW)
                .build();

        return Protos.ContainerInfo.newBuilder()
                .setType(Protos.ContainerInfo.Type.DOCKER)
                .addVolumes(dockerVolume)
                .setDocker(docker);
    }
}
