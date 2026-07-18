package com.myyak;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class MyyakServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MyyakServerApplication.class, args);
	}

	/**
	 * 리마인더 발송 시각과 날짜 경계 계산이 호스트 타임존 설정에 좌우되지 않도록
	 * 애플리케이션 기본 타임존을 KST로 고정
	 */
	@PostConstruct
	public void setDefaultTimeZone() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}

}
