package com.example.billport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller that serves customer portals. Its routes match only
 * on a customer's domain; every other controller matches only on
 * APP_HOSTS. See WebConfig.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CustomerSite {

}
