package tech.buildrun.notebooklm.adapter.in.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tech.buildrun.notebooklm.core.domain.user.User;
import tech.buildrun.notebooklm.core.port.in.UpsertUserPort;

import java.io.IOException;

@Component
public class UserUpsertFilter extends OncePerRequestFilter {

    public static final String CURRENT_USER_ATTRIBUTE = "currentUser";

    private final UpsertUserPort upsertUser;

    public UserUpsertFilter(UpsertUserPort upsertUser) {
        this.upsertUser = upsertUser;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            User user = upsertUser.execute(jwt.getSubject(), extractEmail(jwt), extractName(jwt));
            request.setAttribute(CURRENT_USER_ATTRIBUTE, user);
            MDC.put("userId", user.id().toString());
            MDC.put("sub", jwt.getSubject());
        }
        filterChain.doFilter(request, response);
    }

    private String extractEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        return email != null ? email : jwt.getSubject();
    }

    private String extractName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name != null) {
            return name;
        }
        String username = jwt.getClaimAsString("username");
        return username != null ? username : jwt.getSubject();
    }
}
