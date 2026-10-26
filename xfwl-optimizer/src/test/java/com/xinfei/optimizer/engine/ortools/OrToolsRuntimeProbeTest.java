package com.xinfei.optimizer.engine.ortools;

import com.xinfei.optimizer.OptimizerTestFixtures;
import com.xinfei.optimizer.config.OptimizerProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrToolsRuntimeProbeTest {

    @Test
    void shouldBeDisabledByDefaultAndNeverLoadNativeLibraries() {
        OptimizerProperties properties = OptimizerTestFixtures.properties();
        OrToolsRuntimeProbe probe = new OrToolsRuntimeProbe(properties);

        OrToolsRuntimeProbe.ProbeResult result = probe.probe();

        assertThat(probe.isEnabled()).isFalse();
        assertThat(result.status()).isEqualTo(OrToolsRuntimeProbe.ProbeStatus.DISABLED);
        assertThat(result.available()).isFalse();
    }
}
