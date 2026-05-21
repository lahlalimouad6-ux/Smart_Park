package com.smartpark.rmi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.SmartLifecycle;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;

@Configuration
@ConditionalOnProperty(name = "smartpark.rmi.enabled", havingValue = "true")
public class RmiConfig {

    @Bean
    public SmartLifecycle rmiServer(
            SmartParkRmiService smartParkRmiService,
            @Value("${rmi.registry.port:1099}") int registryPort,
            @Value("${rmi.service.port:0}") int servicePort
    ) {
        return new RmiServerLifecycle("SmartParkRmiService", smartParkRmiService, registryPort, servicePort);
    }

    static final class RmiServerLifecycle implements SmartLifecycle {
        private final String serviceName;
        private final SmartParkRmiService service;
        private final int registryPort;
        private final int servicePort;

        private volatile boolean running;
        private Registry registry;
        private Remote stub;
        private boolean registryCreated;

        RmiServerLifecycle(String serviceName, SmartParkRmiService service, int registryPort, int servicePort) {
            this.serviceName = serviceName;
            this.service = service;
            this.registryPort = registryPort;
            this.servicePort = servicePort;
        }

        @Override
        public void start() {
            if (running) {
                return;
            }
            try {
                registry = ensureRegistry(registryPort);
                stub = UnicastRemoteObject.exportObject(service, servicePort);
                registry.rebind(serviceName, stub);
                running = true;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to start RMI service " + serviceName + " on registry port " + registryPort, e);
            }
        }

        private Registry ensureRegistry(int port) throws RemoteException {
            try {
                Registry existing = LocateRegistry.getRegistry(port);
                existing.list();
                return existing;
            } catch (RemoteException ignored) {
                try {
                    registryCreated = true;
                    return LocateRegistry.createRegistry(port);
                } catch (ExportException alreadyExists) {
                    Registry existing = LocateRegistry.getRegistry(port);
                    existing.list();
                    registryCreated = false;
                    return existing;
                }
            }
        }

        @Override
        public void stop() {
            if (!running) {
                return;
            }
            try {
                if (registry != null) {
                    try {
                        registry.unbind(serviceName);
                    } catch (Exception ignored) {
                    }
                }
                try {
                    UnicastRemoteObject.unexportObject(service, true);
                } catch (Exception ignored) {
                }
                if (registryCreated && registry != null) {
                    try {
                        UnicastRemoteObject.unexportObject(registry, true);
                    } catch (Exception ignored) {
                    }
                }
            } finally {
                stub = null;
                registry = null;
                registryCreated = false;
                running = false;
            }
        }

        @Override
        public void stop(Runnable callback) {
            stop();
            callback.run();
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public boolean isAutoStartup() {
            return true;
        }
    }
}
