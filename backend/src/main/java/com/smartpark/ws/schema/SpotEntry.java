package com.smartpark.ws.schema;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import javax.xml.datatype.XMLGregorianCalendar;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SpotEntry", namespace = "http://smartpark.com/ws", propOrder = {
        "spotId",
        "numeroPlace",
        "statut",
        "nextReservationStart"
})
public class SpotEntry {

    @XmlElement(namespace = "http://smartpark.com/ws")
    private long spotId;

    @XmlElement(namespace = "http://smartpark.com/ws", required = true)
    private String numeroPlace;

    @XmlElement(namespace = "http://smartpark.com/ws", required = true)
    private String statut;

    @XmlElement(namespace = "http://smartpark.com/ws")
    private XMLGregorianCalendar nextReservationStart;

    public long getSpotId() {
        return spotId;
    }

    public void setSpotId(long spotId) {
        this.spotId = spotId;
    }

    public String getNumeroPlace() {
        return numeroPlace;
    }

    public void setNumeroPlace(String numeroPlace) {
        this.numeroPlace = numeroPlace;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public XMLGregorianCalendar getNextReservationStart() {
        return nextReservationStart;
    }

    public void setNextReservationStart(XMLGregorianCalendar nextReservationStart) {
        this.nextReservationStart = nextReservationStart;
    }
}

