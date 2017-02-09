package reins.wuqq.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.val;
import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.state.ZooKeeperState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reins.wuqq.support.PersistedState;

import javax.annotation.Nonnull;
import java.io.IOException;

@Component
public class PersistedFrameworkDetail extends PersistedState<FrameworkDetail> {
    private static final String FRAMEWORK_CONFIGURATION_VAR = "frameworkConf";

    @Autowired
    private static ObjectMapper mapper;

    @Autowired
    public PersistedFrameworkDetail(final ZooKeeperState state) {
        super(FRAMEWORK_CONFIGURATION_VAR,
                state,
                () -> new FrameworkDetail(),
                input -> {
                    try {
                        return mapper.readValue(input, FrameworkDetail.class);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                input -> {
                    try {
                        return mapper.writeValueAsBytes(input);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public String getFrameworkId() {
        return get().getFrameworkId();
    }

    public void setFrameworkId(final @Nonnull FrameworkID id) {
        val v = get();

        v.setFrameworkId(id.getValue());

        setValue(v);
    }
}