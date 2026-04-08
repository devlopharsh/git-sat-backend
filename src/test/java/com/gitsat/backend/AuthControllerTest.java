package com.gitsat.backend;

import com.gitsat.backend.auth.AppUser;
import com.gitsat.backend.auth.AppUserRepository;
import com.gitsat.backend.auth.DeviceCode;
import com.gitsat.backend.auth.DeviceCodeRepository;
import com.gitsat.backend.dto.ActivateDeviceRequest;
import com.gitsat.backend.dto.AuthResponse;
import com.gitsat.backend.dto.DeviceActivationResponse;
import com.gitsat.backend.dto.DeviceCodeResponse;
import com.gitsat.backend.dto.DeviceVerificationRequest;
import com.gitsat.backend.dto.DeviceVerificationResponse;
import com.gitsat.backend.dto.LoginRequest;
import com.gitsat.backend.dto.RefreshTokenRequest;
import com.gitsat.backend.dto.RefreshTokenResponse;
import com.gitsat.backend.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration")
class AuthControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @MockBean
    private AppUserRepository appUserRepository;

    @MockBean
    private DeviceCodeRepository deviceCodeRepository;

    private Map<String, AppUser> usersById;
    private Map<String, AppUser> usersByEmail;
    private Map<String, DeviceCode> deviceCodesById;
    private Map<String, DeviceCode> deviceCodesByHash;
    private Map<String, DeviceCode> deviceCodesByUserCode;

    @BeforeEach
    void setUpRepository() {
        usersById = new ConcurrentHashMap<>();
        usersByEmail = new ConcurrentHashMap<>();
        deviceCodesById = new ConcurrentHashMap<>();
        deviceCodesByHash = new ConcurrentHashMap<>();
        deviceCodesByUserCode = new ConcurrentHashMap<>();

        when(appUserRepository.findByEmail(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(usersByEmail.get(invocation.getArgument(0))));

        when(appUserRepository.findById(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(usersById.get(invocation.getArgument(0))));

        when(appUserRepository.save(any(AppUser.class)))
                .thenAnswer(invocation -> {
                    AppUser user = invocation.getArgument(0);
                    AppUser persistedUser = user.id() == null
                            ? new AppUser(
                            UUID.randomUUID().toString(),
                            user.email(),
                            user.name(),
                            user.passwordHash(),
                            user.createdAt(),
                            user.lastLoginAt()
                    )
                            : user;
                    usersById.put(persistedUser.id(), persistedUser);
                    usersByEmail.put(persistedUser.email(), persistedUser);
                    return persistedUser;
                });

        when(deviceCodeRepository.existsByDeviceCodeHash(anyString()))
                .thenAnswer(invocation -> deviceCodesByHash.containsKey(invocation.getArgument(0)));

        when(deviceCodeRepository.existsByUserCode(anyString()))
                .thenAnswer(invocation -> deviceCodesByUserCode.containsKey(invocation.getArgument(0)));

        when(deviceCodeRepository.findByDeviceCodeHash(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(deviceCodesByHash.get(invocation.getArgument(0))));

        when(deviceCodeRepository.findByUserCode(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(deviceCodesByUserCode.get(invocation.getArgument(0))));

        when(deviceCodeRepository.save(any(DeviceCode.class)))
                .thenAnswer(invocation -> {
                    DeviceCode deviceCode = invocation.getArgument(0);
                    DeviceCode persistedDeviceCode = deviceCode.id() == null
                            ? new DeviceCode(
                            UUID.randomUUID().toString(),
                            deviceCode.deviceCodeHash(),
                            deviceCode.userCode(),
                            deviceCode.status(),
                            deviceCode.userId(),
                            deviceCode.expiresAt(),
                            deviceCode.createdAt(),
                            deviceCode.lastPolledAt(),
                            deviceCode.intervalSeconds()
                    )
                            : deviceCode;
                    upsertDeviceCode(persistedDeviceCode);
                    return persistedDeviceCode;
                });

        when(deviceCodeRepository.deleteByExpiresAtBefore(any(Instant.class)))
                .thenAnswer(invocation -> {
                    Instant cutoff = invocation.getArgument(0);
                    long removed = deviceCodesById.values().removeIf(code -> code.expiresAt().isBefore(cutoff)) ? 1L : 0L;
                    deviceCodesByHash.values().removeIf(code -> code.expiresAt().isBefore(cutoff));
                    deviceCodesByUserCode.values().removeIf(code -> code.expiresAt().isBefore(cutoff));
                    return removed;
                });
    }

    @Test
    void registerAndLoginIssueGitSatCookie() {
        RegisterRequest registerRequest = new RegisterRequest(
                "Test User",
                "tester@example.com",
                "password123"
        );

        ResponseEntity<AuthResponse> registerResponse =
                restTemplate.postForEntity("/auth/register", registerRequest, AuthResponse.class);

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("git-sat=")
                .contains("HttpOnly");
        assertThat(registerResponse.getBody()).isNotNull();
        assertThat(registerResponse.getBody().email()).isEqualTo("tester@example.com");

        HttpHeaders meHeaders = new HttpHeaders();
        meHeaders.add(HttpHeaders.COOKIE, extractCookie(registerResponse));
        ResponseEntity<AuthResponse> meResponse = restTemplate.exchange(
                "/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(meHeaders),
                AuthResponse.class
        );

        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResponse.getBody()).isNotNull();
        assertThat(meResponse.getBody().email()).isEqualTo("tester@example.com");

        LoginRequest loginRequest = new LoginRequest("tester@example.com", "password123");
        ResponseEntity<AuthResponse> loginResponse =
                restTemplate.postForEntity("/auth/login", loginRequest, AuthResponse.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("git-sat=")
                .contains("HttpOnly");
    }

    @Test
    void deviceCodeReturnsVerificationUrlPointingToDevice() {
        ResponseEntity<DeviceCodeResponse> response =
                restTemplate.postForEntity("/auth/device-code", null, DeviceCodeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().verificationUrl())
                .endsWith("/device?user_code=" + response.getBody().userCode());
    }

    @Test
    void loginPageLoadsAndFormLoginRedirectsWithCookie() {
        RegisterRequest registerRequest = new RegisterRequest(
                "Page User",
                "page@example.com",
                "password123"
        );
        restTemplate.postForEntity("/auth/register", registerRequest, AuthResponse.class);

        ResponseEntity<String> pageResponse = restTemplate.getForEntity("/login", String.class);

        assertThat(pageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pageResponse.getBody()).contains("<form action=\"/auth/login-form\" method=\"post\">");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("email", "page@example.com");
        form.add("password", "password123");

        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "/auth/login-form",
                new HttpEntity<>(form, headers),
                String.class
        );

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(loginResponse.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("/login?status=success");
        assertThat(loginResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("git-sat=")
                .contains("HttpOnly");
    }

    @Test
    void failedLoginFormRedirectsToSignupWithEmailAndNext() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("email", "new-user@example.com");
        form.add("password", "password123");
        form.add("next", "/device?user_code=ABCD1234");

        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "/auth/login-form",
                new HttpEntity<>(form, headers),
                String.class
        );

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(loginResponse.getHeaders().getFirst(HttpHeaders.LOCATION))
                .contains("/signup?status=info")
                .contains("email=new-user%40example.com")
                .contains("next=%2Fdevice%3Fuser_code%3DABCD1234");
    }

    @Test
    void signupPageLoadsAndFormSignupRedirectsWithCookie() {
        ResponseEntity<String> pageResponse = restTemplate.getForEntity("/signup", String.class);

        assertThat(pageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pageResponse.getBody()).contains("<form action=\"/auth/signup-form\" method=\"post\">");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("name", "Signup User");
        form.add("email", "signup@example.com");
        form.add("password", "password123");

        ResponseEntity<String> signupResponse = restTemplate.postForEntity(
                "/auth/signup-form",
                new HttpEntity<>(form, headers),
                String.class
        );

        assertThat(signupResponse.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(signupResponse.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("/signup?status=success");
        assertThat(signupResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("git-sat=")
                .contains("HttpOnly");
    }

    @Test
    void unauthenticatedDevicePageRedirectsToLogin() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/device?user_code=ABCD1234"))
                .GET()
                .build();

        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(response.headers().firstValue("Location")).hasValueSatisfying(location -> {
            assertThat(location).contains("/login?");
            assertThat(location).contains("next=%2Fdevice%3Fuser_code%3DABCD1234");
        });
    }

    @Test
    void devicePageLoadsForAuthenticatedUser() {
        ResponseEntity<AuthResponse> registerResponse = registerBrowserUser("device-page@example.com");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, extractCookie(registerResponse));

        ResponseEntity<String> pageResponse = restTemplate.exchange(
                "/device?user_code=ABCD1234",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(pageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pageResponse.getBody()).contains("Link your device.");
        assertThat(pageResponse.getBody()).contains("name=\"user_code\"");
    }

    @Test
    void authenticatedFormActivationRejectsInvalidCode() {
        ResponseEntity<AuthResponse> registerResponse = registerBrowserUser("invalid-code@example.com");

        ResponseEntity<String> activationResponse = submitDeviceActivationForm(
                "INVALID000",
                extractCookie(registerResponse)
        );

        assertThat(activationResponse.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(activationResponse.getHeaders().getFirst(HttpHeaders.LOCATION))
                .contains("/device?status=error")
                .contains("Invalid+user+code.");
    }

    @Test
    void authenticatedFormActivationRejectsExpiredCode() {
        ResponseEntity<DeviceCodeResponse> deviceCodeResponse =
                restTemplate.postForEntity("/auth/device-code", null, DeviceCodeResponse.class);
        DeviceCode existing = deviceCodesByUserCode.get(deviceCodeResponse.getBody().userCode());
        DeviceCode expired = new DeviceCode(
                existing.id(),
                existing.deviceCodeHash(),
                existing.userCode(),
                existing.status(),
                existing.userId(),
                Instant.now().minusSeconds(10),
                existing.createdAt(),
                existing.lastPolledAt(),
                existing.intervalSeconds()
        );
        upsertDeviceCode(expired);

        ResponseEntity<AuthResponse> registerResponse = registerBrowserUser("expired-code@example.com");

        ResponseEntity<String> activationResponse = submitDeviceActivationForm(
                deviceCodeResponse.getBody().userCode(),
                extractCookie(registerResponse)
        );

        assertThat(activationResponse.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(activationResponse.getHeaders().getFirst(HttpHeaders.LOCATION))
                .contains("/device?status=error")
                .contains("Device+code+has+expired.");
    }

    @Test
    void devicePageActivationAllowsPollingToComplete() {
        ResponseEntity<DeviceCodeResponse> deviceCodeResponse =
                restTemplate.postForEntity("/auth/device-code", null, DeviceCodeResponse.class);

        ResponseEntity<AuthResponse> registerResponse = registerBrowserUser("device-link@example.com");

        ResponseEntity<String> activationResponse = submitDeviceActivationForm(
                deviceCodeResponse.getBody().userCode(),
                extractCookie(registerResponse)
        );

        assertThat(activationResponse.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(activationResponse.getHeaders().getFirst(HttpHeaders.LOCATION))
                .contains("/device?status=success");

        ResponseEntity<DeviceVerificationResponse> verifiedResponse = restTemplate.postForEntity(
                "/auth/verify-device-code",
                new DeviceVerificationRequest(deviceCodeResponse.getBody().deviceCode()),
                DeviceVerificationResponse.class
        );

        assertThat(verifiedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verifiedResponse.getBody()).isNotNull();
        assertThat(verifiedResponse.getBody().accessToken()).isNotBlank();
        assertThat(verifiedResponse.getBody().refreshToken()).isNotBlank();
    }

    @Test
    void deviceCodeFlowReturnsAccessAndRefreshTokens() {
        ResponseEntity<DeviceCodeResponse> deviceCodeResponse =
                restTemplate.postForEntity("/auth/device-code", null, DeviceCodeResponse.class);

        assertThat(deviceCodeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deviceCodeResponse.getBody()).isNotNull();
        assertThat(deviceCodeResponse.getBody().deviceCode()).isNotBlank();
        assertThat(deviceCodeResponse.getBody().userCode()).isNotBlank();

        ResponseEntity<DeviceVerificationResponse> pendingResponse = restTemplate.postForEntity(
                "/auth/verify-device-code",
                new DeviceVerificationRequest(deviceCodeResponse.getBody().deviceCode()),
                DeviceVerificationResponse.class
        );

        assertThat(pendingResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(pendingResponse.getBody()).isNotNull();
        assertThat(pendingResponse.getBody().error()).isEqualTo("authorization_pending");

        RegisterRequest registerRequest = new RegisterRequest(
                "CLI User",
                "cli@example.com",
                "password123"
        );
        ResponseEntity<AuthResponse> registerResponse =
                restTemplate.postForEntity("/auth/register", registerRequest, AuthResponse.class);

        HttpHeaders activationHeaders = new HttpHeaders();
        activationHeaders.add(HttpHeaders.COOKIE, extractCookie(registerResponse));
        ResponseEntity<DeviceActivationResponse> activationResponse = restTemplate.exchange(
                "/auth/activate-device",
                HttpMethod.POST,
                new HttpEntity<>(new ActivateDeviceRequest(deviceCodeResponse.getBody().userCode()), activationHeaders),
                DeviceActivationResponse.class
        );

        assertThat(activationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activationResponse.getBody()).isNotNull();
        assertThat(activationResponse.getBody().message()).contains("approved");

        ResponseEntity<DeviceVerificationResponse> verifiedResponse = restTemplate.postForEntity(
                "/auth/verify-device-code",
                new DeviceVerificationRequest(deviceCodeResponse.getBody().deviceCode()),
                DeviceVerificationResponse.class
        );

        assertThat(verifiedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verifiedResponse.getBody()).isNotNull();
        assertThat(verifiedResponse.getBody().accessToken()).isNotBlank();
        assertThat(verifiedResponse.getBody().refreshToken()).isNotBlank();

        HttpHeaders bearerHeaders = new HttpHeaders();
        bearerHeaders.setBearerAuth(verifiedResponse.getBody().accessToken());
        ResponseEntity<AuthResponse> meResponse = restTemplate.exchange(
                "/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders),
                AuthResponse.class
        );

        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResponse.getBody()).isNotNull();
        assertThat(meResponse.getBody().email()).isEqualTo("cli@example.com");

        ResponseEntity<RefreshTokenResponse> refreshResponse = restTemplate.postForEntity(
                "/auth/refresh",
                new RefreshTokenRequest(verifiedResponse.getBody().refreshToken()),
                RefreshTokenResponse.class
        );

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResponse.getBody()).isNotNull();
        assertThat(refreshResponse.getBody().accessToken()).isNotBlank();

        ResponseEntity<DeviceVerificationResponse> completedResponse = restTemplate.postForEntity(
                "/auth/verify-device-code",
                new DeviceVerificationRequest(deviceCodeResponse.getBody().deviceCode()),
                DeviceVerificationResponse.class
        );

        assertThat(completedResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(completedResponse.getBody()).isNotNull();
        assertThat(completedResponse.getBody().error()).isEqualTo("invalid_grant");
    }

    private String extractCookie(ResponseEntity<?> response) {
        String header = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(header).isNotBlank();
        return header.split(";", 2)[0];
    }

    private ResponseEntity<AuthResponse> registerBrowserUser(String email) {
        RegisterRequest registerRequest = new RegisterRequest(
                "Browser User",
                email,
                "password123"
        );
        return restTemplate.postForEntity("/auth/register", registerRequest, AuthResponse.class);
    }

    private ResponseEntity<String> submitDeviceActivationForm(String userCode, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, cookie);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("user_code", userCode);

        return restTemplate.postForEntity(
                "/auth/activate-device",
                new HttpEntity<>(form, headers),
                String.class
        );
    }

    private void upsertDeviceCode(DeviceCode deviceCode) {
        deviceCodesById.put(deviceCode.id(), deviceCode);
        deviceCodesByHash.put(deviceCode.deviceCodeHash(), deviceCode);
        deviceCodesByUserCode.put(deviceCode.userCode(), deviceCode);
    }
}
