package team.ylab.testsupport.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.ContextConfiguration;
import team.ylab.testsupport.condition.RequiresDocker;
import team.ylab.testsupport.context.PostgresContainerInitializer;

/** Enables PostgreSQL Testcontainer for the annotated test class. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@RequiresDocker
@ContextConfiguration(initializers = PostgresContainerInitializer.class)
public @interface EnablePostgresTestcontainer {}
