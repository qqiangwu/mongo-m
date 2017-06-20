package edu.reins.mongocloud.instance;

import edu.reins.mongocloud.Fsm;
import edu.reins.mongocloud.model.InstanceDefinition;
import edu.reins.mongocloud.model.InstanceID;

public interface Instance extends Fsm<InstanceState, InstanceEvent>{
    InstanceID getID();
    int getLocalID();
    InstanceState getState();
    InstanceType getType();
    InstanceDefinition getDefinition();
    InstanceHost getHost();
}
