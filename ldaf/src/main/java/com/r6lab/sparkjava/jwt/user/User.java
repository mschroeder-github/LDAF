package com.r6lab.sparkjava.jwt.user;

import java.util.List;

public final class User {

    
    private final String userName;
    private final String password;
    
    private final String firstName;
    private final String lastName;
    
    private final List<Role> roles;
    
    private final String logo;

    private User(String username, String passwordHash, String firstName, String lastName, List<Role> roles, String logo) {
        this.userName = username;
        this.password = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.roles = roles;
        this.logo = logo;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public List<Role> getRoles() {
        return roles;
    }

    public String getLogo() {
        return logo;
    }
    
    public void assignRole(Role role) {
        if (!roles.contains(role)) {
            roles.add(role);
        }
    }

    public void revokeRole(Role role) {
        if (!roles.contains(role)) {
            roles.remove(role);
        }
    }

    public static final User of(String username, String passwordHash, String firstName, String lastName, List<Role> roles, String logo) {
        return new User(username, passwordHash, firstName, lastName, roles, logo);
    }
}
