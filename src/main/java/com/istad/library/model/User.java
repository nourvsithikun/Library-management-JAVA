package com.istad.library.model;

import java.util.Objects;

/** A registered library member. Identity is represented by the immutable user ID. */
public final class User {
    private final String id;
    private String name;
    private String email;
    private String phone;
    private AccountStatus status;

    public User(String id, String name, String email, String phone, AccountStatus status) {
        this.id = clean(id);
        this.name = clean(name);
        this.email = clean(email);
        this.phone = clean(phone);
        this.status = status == null ? AccountStatus.ACTIVE : status;
    }

    public User(User source) {
        this(source.id, source.name, source.email, source.phone, source.status);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = clean(name);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = clean(email);
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = clean(phone);
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status == null ? AccountStatus.ACTIVE : status;
    }

    @Override
    public String toString() {
        return id + " — " + name;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof User other)) {
            return false;
        }
        return id.equalsIgnoreCase(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id.toUpperCase(java.util.Locale.ROOT));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
