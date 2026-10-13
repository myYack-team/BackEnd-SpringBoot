package com.myyak.filter;

import com.myyak.service.visitService.VisitTrackingService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class UserVisitFilterTest {
    private final VisitTrackingService tracking = mock(VisitTrackingService.class);
    private final UserVisitFilter filter = new UserVisitFilter(tracking);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

    private void authenticate(Object principal, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role))));
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/api/users/me,200",
            "GET,/api/medications/today,200",
            "POST,/api/intakes,201",
            "PATCH,/api/supplements/3,204",
            "DELETE,/api/medications/3,204",
            "GET,/api/drugs/search,206"
    })
    void recordsAuthenticatedSuccessfulUserRequestsAfterTheirResponse(String method, String path, int status)
            throws Exception {
        authenticate(7L, "ROLE_USER");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean completed = new AtomicBoolean();
        FilterChain chain = (request, result) -> {
            verifyNoInteractions(tracking);
            ((MockHttpServletResponse) result).setStatus(status);
            completed.set(true);
        };

        filter.doFilter(request(method, path), response, chain);

        assertThat(completed).isTrue();
        assertThat(response.getStatus()).isEqualTo(status);
        verify(tracking).recordVisit(7L);
    }

    @ParameterizedTest
    @ValueSource(ints = {199, 300, 302, 400, 401, 403, 404, 409, 500, 503})
    void doesNotRecordFailedOrRedirectedResponses(int status) throws Exception {
        authenticate(7L, "ROLE_USER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("GET", "/api/users/me"), response,
                (request, result) -> ((MockHttpServletResponse) result).setStatus(status));

        assertThat(response.getStatus()).isEqualTo(status);
        verifyNoInteractions(tracking);
    }

    @ParameterizedTest
    @CsvSource({
            "POST,/api/auth/refresh",
            "POST,/api/auth/kakao",
            "POST,/api/auth/logout",
            "GET,/api/admin/dashboard",
            "POST,/api/admin/auth/verify",
            "POST,/api/drugs/batch",
            "POST,/api/drugs/batch/upload",
            "GET,/api/error-log",
            "POST,/api/error-log",
            "GET,/api/error-log/3",
            "GET,/api/health",
            "GET,/api/health/details",
            "HEAD,/api/users/me",
            "OPTIONS,/api/users/me",
            "DELETE,/api/users/me",
            "GET,/admin/index.html",
            "GET,/actuator/health",
            "GET,/v3/api-docs",
            "GET,/swagger-ui/index.html"
    })
    void doesNotRecordOperationalAuthenticationOrAccountDeletionRequests(String method, String path)
            throws Exception {
        authenticate(7L, "ROLE_USER");
        AtomicBoolean completed = new AtomicBoolean();

        filter.doFilter(request(method, path), new MockHttpServletResponse(),
                (request, response) -> completed.set(true));

        assertThat(completed).isTrue();
        verifyNoInteractions(tracking);
    }

    @Test
    void doesNotRecordAnonymousOrUnauthenticatedPrincipals() throws Exception {
        filter.doFilter(request("GET", "/api/users/me"), new MockHttpServletResponse(), (request, response) -> {});
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null));
        filter.doFilter(request("GET", "/api/users/me"), new MockHttpServletResponse(), (request, response) -> {});
        verifyNoInteractions(tracking);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_ADMIN", "ROLE_ANONYMOUS", "USER"})
    void requiresUserAuthorityEvenWhenThePrincipalIsNumeric(String role) throws Exception {
        authenticate(7L, role);
        filter.doFilter(request("GET", "/api/users/me"), new MockHttpServletResponse(), (request, response) -> {});
        verifyNoInteractions(tracking);
    }

    @Test
    void doesNotInferUserIdsFromStringOrIntegerPrincipals() throws Exception {
        for (Object principal : List.of("7", 7, "administrator", "anonymousUser")) {
            authenticate(principal, "ROLE_USER");
            filter.doFilter(request("GET", "/api/users/me"), new MockHttpServletResponse(), (request, response) -> {});
        }
        verifyNoInteractions(tracking);
    }

    @Test
    void leavesServletExceptionsUnchangedAndDoesNotRecordThem() {
        authenticate(7L, "ROLE_USER");
        ServletException failure = new ServletException("synthetic business failure");
        assertThatThrownBy(() -> filter.doFilter(request("GET", "/api/users/me"),
                new MockHttpServletResponse(), (request, response) -> { throw failure; }))
                .isSameAs(failure);
        verifyNoInteractions(tracking);
    }

    @Test
    void leavesIoExceptionsUnchangedAndDoesNotRecordThem() {
        authenticate(7L, "ROLE_USER");
        IOException failure = new IOException("synthetic response failure");
        assertThatThrownBy(() -> filter.doFilter(request("GET", "/api/users/me"),
                new MockHttpServletResponse(), (request, response) -> { throw failure; }))
                .isSameAs(failure);
        verifyNoInteractions(tracking);
    }

    @Test
    void doesNotCountAnAsyncRequestBeforeItsResponseCompletes() throws Exception {
        authenticate(7L, "ROLE_USER");
        MockHttpServletRequest request = request("GET", "/api/analysis/reports");
        request.setAsyncSupported(true);

        filter.doFilter(request, new MockHttpServletResponse(), (incoming, response) -> incoming.startAsync());

        assertThat(request.isAsyncStarted()).isTrue();
        verifyNoInteractions(tracking);
    }

    @Test
    void doesNotCountAnAsyncRedispatchAsASeparateUserActivity() throws Exception {
        authenticate(7L, "ROLE_USER");
        MockHttpServletRequest request = request("GET", "/api/analysis/reports");
        request.setDispatcherType(DispatcherType.ASYNC);

        filter.doFilter(request, new MockHttpServletResponse(), (incoming, response) -> {});

        verifyNoInteractions(tracking);
    }
}
