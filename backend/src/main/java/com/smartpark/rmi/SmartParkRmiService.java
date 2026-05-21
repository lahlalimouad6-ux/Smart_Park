package com.smartpark.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface SmartParkRmiService extends Remote {
    String ping() throws RemoteException;
    ReservationAccessInfo getReservationAccessInfo(String qrToken) throws RemoteException;
}

