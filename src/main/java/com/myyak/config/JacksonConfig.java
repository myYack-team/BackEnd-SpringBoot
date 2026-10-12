package com.myyak.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Configuration
public class JacksonConfig {

    /**
     * 요청 본문의 LocalDateTime 역직렬화를 시간대 인식형으로 교체
     * 기본 역직렬화기는 "2026-09-10T01:26:13.599Z"처럼 오프셋이 붙은 값에서 'Z'만 잘라내고
     * UTC 시각을 로컬 시각으로 그대로 받아들여, KST보다 9시간 이른 값이 저장되는 문제가 있었음
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer zoneAwareLocalDateTimeCustomizer() {
        return builder -> builder.deserializerByType(LocalDateTime.class, new ZoneAwareLocalDateTimeDeserializer());
    }

    /**
     * 오프셋(Z, +09:00 등)이 포함된 시각은 애플리케이션 기본 시간대로 변환하고,
     * 오프셋이 없는 시각은 기본 역직렬화기에 그대로 위임
     */
    public static class ZoneAwareLocalDateTimeDeserializer extends LocalDateTimeDeserializer {

        public ZoneAwareLocalDateTimeDeserializer() {
            super(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.hasToken(JsonToken.VALUE_STRING)) {
                LocalDateTime converted = toSystemZone(parser.getText().trim());
                if (converted != null) {
                    return converted;
                }
            }
            return super.deserialize(parser, context);
        }

        private LocalDateTime toSystemZone(String text) {
            try {
                // ZoneId.systemDefault()는 MyyakServerApplication에서 고정한 Asia/Seoul을 따름
                return OffsetDateTime.parse(text)
                        .atZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDateTime();
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }
}
