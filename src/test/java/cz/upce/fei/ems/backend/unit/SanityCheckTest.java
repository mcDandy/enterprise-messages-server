package cz.upce.fei.ems.backend.unit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SanityCheckTest {

    @Test
    void testExecutionWorks() {
        boolean testRunnerIsWorking = true;
        assertThat(testRunnerIsWorking).isTrue();
    }
}