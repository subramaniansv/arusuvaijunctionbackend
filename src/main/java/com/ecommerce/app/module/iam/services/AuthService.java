package com.ecommerce.app.module.iam.services;

import java.util.List;
import java.util.UUID;

import com.ecommerce.app.module.iam.models.RefreshToken;
import com.ecommerce.app.module.iam.models.Role;
import com.ecommerce.app.module.iam.models.TokenResponse;
import com.ecommerce.app.module.iam.models.User;
import com.ecommerce.app.module.iam.models.UserStatus;
import com.ecommerce.app.module.iam.repository.AuthRepository;
import com.ecommerce.app.module.iam.repository.MapperRepository;
import com.ecommerce.app.module.iam.repository.UserRepository;
import com.ecommerce.app.module.iam.services.EmailVerificationService;
import com.ecommerce.app.module.iam.util.JwtUtil;
import com.ecommerce.app.module.iam.util.PasswordUtil;
import com.ecommerce.app.module.iam.util.GoogleTokenVerifier;
import com.ecommerce.app.module.mail.MailService;
import com.ecommerce.app.module.mail.MailTemplates;

public class AuthService {
    AuthRepository authRepository = new AuthRepository();
    UserRepository userRepository = new UserRepository();
    MapperRepository mapperRepository = new MapperRepository();
    JwtUtil jwtUtil = new JwtUtil();
    public TokenResponse register(User user,RefreshToken refreshToken){
        user = userRepository.create(user);
        if (user == null || user.getId() == null) {
            throw new RuntimeException("user not registered (email may already exist)");
        }
        List<Role> roles = mapperRepository.getRolesAndPermissionsByUserId(user.getId());
        TokenResponse tokenResponse = new TokenResponse();
        String accessToken =  jwtUtil.generateAccessToken(user.getId(),user.getEmail(),roles);
        String refreshTokenString = jwtUtil.generateRefreshToken(user.getId());
        refreshToken.setTokenHash(refreshTokenString);
        refreshToken.setUserId(user.getId());
        refreshToken = authRepository.create(refreshToken);
        tokenResponse.setExpiresIn(System.currentTimeMillis() + 86400000L);
        tokenResponse.setAccessToken(accessToken);
        tokenResponse.setRefreshToken(refreshTokenString);
        tokenResponse.setTokenType("Bearer");
        // Fire-and-forget welcome email. Never blocks registration on SMTP.
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            MailService.get().send(
                    user.getEmail(),
                    "Welcome to Arusuvai",
                    MailTemplates.welcome(user.getFirstName()));
            // Also dispatch the email-verification link. The service handles
            // token generation, persistence and the mail send internally.
            try {
                new EmailVerificationService().send(user);
            } catch (Exception e) {
                // Never let mail-side issues fail the registration flow.
            }
        }
        return tokenResponse;
    }

    public TokenResponse refreshAccessToken(String refreshTokenString) throws RuntimeException{
       if (refreshTokenString == null || refreshTokenString.isBlank()) {
           throw new RuntimeException("invalid refresh token");
       }
       // 1. Verify the JWT signature & expiry cryptographically. This rejects
       //    forged tokens before we ever touch the database.
       try {
           jwtUtil.validateRefreshToken(refreshTokenString);
       } catch (Exception e) {
           throw new RuntimeException("invalid refresh token");
       }
       // 2. Look up the row. The repository filters isRevoked = false, so a
       //    revoked or unknown token simply returns null.
       RefreshToken refreshToken = authRepository.getRefreshTokenByTokenHash(refreshTokenString);
       if (refreshToken == null || refreshToken.getUserId() == null) {
           throw new RuntimeException("invalid refresh token");
       }
       if (refreshToken.isRevoked() || refreshToken.getexpiredAt() < System.currentTimeMillis()) {
           throw new RuntimeException("refresh token expired");
       }
       User user = userRepository.getUser(refreshToken.getUserId());
        List<Role> roles = mapperRepository.getRolesAndPermissionsByUserId(user.getId());
        TokenResponse tokenResponse = new TokenResponse();
        String accessToken =  jwtUtil.generateAccessToken(user.getId(),user.getEmail(),roles);
        tokenResponse.setExpiresIn(System.currentTimeMillis() + 86400000L);
        tokenResponse.setAccessToken(accessToken);
        tokenResponse.setRefreshToken(refreshTokenString);
        tokenResponse.setTokenType("Bearer");
        return tokenResponse;
    }

    public TokenResponse login(User user,RefreshToken refreshToken) throws RuntimeException{
       User userDB = userRepository.getUserWithPassword(user.getEmail());
      if(userDB == null || userDB.getId() == null){
          // Privacy-safe: don't reveal whether the email exists.
          throw new RuntimeException("invalid email or password");
      }
      if(!userDB.getStatus().equals(UserStatus.ACTIVE)){
        throw new RuntimeException("user status is "+userDB.getStatus().name());
      }
      // Google-only accounts carry the OAuth sentinel instead of a real hash.
      // Detect it here and steer the user to Google rather than running
      // verify() (which would just say "invalid email or password").
      if (UserRepository.OAUTH_PASSWORD_SENTINEL.equals(userDB.getPasswordHash())) {
        throw new RuntimeException("This account was created with Google. Please continue with Google sign-in.");
      }
      boolean isPasswordVerified =  PasswordUtil.verify(user.getPasswordHash(), userDB.getPasswordHash());
      if(!isPasswordVerified){
        throw new RuntimeException("invalid email or password");
      }
       List<Role> roles = mapperRepository.getRolesAndPermissionsByUserId(userDB.getId());
         TokenResponse tokenResponse = new TokenResponse();
        String accessToken =  jwtUtil.generateAccessToken(userDB.getId(),userDB.getEmail(),roles);
        String refreshTokenString = jwtUtil.generateRefreshToken(userDB.getId());
        refreshToken.setTokenHash(refreshTokenString);
        refreshToken.setUserId(userDB.getId());
        refreshToken = authRepository.create(refreshToken);
        tokenResponse.setExpiresIn(System.currentTimeMillis() + 86400000L);
        tokenResponse.setAccessToken(accessToken);
        tokenResponse.setRefreshToken(refreshTokenString);
        tokenResponse.setTokenType("Bearer");
        userRepository.updateLastLogin(userDB.getId());
        // Optional sign-in alert - opt-in via MAIL_LOGIN_ALERTS=true so dev
        // testing doesn't spam the inbox on every reload.
        if ("true".equalsIgnoreCase(com.ecommerce.app.module.iam.config.ENVConfig.get("MAIL_LOGIN_ALERTS"))
                && userDB.getEmail() != null && !userDB.getEmail().isBlank()) {
            MailService.get().send(
                    userDB.getEmail(),
                    "New sign-in to your Arusuvai account",
                    MailTemplates.loginAlert(userDB.getFirstName(),
                            refreshToken.getIpAddress(), refreshToken.getUserAgent()));
        }
        return tokenResponse;

    }

    /**
     * Sign in (or transparently sign up) with a Google ID token.
     *
     * The token is verified by {@link GoogleTokenVerifier} (signature via
     * Google, audience + email_verified by us). We then match the user by
     * email:
     *   - existing row  -> issue tokens (works for users who originally
     *     registered with a password too; same person, one account).
     *   - no row        -> create a passwordless account (OAuth sentinel hash,
     *     email already verified) and issue tokens.
     * We never overwrite an existing password hash here, so a password user
     * who later uses Google keeps both login methods.
     */
    public TokenResponse loginWithGoogle(String credential, RefreshToken refreshToken) {
        GoogleTokenVerifier.GoogleProfile profile = GoogleTokenVerifier.verify(credential);

        User user = userRepository.getUserWithPassword(profile.email);
        if (user == null || user.getId() == null) {
            User toCreate = new User();
            toCreate.setEmail(profile.email);
            toCreate.setFirstName(profile.firstName);
            toCreate.setLastName(profile.lastName);
            user = userRepository.createOAuthUser(toCreate);
            if (user == null || user.getId() == null) {
                throw new RuntimeException("could not create account");
            }
            // Fire-and-forget welcome email. No verification mail: Google has
            // already proven the address.
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                MailService.get().send(
                        user.getEmail(),
                        "Welcome to Arusuvai",
                        MailTemplates.welcome(user.getFirstName()));
            }
        } else if (!user.getStatus().equals(UserStatus.ACTIVE)) {
            throw new RuntimeException("user status is " + user.getStatus().name());
        }

        List<Role> roles = mapperRepository.getRolesAndPermissionsByUserId(user.getId());
        TokenResponse tokenResponse = new TokenResponse();
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), roles);
        String refreshTokenString = jwtUtil.generateRefreshToken(user.getId());
        refreshToken.setTokenHash(refreshTokenString);
        refreshToken.setUserId(user.getId());
        refreshToken = authRepository.create(refreshToken);
        tokenResponse.setExpiresIn(System.currentTimeMillis() + 86400000L);
        tokenResponse.setAccessToken(accessToken);
        tokenResponse.setRefreshToken(refreshTokenString);
        tokenResponse.setTokenType("Bearer");
        userRepository.updateLastLogin(user.getId());
        return tokenResponse;
    }

    public void deleteAll(String uuid){
        UUID userId = UUID.fromString(uuid);
        authRepository.revokeByUserId(userId);
    }

    public void deleteByRefreshId(String refreshToken, UUID expectedUserId){
        // Endpoint is now authenticated: the caller's UUID comes from the JWT,
        // not from the request. Verify the refresh token actually belongs to
        // them so a user with a leaked token can't revoke another user's session.
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RuntimeException("refresh token required");
        }
        RefreshToken stored = authRepository.getRefreshTokenByTokenHash(refreshToken);
        if (stored == null || stored.getUserId() == null) {
            throw new RuntimeException("refresh token not found");
        }
        if (expectedUserId == null || !expectedUserId.equals(stored.getUserId())) {
            throw new RuntimeException("refresh token does not belong to this user");
        }
        authRepository.revokeByTokenHash(refreshToken, stored.getUserId());
    }



}
