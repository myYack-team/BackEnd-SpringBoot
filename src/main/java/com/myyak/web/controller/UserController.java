package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.service.userService.UserService;
import com.myyak.web.dto.UserDTO.UserRequestDTO;
import com.myyak.web.dto.UserDTO.UserResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "사용자 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 정보를 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<UserResponseDTO.UserInfo> getMyInfo(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(userService.getMyInfo(userId));
    }

    @Operation(summary = "내 정보 수정", description = "현재 로그인한 사용자의 정보를 수정합니다.")
    @PatchMapping("/me")
    public ApiResponse<UserResponseDTO.UpdateResult> updateMyInfo(
            Authentication authentication,
            @RequestBody UserRequestDTO.UpdateRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(userService.updateMyInfo(userId, request));
    }

    @Operation(summary = "회원 탈퇴", description = "현재 로그인한 사용자의 계정과 모든 관련 데이터를 삭제합니다.")
    @DeleteMapping("/me")
    public ApiResponse<Void> deleteMe(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        userService.deleteMe(userId);
        return ApiResponse.onSuccess(null);
    }
}
