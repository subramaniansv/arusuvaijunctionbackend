package com.ecommerce.app.module.iam.security;
import java.lang.annotation.*;


@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresRole {
    String[] value();
    boolean matchAll() default false;
        
}
