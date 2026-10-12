package com.myyak.service.adminAuthService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.config.AdminAccessProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class SesAdminMailSender implements AdminMailSender {
    private final AdminAccessProperties properties;

    @Override
    public void sendLoginCode(String code) {
        try (SesV2Client client = SesV2Client.builder().region(Region.of(properties.getRegion()))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(10)))
                .build()) {
            client.sendEmail(SendEmailRequest.builder()
                    .fromEmailAddress(properties.getSender())
                    .destination(Destination.builder().toAddresses(properties.getEmail()).build())
                    .content(EmailContent.builder().simple(Message.builder()
                            .subject(Content.builder().charset("UTF-8").data("마이약 관리자 로그인 인증 코드").build())
                            .body(Body.builder().text(Content.builder().charset("UTF-8")
                                    .data("인증 코드: " + code + "\n5분 안에 요청한 관리자 로그인 화면에 입력해주세요.\n직접 요청하지 않았다면 입력하지 마세요.")
                                    .build()).build()).build()).build()).build());
        } catch (RuntimeException e) {
            throw new GeneralException(ErrorStatus.ADMIN_UNAVAILABLE);
        }
    }
}
