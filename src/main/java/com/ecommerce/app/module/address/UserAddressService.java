package com.ecommerce.app.module.address;

import java.util.List;
import java.util.UUID;

public class UserAddressService {
    private final UserAddressRepository repo = new UserAddressRepository();

    public List<UserAddress> getAddresses(UUID userId) {
        return repo.findByUser(userId);
    }

    public UserAddress createAddress(UUID userId, UserAddress input) {
        validate(input);
        input.setUserId(userId);
        return repo.create(input);
    }

    public UserAddress updateAddress(UUID userId, UUID addressId, UserAddress input) {
        validate(input);
        input.setUserId(userId);
        input.setAddressId(addressId);
        UserAddress updated = repo.update(input);
        if (updated == null) throw new IllegalArgumentException("address not found");
        return updated;
    }

    public void deleteAddress(UUID userId, UUID addressId) {
        boolean deleted = repo.delete(addressId, userId);
        if (!deleted) throw new IllegalArgumentException("address not found");
    }

    private void validate(UserAddress a) {
        if (blank(a.getFullName()))  throw new IllegalArgumentException("fullName is required");
        if (blank(a.getPhone()))     throw new IllegalArgumentException("phone is required");
        if (blank(a.getLine1()))     throw new IllegalArgumentException("line1 is required");
        if (blank(a.getCity()))      throw new IllegalArgumentException("city is required");
        if (blank(a.getState()))     throw new IllegalArgumentException("state is required");
        if (blank(a.getPincode()))   throw new IllegalArgumentException("pincode is required");
        if (blank(a.getCountry()))   throw new IllegalArgumentException("country is required");
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
