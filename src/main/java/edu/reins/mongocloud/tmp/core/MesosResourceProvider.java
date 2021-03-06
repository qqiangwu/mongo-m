package edu.reins.mongocloud.tmp.core;

import edu.reins.mongocloud.instance.Instance;
import edu.reins.mongocloud.model.InstanceID;
import edu.reins.mongocloud.model.InstanceLaunchRequest;
import edu.reins.mongocloud.tmp.EventBus;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;

import java.util.List;

public class MesosResourceProvider implements ResourceProvider, Scheduler {
    private EventBus eventBus;

    @Override
    public void launch(InstanceLaunchRequest request) {

    }

    @Override
    public void kill(InstanceID instanceID) {

    }

    @Override
    public void sync(List<Instance> instances) {

    }

    @Override
    public void registered(SchedulerDriver schedulerDriver, Protos.FrameworkID frameworkID, Protos.MasterInfo masterInfo) {

    }

    @Override
    public void reregistered(SchedulerDriver schedulerDriver, Protos.MasterInfo masterInfo) {

    }

    @Override
    public void resourceOffers(SchedulerDriver schedulerDriver, List<Protos.Offer> list) {

    }

    @Override
    public void offerRescinded(SchedulerDriver schedulerDriver, Protos.OfferID offerID) {

    }

    @Override
    public void statusUpdate(SchedulerDriver schedulerDriver, Protos.TaskStatus taskStatus) {

    }

    @Override
    public void frameworkMessage(SchedulerDriver schedulerDriver, Protos.ExecutorID executorID, Protos.SlaveID slaveID, byte[] bytes) {

    }

    @Override
    public void disconnected(SchedulerDriver schedulerDriver) {

    }

    @Override
    public void slaveLost(SchedulerDriver schedulerDriver, Protos.SlaveID slaveID) {

    }

    @Override
    public void executorLost(SchedulerDriver schedulerDriver, Protos.ExecutorID executorID, Protos.SlaveID slaveID, int i) {

    }

    @Override
    public void error(SchedulerDriver schedulerDriver, String s) {

    }
}
