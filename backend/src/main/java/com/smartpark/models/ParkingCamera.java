package com.smartpark.models;

import jakarta.persistence.*;

@Entity
@Table(name = "parking_cameras")
public class ParkingCamera {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "parking_id", unique = true)
    private Parking parking;

    @Column(name = "video_file", nullable = false, length = 255)
    private String videoFile;

    public ParkingCamera() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Parking getParking() { return parking; }
    public void setParking(Parking parking) { this.parking = parking; }

    public String getVideoFile() { return videoFile; }
    public void setVideoFile(String videoFile) { this.videoFile = videoFile; }
}

