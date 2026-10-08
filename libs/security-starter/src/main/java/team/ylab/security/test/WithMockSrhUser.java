package team.ylab.security.test;

import java.lang.annotation.*;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * Annotation for Spring Security test methods that populates the {@link
 * org.springframework.security.core.context.SecurityContext} with an authenticated user having the
 * specified roles and their mapped permissions from the RBAC catalog.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@WithSecurityContext(factory = WithMockSrhUserSecurityContextFactory.class)
public @interface WithMockSrhUser {

  String username() default "test-user";

  String userId() default "00000000-0000-0000-0000-000000000001";

  String[] roles() default {};
}
