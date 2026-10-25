package com.wineguard.simulation;

import com.wineguard.domain.DeviceCommand;
import com.wineguard.domain.Zone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UltraLightTest {

    @Test
    void encodesMeasurementLikeTheFirmware() {
        String ul = UltraLight.measurement(new Measurement(13.44, 66.0, 4.0, "none", "off", "off", 1234));
        assertThat(ul).isEqualTo("t|13.4|h|66.0|l|4.0|a|none|k|off|d|off|u|1234");
    }

    @Test
    void parsesCommandsWithAndWithoutParameter() {
        var c1 = UltraLight.parseCommand("wg002@ledOn|");
        assertThat(c1.deviceKey()).isEqualTo("wg002");
        assertThat(c1.name()).isEqualTo("ledOn");
        assertThat(c1.param()).isEmpty();
        assertThat(c1.ack("OK")).isEqualTo("wg002@ledOn|OK");

        var c2 = UltraLight.parseCommand("wg002@alertStop|temp");
        assertThat(c2.param()).isEqualTo("temp");

        var c3 = UltraLight.parseCommand("wg002@demo|temp:+8.5");
        assertThat(c3.param()).isEqualTo("temp:+8.5");
    }

    @Test
    void rejectsInvalidPayload() {
        assertThatThrownBy(() -> UltraLight.parseCommand("sem-arroba")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void virtualDeviceFollowsFirmwareCommandSemantics() {
        VirtualDevice vd = new VirtualDevice("wg002", Zone.RESERVE_CELLAR);
        vd.apply(DeviceCommand.ALERT_TEMP, "");
        vd.apply(DeviceCommand.ALERT_LUX, "");
        vd.apply(DeviceCommand.LED_ON, "");
        Measurement m = vd.step(5);
        assertThat(m.alertState()).isEqualTo("temp,lux");
        assertThat(m.ledState()).isEqualTo("on");

        vd.apply(DeviceCommand.ALERT_STOP, "temp");
        assertThat(vd.step(5).alertState()).isEqualTo("lux");
        vd.apply(DeviceCommand.ALERT_STOP, "all");
        vd.apply(DeviceCommand.LED_OFF, "");
        Measurement off = vd.step(5);
        assertThat(off.alertState()).isEqualTo("none");
        assertThat(off.ledState()).isEqualTo("off");
    }

    @Test
    void demoOffsetIsAppliedGradually() {
        VirtualDevice vd = new VirtualDevice("wg002", Zone.PREMIUM_CELLAR);
        double base = vd.step(5).temperature();
        vd.apply(DeviceCommand.DEMO, "temp:+9");
        double first = vd.step(5).temperature();
        double later = 0;
        for (int i = 0; i < 10; i++) later = vd.step(5).temperature();
        assertThat(first - base).isBetween(1.0, 6.0);       // rampa, não salto
        assertThat(later - base).isBetween(7.5, 10.5);      // atinge o alvo
        assertThat(vd.step(5).demoMode()).isEqualTo("temp:+9");
    }
}
