package com.agritech.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String fullName;
    private String farmName;
    private String location;
    private Double landAcres;

    public AppUser() {}

    public AppUser(String email, String password, String fullName, String farmName, String location, Double landAcres) {
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.farmName = farmName;
        this.location = location;
        this.landAcres = landAcres;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getFarmName() { return farmName; }
    public void setFarmName(String farmName) { this.farmName = farmName; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Double getLandAcres() { return landAcres; }
    public void setLandAcres(Double landAcres) { this.landAcres = landAcres; }
}
