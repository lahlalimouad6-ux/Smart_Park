package com.smartpark.ws.schema;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = { "parkingId", "parkingNom", "spots" })
@XmlRootElement(name = "GetParkingPlanResponse", namespace = "http://smartpark.com/ws")
public class GetParkingPlanResponse {

    @XmlElement(namespace = "http://smartpark.com/ws")
    private Long parkingId;

    @XmlElement(namespace = "http://smartpark.com/ws")
    private String parkingNom;

    @XmlElement(namespace = "http://smartpark.com/ws")
    private List<SpotEntry> spots;

    public Long getParkingId() {
        return parkingId;
    }

    public void setParkingId(Long parkingId) {
        this.parkingId = parkingId;
    }

    public String getParkingNom() {
        return parkingNom;
    }

    public void setParkingNom(String parkingNom) {
        this.parkingNom = parkingNom;
    }

    public List<SpotEntry> getSpots() {
        if (spots == null) {
            spots = new ArrayList<>();
        }
        return spots;
    }

    public void setSpots(List<SpotEntry> spots) {
        this.spots = spots;
    }
}

