package pt.futapp.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AppConfig {

    public static final ZoneId ZONA = ZoneId.of("Europe/Lisbon");

    @Bean
    public Clock clock() {
        return Clock.system(ZONA);
    }
}
