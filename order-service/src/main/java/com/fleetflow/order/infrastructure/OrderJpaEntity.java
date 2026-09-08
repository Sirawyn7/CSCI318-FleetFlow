package com.fleetflow.order.infrastructure;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class OrderJpaEntity {

    @Id
    private UUID id;

    private UUID customerId;

    private String pickupStreet;
    private String pickupSuburb;
    private String pickupState;
    private String pickupPostcode;
    private double pickupLatitude;
    private double pickupLongitude;

    private String dropoffStreet;
    private String dropoffSuburb;
    private String dropoffState;
    private String dropoffPostcode;
    private double dropoffLatitude;
    private double dropoffLongitude;

    private String packageDetails;
    private String serviceLevel;
    private Instant requestedDeliveryBy;
    private String status;
    private Instant createdAt;

    protected OrderJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getPickupStreet() {
        return pickupStreet;
    }

    public void setPickupStreet(String pickupStreet) {
        this.pickupStreet = pickupStreet;
    }

    public String getPickupSuburb() {
        return pickupSuburb;
    }

    public void setPickupSuburb(String pickupSuburb) {
        this.pickupSuburb = pickupSuburb;
    }

    public String getPickupState() {
        return pickupState;
    }

    public void setPickupState(String pickupState) {
        this.pickupState = pickupState;
    }

    public String getPickupPostcode() {
        return pickupPostcode;
    }

    public void setPickupPostcode(String pickupPostcode) {
        this.pickupPostcode = pickupPostcode;
    }

    public double getPickupLatitude() {
        return pickupLatitude;
    }

    public void setPickupLatitude(double pickupLatitude) {
        this.pickupLatitude = pickupLatitude;
    }

    public double getPickupLongitude() {
        return pickupLongitude;
    }

    public void setPickupLongitude(double pickupLongitude) {
        this.pickupLongitude = pickupLongitude;
    }

    public String getDropoffStreet() {
        return dropoffStreet;
    }

    public void setDropoffStreet(String dropoffStreet) {
        this.dropoffStreet = dropoffStreet;
    }

    public String getDropoffSuburb() {
        return dropoffSuburb;
    }

    public void setDropoffSuburb(String dropoffSuburb) {
        this.dropoffSuburb = dropoffSuburb;
    }

    public String getDropoffState() {
        return dropoffState;
    }

    public void setDropoffState(String dropoffState) {
        this.dropoffState = dropoffState;
    }

    public String getDropoffPostcode() {
        return dropoffPostcode;
    }

    public void setDropoffPostcode(String dropoffPostcode) {
        this.dropoffPostcode = dropoffPostcode;
    }

    public double getDropoffLatitude() {
        return dropoffLatitude;
    }

    public void setDropoffLatitude(double dropoffLatitude) {
        this.dropoffLatitude = dropoffLatitude;
    }

    public double getDropoffLongitude() {
        return dropoffLongitude;
    }

    public void setDropoffLongitude(double dropoffLongitude) {
        this.dropoffLongitude = dropoffLongitude;
    }

    public String getPackageDetails() {
        return packageDetails;
    }

    public void setPackageDetails(String packageDetails) {
        this.packageDetails = packageDetails;
    }

    public String getServiceLevel() {
        return serviceLevel;
    }

    public void setServiceLevel(String serviceLevel) {
        this.serviceLevel = serviceLevel;
    }

    public Instant getRequestedDeliveryBy() {
        return requestedDeliveryBy;
    }

    public void setRequestedDeliveryBy(Instant requestedDeliveryBy) {
        this.requestedDeliveryBy = requestedDeliveryBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}