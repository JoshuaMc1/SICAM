package com.joshuamc1.sicam.models.dto;

public class UserDTO {
    private Integer id;
    private String name;
    private String username;
    private String role;
    private String createdAt;

    public UserDTO(Integer id, String name, String username, String role, String createdAt) {
        this.id = id; this.name = name; this.username = username; this.role = role; this.createdAt = createdAt;
    }

    public Integer getId() { return id; }
    public String getName() { return name; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getCreatedAt() { return createdAt; }

    public void setId(Integer id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setUsername(String username) { this.username = username; }
    public void setRole(String role) { this.role = role; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}

