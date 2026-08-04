package tech.buildrun.notebooklm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tech.buildrun.notebooklm.entity.User;

import java.io.IOException;

@Component
public class UserUpsertFilter extends OncePerRequestFilter {

    public static final String CURRENT_USER_ATTRIBUTE = "currentUser";

    private final UserUpsertService userUpsertService;

    public UserUpsertFilter(UserUpsertService userUpsertService) {
        this.userUpsertService = userUpsertService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            User user = userUpsertService.resolve(jwt.getSubject(), extractEmail(jwt), extractName(jwt));
            request.setAttribute(CURRENT_USER_ATTRIBUTE, user);
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
