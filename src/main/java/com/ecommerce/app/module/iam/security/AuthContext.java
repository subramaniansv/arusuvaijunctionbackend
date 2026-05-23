package com.ecommerce.app.module.iam.security;

public class AuthContext {
    private static final ThreadLocal<AuthUser> currentuser = new ThreadLocal<>();

    public static void set(AuthUser user){
        currentuser.set(user);
    }
    public static AuthUser get(){
        return currentuser.get();
    }
    public static void clear(){
        currentuser.remove();
    }
}
