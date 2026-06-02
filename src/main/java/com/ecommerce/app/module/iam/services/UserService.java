package com.ecommerce.app.module.iam.services;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.module.iam.models.PasswordResetRequest;
import com.ecommerce.app.module.iam.models.User;
import com.ecommerce.app.module.iam.models.UserStatus;
import com.ecommerce.app.module.iam.repository.UserRepository;
import com.ecommerce.app.module.mail.MailService;
import com.ecommerce.app.module.mail.MailTemplates;

public class UserService {
    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);
    UserRepository repository = new UserRepository();

    // Minimum password length for self-service password changes.
    // Matches the implicit registration policy; bump in one place if changed.
    private static final int MIN_PASSWORD_LENGTH = 6;

    public User getUser(String uuid){
        UUID userId = UUID.fromString(uuid);
      return  repository.getUser(userId);
    }

    /**
     * Self-service profile fetch. Caller passes their authenticated UUID
     * directly (never from request body) so impersonation is impossible.
     * Strips the password hash before returning.
     */
    public User getOwnProfile(UUID userId) {
        if (userId == null) {
            return null;
        }
        User user = repository.getUser(userId);
        if (user != null) {
            // Compute hasPassword BEFORE stripping the hash so the UI knows
            // whether to show the change-password form. Google-only accounts
            // carry the OAuth sentinel, not a real bcrypt hash.
            String hash = user.getPasswordHash();
            boolean hasPw = hash != null && !hash.isBlank()
                    && !UserRepository.OAUTH_PASSWORD_SENTINEL.equals(hash);
            user.setHasPassword(hasPw);
            user.setPasswordHash(null);
        }
        return user;
    }

    public List<User> getAllUsers(){
        return repository.getAllUsers();
    }

    /**
     * Legacy entry point — userId comes from the request body. Kept for
     * admin tooling. Self-service callers MUST use the overload below so the
     * userId is taken from AuthContext and cannot be forged.
     */
    public boolean updatePassword(PasswordResetRequest req){
        UUID userID = UUID.fromString(req.getUserId());
        boolean ok = repository.updatePassword(userID, req.getOldPassword(), req.getNewPassword());
        if (ok) {
            notifyPasswordChanged(userID);
        }
        return ok;
    }

    /**
     * Self-service password change.
     *
     * userId is supplied by the controller from AuthContext (never from the
     * request body), so a logged-in user can only change their OWN password.
     * Validates basic policy:
     *   - both old and new are present
     *   - new password meets MIN_PASSWORD_LENGTH
     *   - new password is not identical to the old one
     *
     * Final old-password verification is done in the repository against the
     * stored hash; that's the security gate.
     */
    public boolean updatePassword(UUID userId, String oldPassword, String newPassword) {
        if (userId == null) {
            throw new RuntimeException("unauthenticated");
        }
        if (oldPassword == null || oldPassword.isBlank()
                || newPassword == null || newPassword.isBlank()) {
            throw new RuntimeException("oldPassword and newPassword are required");
        }
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new RuntimeException(
                    "newPassword must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (oldPassword.equals(newPassword)) {
            throw new RuntimeException("newPassword must be different from oldPassword");
        }
        boolean ok = repository.updatePassword(userId, oldPassword, newPassword);
        if (ok) {
            notifyPasswordChanged(userId);
        }
        return ok;
    }

    public Boolean updateStatus(String uuid,String status){
          UUID userID = UUID.fromString(uuid);
          UserStatus userStatus = UserStatus.valueOf( status);

          Boolean ok = repository.updateUserStatus(userID, userStatus);
          if (Boolean.TRUE.equals(ok)) {
              notifyAccountStatusChanged(userID, userStatus.name());
          }
          return ok;
    }

    /** Fire-and-forget password-change confirmation. Never throws. */
    private void notifyPasswordChanged(UUID userId) {
        try {
            if (userId == null) return;
            User u = repository.getUser(userId);
            if (u == null || u.getEmail() == null || u.getEmail().isBlank()) return;
            MailService.get().send(
                    u.getEmail(),
                    "Your Arusuvai password was changed",
                    MailTemplates.passwordChanged(u.getFirstName()));
        } catch (Exception e) {
            LOG.warn("could not send password-changed email: {}", e.getMessage());
        }
    }

    /** Fire-and-forget account-status notification. Never throws. */
    private void notifyAccountStatusChanged(UUID userId, String status) {
        try {
            if (userId == null) return;
            User u = repository.getUser(userId);
            if (u == null || u.getEmail() == null || u.getEmail().isBlank()) return;
            MailService.get().send(
                    u.getEmail(),
                    "Your Arusuvai account status is now " + status,
                    MailTemplates.accountStatusChanged(u.getFirstName(), status));
        } catch (Exception e) {
            LOG.warn("could not send account-status email: {}", e.getMessage());
        }
    }

}
