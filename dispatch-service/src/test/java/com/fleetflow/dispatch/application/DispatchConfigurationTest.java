package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.domain.GeoCoordinate;
import com.fleetflow.dispatch.infrastructure.agent.GeminiDispatchAgent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import static org.junit.jupiter.api.Assertions.*;

class DispatchConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OperatingZones.class)
    @Import({GeminiDispatchAgent.class, BoundedDispatchAgent.class})
    static class Config { }

    @Test void realYamlBindsZoneNamesAndAgentBeansWithoutCredentials() {
        new ApplicationContextRunner().withUserConfiguration(Config.class)
                .withPropertyValues("dispatch.agent.api-key=")
                .withInitializer(context -> {
                    try {
                        new YamlPropertySourceLoader().load("dispatch", new ClassPathResource("application.yml"))
                                .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                    } catch (java.io.IOException e) { throw new IllegalStateException(e); }
                }).run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.getBean(OperatingZones.class).serves("ZONE_A", new GeoCoordinate(-34.4, 150.8)));
                    assertFalse(context.getBean(OperatingZones.class).serves("ZONE_B", new GeoCoordinate(-34.4, 150.8)));
                    assertNotNull(context.getBean(BoundedDispatchAgent.class));
                    assertTrue(context.getEnvironment().getProperty("spring.cloud.function.definition")
                            .contains("courierLoadChangedConsumer"));
                });
    }
}
