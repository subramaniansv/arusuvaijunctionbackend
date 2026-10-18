package com.ecommerce.app.module.quick;

import java.util.UUID;

/** A dark store / hub that serves quick (Rapido) deliveries. */
public class QuickStore {
    private UUID storeId;
    private String name;
    private String contactName;
    private String contactPhone;
    private String address;
    private double lat;
    private double lng;
    private String pincode;
    private double serviceRadiusKm;   // dynamic — admin editable
    private boolean active;

    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public double getServiceRadiusKm() { return serviceRadiusKm; }
    public void setServiceRadiusKm(double serviceRadiusKm) { this.serviceRadiusKm = serviceRadiusKm; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
