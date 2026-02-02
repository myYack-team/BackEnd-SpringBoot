package com.myyak.converter;

import com.myyak.domain.FamilyLink;
import com.myyak.domain.FamilyLinkRequest;
import com.myyak.domain.User;
import com.myyak.web.dto.FamilyDTO.FamilyResponseDTO;

public class FamilyConverter {

    /**
     * FamilyLink -> LinkedFamily DTO 변환 (보호자 관점)
     * @param link 가족 연동 엔티티
     * @return 연동된 피보호자 정보
     */
    public static FamilyResponseDTO.LinkedFamily toLinkedFamily(FamilyLink link) {
        User protectedUser = link.getProtectedUser();
        return FamilyResponseDTO.LinkedFamily.builder()
                .linkId(link.getId())
                .userId(protectedUser.getId())
                .name(protectedUser.getName())
                .profileImage(protectedUser.getProfileImage())
                .phone(maskPhone(protectedUser.getPhone()))
                .linkedAt(link.getCreatedAt())
                .isGuardian(true)
                .build();
    }

    /**
     * FamilyLinkRequest -> PendingRequest DTO 변환 (받은 요청용)
     * @param request 가족 연동 요청 엔티티
     * @return 대기 중인 요청 정보 (요청자 정보)
     */
    public static FamilyResponseDTO.PendingRequest toReceivedPendingRequest(FamilyLinkRequest request) {
        User requester = request.getRequester();
        return FamilyResponseDTO.PendingRequest.builder()
                .requestId(request.getId())
                .userId(requester.getId())
                .name(requester.getName())
                .profileImage(requester.getProfileImage())
                .phone(maskPhone(requester.getPhone()))
                .requestedAt(request.getCreatedAt())
                .build();
    }

    /**
     * FamilyLinkRequest -> PendingRequest DTO 변환 (보낸 요청용)
     * @param request 가족 연동 요청 엔티티
     * @return 대기 중인 요청 정보 (대상자 정보)
     */
    public static FamilyResponseDTO.PendingRequest toSentPendingRequest(FamilyLinkRequest request) {
        User target = request.getTarget();
        return FamilyResponseDTO.PendingRequest.builder()
                .requestId(request.getId())
                .userId(target.getId())
                .name(target.getName())
                .profileImage(target.getProfileImage())
                .phone(maskPhone(target.getPhone()))
                .requestedAt(request.getCreatedAt())
                .build();
    }

    /**
     * FamilyLink -> Guardian DTO 변환 (피보호자 관점)
     * @param link 가족 연동 엔티티
     * @return 보호자 정보
     */
    public static FamilyResponseDTO.Guardian toGuardian(FamilyLink link) {
        User guardian = link.getGuardian();
        return FamilyResponseDTO.Guardian.builder()
                .linkId(link.getId())
                .userId(guardian.getId())
                .name(guardian.getName())
                .profileImage(guardian.getProfileImage())
                .linkedAt(link.getCreatedAt())
                .build();
    }

    /**
     * 전화번호 마스킹
     * 예: 01012345678 -> 010-xxxx-5678
     * @param phone 원본 전화번호
     * @return 마스킹된 전화번호
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 10) {
            return null;
        }

        String prefix;
        String suffix;

        if (phone.length() == 11) {
            // 01012345678 형태
            prefix = phone.substring(0, 3);
            suffix = phone.substring(7);
        } else if (phone.length() == 10) {
            // 0101234567 형태 (구형 번호)
            prefix = phone.substring(0, 3);
            suffix = phone.substring(6);
        } else {
            return phone;
        }

        return prefix + "-xxxx-" + suffix;
    }
}
