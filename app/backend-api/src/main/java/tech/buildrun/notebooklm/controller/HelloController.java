package tech.buildrun.notebooklm.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    private static final Logger log = LoggerFactory.getLogger(HelloController.class);

    @GetMapping("/hello")
    public String hello(@AuthenticationPrincipal Jwt jwt) {
        log.info("hello chamado por subject={} claims={}", jwt.getSubject(), jwt.getClaims());
        return "Hello, " + jwt.getClaimAsString("username") + "!";
    }
}
