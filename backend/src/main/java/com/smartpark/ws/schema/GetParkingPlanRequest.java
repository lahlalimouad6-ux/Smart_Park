package com.smartpark.ws.schema;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = { "parkingId" })
@XmlRootElement(name = "GetParkingPlanRequest", namespace = "http://smartpark.com/ws")
public class GetParkingPlanRequest {

    @XmlElement(namespace = "http://smartpark.com/ws", required = true)
    private long parkingId;

    public long getParkingId() {
        return parkingId;
    }

    public void setParkingId(long parkingId) {
        this.parkingId = parkingId;
    }
}

