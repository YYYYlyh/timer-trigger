package com.example.timertrigger;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MainModeLogicTest {
    @Test
    void mode1UsesSingleEpcOverride() throws Exception {
        Config config = new Config();
        config.mode = 1;
        config.singleEpc = "EPC-ONLY";
        config.devicePort = 0;
        config.epcList = List.of("A", "B");

        Main main = new Main();
        Method resolveStep = Main.class.getDeclaredMethod("resolveStep", Config.class, int.class);
        resolveStep.setAccessible(true);
        ScheduleStep step = (ScheduleStep) resolveStep.invoke(main, config, 0);

        assertEquals(List.of("EPC-ONLY"), step.epcList);
    }

    @Test
    void mode1UsesIndexFromList() throws Exception {
        Config config = new Config();
        config.mode = 1;
        config.singleEpcIndex = 1;
        config.devicePort = 0;
        config.epcList = List.of("EPC-A", "EPC-B");

        Main main = new Main();
        Method resolveStep = Main.class.getDeclaredMethod("resolveStep", Config.class, int.class);
        resolveStep.setAccessible(true);
        ScheduleStep step = (ScheduleStep) resolveStep.invoke(main, config, 0);

        assertEquals(List.of("EPC-B"), step.epcList);
    }

    @Test
    void mode2RotatesEpcList() throws Exception {
        Config config = new Config();
        config.mode = 2;
        config.devicePort = 0;
        config.epcList = List.of("EPC-1", "EPC-2", "EPC-3");

        Main main = new Main();
        Method resolveStep = Main.class.getDeclaredMethod("resolveStep", Config.class, int.class);
        resolveStep.setAccessible(true);

        ScheduleStep step0 = (ScheduleStep) resolveStep.invoke(main, config, 0);
        ScheduleStep step1 = (ScheduleStep) resolveStep.invoke(main, config, 1);
        ScheduleStep step2 = (ScheduleStep) resolveStep.invoke(main, config, 2);

        assertEquals(List.of("EPC-1"), step0.epcList);
        assertEquals(List.of("EPC-2"), step1.epcList);
        assertEquals(List.of("EPC-3"), step2.epcList);
    }

    @Test
    void mode3UsesAllEpcs() throws Exception {
        Config config = new Config();
        config.mode = 3;
        config.devicePort = 0;
        config.epcList = List.of("EPC-1", "EPC-2");

        Main main = new Main();
        Method resolveStep = Main.class.getDeclaredMethod("resolveStep", Config.class, int.class);
        resolveStep.setAccessible(true);

        ScheduleStep step = (ScheduleStep) resolveStep.invoke(main, config, 0);
        assertEquals(List.of("EPC-1", "EPC-2"), step.epcList);
    }

    @Test
    void mode4RequiresScheduleSteps() throws Exception {
        Config config = new Config();
        config.mode = 4;
        config.devicePort = 0;

        Main main = new Main();
        Method validateScheduleSteps = Main.class.getDeclaredMethod("validateScheduleSteps", Config.class);
        validateScheduleSteps.setAccessible(true);

        Exception ex = assertThrows(Exception.class, () -> validateScheduleSteps.invoke(main, config));
        Throwable cause = ex.getCause();
        assertEquals("mode 4 requires scheduleSteps in config", cause.getMessage());
    }
}
