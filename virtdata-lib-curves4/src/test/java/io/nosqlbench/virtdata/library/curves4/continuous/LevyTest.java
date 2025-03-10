package io.nosqlbench.virtdata.library.curves4.continuous;

import io.nosqlbench.virtdata.library.curves4.continuous.long_double.Levy;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LevyTest {

    @Test
    public void testLevy() {
        Levy levy = new Levy(2.3d, 1.0d);
        assertThat(levy.applyAsDouble(10L)).isCloseTo(2.9379325000660304, Offset.offset(0.000001d));
    }

}
