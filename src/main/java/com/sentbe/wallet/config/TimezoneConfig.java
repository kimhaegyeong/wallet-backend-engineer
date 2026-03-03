package com.sentbe.wallet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * 타임존 설정 클래스
 */
@Configuration
public class TimezoneConfig {

    @Value("${app.timezone.display:Asia/Seoul}")
    private String displayTimezone;

    /**
     * Jackson 라이브러리가 응답 데이터를 직렬화할 때 사용할 타임존을 설정합니다.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonObjectMapperBuilderCustomizer() {
        return builder -> builder.timeZone(TimeZone.getTimeZone(displayTimezone));
    }

    /**
     * 서비스 레이어에서 사용할 디스플레이 타임존 ZoneId 빈을 생성합니다.
     */
    @Bean
    public ZoneId displayZoneId() {
        return ZoneId.of(displayTimezone);
    }
}
