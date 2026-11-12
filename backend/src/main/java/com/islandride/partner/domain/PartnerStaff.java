package com.islandride.partner.domain;

import com.islandride.core.domain.BaseEntity;
import com.islandride.identityaccess.domain.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "partner_staff")
public class PartnerStaff extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "role_in_business", nullable = false, length = 30)
    private String roleInBusiness = "STAFF"; // OWNER, MANAGER, STAFF

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "can_record_handover", nullable = false)
    private boolean canRecordHandover = false;

    @Column(name = "can_collect_offline_payments", nullable = false)
    private boolean canCollectOfflinePayments = false;

    @Column(name = "can_mark_no_show", nullable = false)
    private boolean canMarkNoShow = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Business getBusiness() {
        return business;
    }

    public void setBusiness(Business business) {
        this.business = business;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getRoleInBusiness() {
        return roleInBusiness;
    }

    public void setRoleInBusiness(String roleInBusiness) {
        this.roleInBusiness = roleInBusiness;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isCanRecordHandover() {
        return canRecordHandover;
    }

    public void setCanRecordHandover(boolean canRecordHandover) {
        this.canRecordHandover = canRecordHandover;
    }

    public boolean isCanCollectOfflinePayments() {
        return canCollectOfflinePayments;
    }

    public void setCanCollectOfflinePayments(boolean canCollectOfflinePayments) {
        this.canCollectOfflinePayments = canCollectOfflinePayments;
    }

    public boolean isCanMarkNoShow() {
        return canMarkNoShow;
    }

    public void setCanMarkNoShow(boolean canMarkNoShow) {
        this.canMarkNoShow = canMarkNoShow;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

