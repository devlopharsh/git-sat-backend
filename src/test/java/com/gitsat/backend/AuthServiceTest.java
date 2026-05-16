package com.gitsat.backend;

import com.gitsat.backend.auth.AppUser;
import com.gitsat.backend.auth.AppUserService;
import com.gitsat.backend.auth.AuthService;
import com.gitsat.backend.auth.AuthenticatedUser;
import com.gitsat.backend.auth.DeviceCode;
import com.gitsat.backend.auth.DeviceCodeRepository;
import com.gitsat.backend.auth.DeviceCodeStatus;
import com.gitsat.backend.auth.JwtUtil;
import com.gitsat.backend.dto.DeviceActivationResponse;
import com.gitsat.backend.dto.DeviceCodeResponse;
import com.gitsat.backend.dto.DeviceVerificationResponse;
import com.gitsat.backend.dto.RefreshTokenResponse;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private DeviceCodeRepository deviceCodeRepository;

    @Mock
    private AppUserService appUserService;

    @Mock
    private JwtUtil jwtUtil;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                deviceCodeRepository,
                appUserService,
                jwtUtil,
                "https://example.com/device?source=test",
                600,
                5
        );
    }

    @Test
    void createDeviceCodePersistsPendingCodeAndBuildsVerificationUrl() {
        when(deviceCodeRepository.existsByDeviceCodeHash(anyString())).thenReturn(false);
        when(deviceCodeRepository.existsByUserCode(anyString())).thenReturn(false);
        when(deviceCodeRepository.save(any(DeviceCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceCodeResponse response = authService.createDeviceCode();

        assertThat(response.deviceCode()).isNotBlank();
        assertThat(response.userCode()).matches("[A-Z2-9]{8}");
        assertThat(response.verificationUrl())
                .isEqualTo("https://example.com/device?source=test&user_code=" + response.userCode());
        assertThat(response.expiresIn()).isEqualTo(600);
        assertThat(response.interval()).isEqualTo(5);

        ArgumentCaptor<DeviceCode> savedCode = ArgumentCaptor.forClass(DeviceCode.class);
        verify(deviceCodeRepository).save(savedCode.capture());
        assertThat(savedCode.getValue().status()).isEqualTo(DeviceCodeStatus.PENDING);
        assertThat(savedCode.getValue().userCode()).isEqualTo(response.userCode());
        assertThat(savedCode.getValue().deviceCodeHash()).isNotBlank();
    }

    @Test
    void activateDeviceNormalizesUserCodeAndApprovesCurrentUser() {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser("user-1", "user@example.com", "Test User");
        AppUser currentUser = new AppUser(
                "user-1",
                "user@example.com",
                "Test User",
                "hash",
                Instant.now(),
                Instant.now()
        );
        DeviceCode pendingCode = new DeviceCode(
                "device-1",
                "hash-1",
                "ABCD1234",
                DeviceCodeStatus.PENDING,
                null,
                Instant.now().plusSeconds(300),
                Instant.now(),
                null,
                5
        );

        when(appUserService.findUserById("user-1")).thenReturn(Optional.of(currentUser));
        when(deviceCodeRepository.findByUserCode("ABCD1234")).thenReturn(Optional.of(pendingCode));
        when(deviceCodeRepository.save(any(DeviceCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceActivationResponse response = authService.activateDevice("abcd-1234", authenticatedUser);

        assertThat(response.message()).isEqualTo("Device approved.");

        ArgumentCaptor<DeviceCode> savedCode = ArgumentCaptor.forClass(DeviceCode.class);
        verify(deviceCodeRepository).save(savedCode.capture());
        assertThat(savedCode.getValue().status()).isEqualTo(DeviceCodeStatus.APPROVED);
        assertThat(savedCode.getValue().userId()).isEqualTo("user-1");
    }

    @Test
    void verifyDeviceCodeReturnsPendingForUnapprovedDevice() {
        DeviceCode pendingCode = new DeviceCode(
                "device-1",
                "hash-1",
                "ABCD1234",
                DeviceCodeStatus.PENDING,
                null,
                Instant.now().plusSeconds(300),
                Instant.now(),
                null,
                5
        );

        when(deviceCodeRepository.findByDeviceCodeHash(anyString())).thenReturn(Optional.of(pendingCode));
        when(deviceCodeRepository.save(any(DeviceCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceVerificationResponse response = authService.verifyDeviceCode("raw-device-code");

        assertThat(response.error()).isEqualTo("authorization_pending");
        assertThat(response.accessToken()).isNull();
        assertThat(response.refreshToken()).isNull();

        ArgumentCaptor<DeviceCode> savedCode = ArgumentCaptor.forClass(DeviceCode.class);
        verify(deviceCodeRepository).save(savedCode.capture());
        assertThat(savedCode.getValue().lastPolledAt()).isNotNull();
        assertThat(savedCode.getValue().status()).isEqualTo(DeviceCodeStatus.PENDING);
    }

    @Test
    void verifyDeviceCodeReturnsTokensAndMarksApprovedCodeCompleted() {
        DeviceCode approvedCode = new DeviceCode(
                "device-1",
                "hash-1",
                "ABCD1234",
                DeviceCodeStatus.APPROVED,
                "user-1",
                Instant.now().plusSeconds(300),
                Instant.now(),
                Instant.now().minusSeconds(30),
                5
        );
        AppUser user = new AppUser(
                "user-1",
                "user@example.com",
                "Test User",
                "hash",
                Instant.now(),
                Instant.now()
        );

        when(deviceCodeRepository.findByDeviceCodeHash(anyString())).thenReturn(Optional.of(approvedCode));
        when(appUserService.findUserById("user-1")).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(any(AuthenticatedUser.class))).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(any(AuthenticatedUser.class))).thenReturn("refresh-token");
        when(jwtUtil.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(deviceCodeRepository.save(any(DeviceCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceVerificationResponse response = authService.verifyDeviceCode("raw-device-code");

        assertThat(response.error()).isNull();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.expiresIn()).isEqualTo(900L);

        ArgumentCaptor<DeviceCode> savedCode = ArgumentCaptor.forClass(DeviceCode.class);
        verify(deviceCodeRepository).save(savedCode.capture());
        assertThat(savedCode.getValue().status()).isEqualTo(DeviceCodeStatus.COMPLETED);
    }

    @Test
    void refreshAccessTokenRejectsInvalidRefreshToken() {
        when(jwtUtil.parseRefreshToken("bad-token")).thenReturn(null);

        assertThatThrownBy(() -> authService.refreshAccessToken("bad-token"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");

        verify(appUserService, never()).findAuthenticatedUserById(anyString());
    }

    @Test
    void refreshAccessTokenIssuesNewAccessTokenForKnownUser() {
        AuthenticatedUser refreshUser = new AuthenticatedUser("user-1", "user@example.com", "Test User");
        when(jwtUtil.parseRefreshToken("refresh-token")).thenReturn(refreshUser);
        when(appUserService.findAuthenticatedUserById("user-1")).thenReturn(Optional.of(refreshUser));
        when(jwtUtil.generateAccessToken(refreshUser)).thenReturn("new-access-token");
        when(jwtUtil.getAccessTokenTtlSeconds()).thenReturn(900L);

        RefreshTokenResponse response = authService.refreshAccessToken("refresh-token");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.expiresIn()).isEqualTo(900L);
    }
}
